package io.benwiegand.atvremote.phone.protocol.stream;

import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.OP_EVENT_STREAM_SUBSCRIBE;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.OP_EVENT_STREAM_UNSUBSCRIBE;

import android.util.Log;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import io.benwiegand.atvremote.phone.async.Sec;
import io.benwiegand.atvremote.phone.protocol.MalformedEventException;
import io.benwiegand.atvremote.phone.protocol.RemoteProtocolException;
import io.benwiegand.atvremote.phone.util.ErrorUtil;

public class EventStreamSubscriptionManager {
    private static final String TAG = EventStreamSubscriptionManager.class.getSimpleName();

    private final Map<String, IncomingEventStream> subscriptionMap = new ConcurrentHashMap<>();
    private final Function<String, Sec<String>> eventSender;

    public EventStreamSubscriptionManager(Function<String, Sec<String>> eventSender) {
        this.eventSender = eventSender;
    }

    public void onIncomingStreamedEvent(String extra) {
        int sep = extra.indexOf(' ');
        if (sep < 1 || extra.length() < sep + 2) throw new MalformedEventException("event type and data separated by ' '");
        String type = extra.substring(0, sep);

        IncomingEventStream incomingEventStream = subscriptionMap.get(type);
        if (incomingEventStream == null) {
            // this could happen briefly after the last listener is unregistered and the unsubscribe
            // event hasn't reached the destination yet.
            Log.v(TAG, "no subscription for streamed event of type: " + type);
            return;
        }

        String eventData = extra.substring(sep + 1);
        incomingEventStream.onIncomingStreamedEvent(eventData);
    }

    /**
     * registers a listener from an event stream and sends a subscribe event.
     * <p>
     *     if the result of the subscribe event is an error, the listener is automatically removed
     *     and unsubscribe() does not need to be called.
     * </p>
     * <p>
     *     sending a subscribe event on each registration ensures proper/expected behavior of state
     *     event streams, as the latest state is re-sent for each channel on the other end.
     * </p>
     * @param type event stream type
     * @param listener the listener to register
     * @return Sec for subscribe event
     */
    public Sec<Void> subscribe(String type, Consumer<String> listener) {
        synchronized (subscriptionMap) {
            IncomingEventStream incomingEventStream = subscriptionMap.computeIfAbsent(type,
                    t -> new IncomingEventStream());

            incomingEventStream.registerListener(listener);
            Log.d(TAG, "subscription to event type: " + type);
            Log.d(TAG, "total subscriptions: " + incomingEventStream.totalRegisteredListeners());

            return eventSender.apply(OP_EVENT_STREAM_SUBSCRIBE + " " + type)
                    .mapError(t -> {
                        // this could be a rejection or a dead connection. either way, consider the subscription failed.
                        if (t instanceof RemoteProtocolException e && e.isReceivedOverConnection())
                            Log.e(TAG, "subscribe rejected for event type '" + type + "': " + t.getMessage());

                        synchronized (subscriptionMap) {
                            if (subscriptionMap.get(type) != incomingEventStream) return t;

                            incomingEventStream.unregisterListener(listener);
                            if (!incomingEventStream.hasRegisteredListeners())
                                subscriptionMap.remove(type);
                        }

                        return t;
                    })
                    .map(r -> null);
        }
    }

    /**
     * unregisters a listener from an event stream. if there are no more listeners for that stream,
     * it sends an unsubscribe event.
     * <p>
     *     the result of the unsubscribe event is not very important. it could either fail due to a
     *     dead connection or the other side erroring out. In the latter case nothing reasonable can
     *     be done about it and in the former it doesn't matter because everything here is about to
     *     be destroyed.
     * </p>
     * @param type event stream type
     * @param listener the registered listener
     * @return Sec for unsubscribe event, or a premeditated one if no unsubscribe needs to be sent.
     */
    public Sec<Void> unsubscribe(String type, Consumer<String> listener) {
        synchronized (subscriptionMap) {
            IncomingEventStream incomingEventStream = subscriptionMap.get(type);
            if (incomingEventStream == null) {
                Log.w(TAG, "trying to unsubscribe from an event that has no subscriptions\n" + ErrorUtil.getLightStackTrace());
                return Sec.premeditatedResult(null);
            }

            incomingEventStream.unregisterListener(listener);
            Log.d(TAG, "unsubscription from event type: " + type);

            if (!incomingEventStream.hasRegisteredListeners()) {
                Log.d(TAG, "no more subscriptions for event type: " + type);
                subscriptionMap.remove(type);

                // stream is no longer needed
                return eventSender.apply(OP_EVENT_STREAM_UNSUBSCRIBE + " " + type)
                        .map(r -> null);
            } else {
                Log.d(TAG, "remaining subscriptions: " + incomingEventStream.totalRegisteredListeners());
            }

            // there are still listeners that need this stream
            return Sec.premeditatedResult(null);
        }
    }
}
