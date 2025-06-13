package io.benwiegand.atvremote.phone.state;

import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.EVENT_TYPE_MEDIA_METADATA;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.EVENT_TYPE_MEDIA_POSITION;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.EVENT_TYPE_MEDIA_SESSIONS;
import static io.benwiegand.atvremote.phone.protocol.ProtocolConstants.EVENT_TYPE_MEDIA_STATE;
import static io.benwiegand.atvremote.phone.util.ErrorUtil.getLightStackTrace;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

import io.benwiegand.atvremote.phone.async.Sec;
import io.benwiegand.atvremote.phone.async.SecAdapter;
import io.benwiegand.atvremote.phone.network.TVReceiverConnection;
import io.benwiegand.atvremote.phone.protocol.json.MediaMetaEvent;
import io.benwiegand.atvremote.phone.protocol.json.MediaPositionEvent;
import io.benwiegand.atvremote.phone.protocol.json.MediaSessionsEvent;
import io.benwiegand.atvremote.phone.protocol.json.MediaStateEvent;
import io.benwiegand.atvremote.phone.util.CallbackUtil;

public class MediaSessionTracker {
    private static final String TAG = MediaSessionTracker.class.getSimpleName();

    private final Object lock = new Object();

    private static final Gson gson = new Gson();

    private final List<String> subscribedEventTypes = new ArrayList<>(4);

    private final Map<String, Consumer<String>> listenerMap = Map.of(
            EVENT_TYPE_MEDIA_SESSIONS,  CallbackUtil.wrapJsonParse(gson, this::onMediaSessionsUpdated, MediaSessionsEvent.class, this::onJsonParseError),
            EVENT_TYPE_MEDIA_METADATA,  CallbackUtil.wrapJsonParse(gson, this::onMediaMetadataUpdated, MediaMetaEvent.class, this::onJsonParseError),
            EVENT_TYPE_MEDIA_STATE,     CallbackUtil.wrapJsonParse(gson, this::onMediaStateUpdated, MediaStateEvent.class, this::onJsonParseError),
            EVENT_TYPE_MEDIA_POSITION,  CallbackUtil.wrapJsonParse(gson, this::onMediaPositionUpdated, MediaPositionEvent.class, this::onJsonParseError)
    );

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final TVReceiverConnection connection;

    private String[] mediaSessionPriorities = new String[0];
    private final Map<String, MediaSessionState> mediaSessionMap = new HashMap<>();

    private MediaSessionCallback primarySessionCallback = null;

    private record MediaSessionState(MediaMetaEvent meta, MediaStateEvent state, MediaPositionEvent position) {
        MediaSessionState() {
            this(null, null, null);
        }

        MediaSessionState newMetadata(MediaMetaEvent meta) {
            return new MediaSessionState(meta, state, position);
        }

        MediaSessionState newState(MediaStateEvent state) {
            return new MediaSessionState(meta, state, position);
        }

        MediaSessionState newPosition(MediaPositionEvent position) {
            return new MediaSessionState(meta, state, position);
        }
    }

    public interface MediaSessionCallback {
        void onMetadataUpdated(MediaMetaEvent metaEvent);
        void onStateUpdated(MediaStateEvent stateEvent);
        void onPositionUpdated(MediaPositionEvent positionEvent);
        void onDestroyed();
    }

    public MediaSessionTracker(TVReceiverConnection connection) {
        this.connection = connection;
    }

    public Sec<Void> subscribeFor(String type) {
        return connection.subscribeToEventStream(type, listenerMap.get(type))
                .map(r -> {
                    subscribedEventTypes.add(type);
                    return null;
                });
    }

    public Sec<Void> init() {
        SecAdapter.SecWithAdapter<Void> secWithAdapter = SecAdapter.createThreadless();

        // subscribe to some basic low-bandwidth events
        List<Sec<Void>> secs = List.of(
                subscribeFor(EVENT_TYPE_MEDIA_SESSIONS),
                subscribeFor(EVENT_TYPE_MEDIA_METADATA),
                subscribeFor(EVENT_TYPE_MEDIA_STATE)
        );

        AtomicInteger completed = new AtomicInteger(0);
        Queue<Throwable> failures = new ConcurrentLinkedQueue<>();

        Runnable onSecCompletion = () -> {
            if (completed.incrementAndGet() < secs.size()) return;

            // all secs complete
            if (failures.isEmpty()) {
                secWithAdapter.secAdapter().provideResult(null);
                return;
            }

            // there were errors
            secWithAdapter.secAdapter().throwError(failures.peek()); // for now just return the first error
            destroy();
        };

        secs.forEach(s -> s
                .doOnResult(r -> onSecCompletion.run())
                .doOnError(t -> {
                    Log.e(TAG, "failed to subscribe to a media event stream", t);
                    failures.add(t);
                    onSecCompletion.run();
                })
                .callMeWhenDone());

        return secWithAdapter.sec();
    }

    public void destroy() {
        for (String eventType : subscribedEventTypes)
            connection.unsubscribeFromEventStream(eventType, listenerMap.get(eventType))
                    .doOnError(t -> Log.w(TAG, "failed to unsubscribe from event type: " + eventType + "\n" + getLightStackTrace(t)))
                    .callMeWhenDone();
    }

    private void updatePrimarySessionCallbackLocked(MediaSessionState sessionState) {
        MediaSessionCallback callback = primarySessionCallback;
        if (callback == null) return;
        handler.post(() -> callback.onMetadataUpdated(sessionState.meta()));
        handler.post(() -> callback.onStateUpdated(sessionState.state()));
        handler.post(() -> callback.onPositionUpdated(sessionState.position()));
    }

    public void setPrimarySessionCallback(MediaSessionCallback callback) {
        synchronized (lock) {
            primarySessionCallback = callback;

            MediaSessionState sessionState = getPrimaryMediaSessionState();
            if (sessionState == null) return;

            updatePrimarySessionCallbackLocked(sessionState);
        }
    }

    public String getPrimaryMediaSessionId() {
        synchronized (lock) {
            return mediaSessionPriorities.length > 0 ? mediaSessionPriorities[0] : null;
        }
    }

    private MediaSessionState getPrimaryMediaSessionState() {
        String primaryMediaSessionId = getPrimaryMediaSessionId();
        if (primaryMediaSessionId == null) return null;

        MediaSessionState primarySessionState = mediaSessionMap.get(primaryMediaSessionId);
        assert primarySessionState != null;
        return primarySessionState;
    }

    private void handleCallbacks(String sessionId, Consumer<MediaSessionCallback> caller) {

        // for now there's only the primary media session callback
        String primaryMediaSessionId = getPrimaryMediaSessionId();
        if (!Objects.equals(primaryMediaSessionId, sessionId)) return;

        MediaSessionCallback callback = primarySessionCallback;
        if (callback == null) return;
        handler.post(() -> caller.accept(callback));
    }

    private void onMediaSessionsUpdated(MediaSessionsEvent event) {
        synchronized (lock) {
            Set<String> oldSessions = new HashSet<>(mediaSessionMap.keySet());

            for (String sessionId : event.activeSessionIds()) {
                if (oldSessions.remove(sessionId)) continue;
                Log.v(TAG, "new media session: " + sessionId);
                mediaSessionMap.put(sessionId, new MediaSessionState());
            }

            for (String sessionId : oldSessions) {
                Log.v(TAG, "session killed: " + sessionId);
                mediaSessionMap.remove(sessionId);
            }

            String newPrimarySessionId = event.activeSessionIds().length > 0 ? event.activeSessionIds()[0] : null;
            String oldPrimarySessionId = mediaSessionPriorities.length > 0 ? mediaSessionPriorities[0] : null;

            mediaSessionPriorities = event.activeSessionIds();

            // handle callbacks for primary session switch
            if (primarySessionCallback == null) return;
            if (Objects.equals(oldPrimarySessionId, newPrimarySessionId)) return;

            if (newPrimarySessionId == null) {
                handler.post(primarySessionCallback::onDestroyed);
                return;
            }

            MediaSessionState primarySessionState = mediaSessionMap.get(newPrimarySessionId);
            assert primarySessionState != null;

            updatePrimarySessionCallbackLocked(primarySessionState);
        }
    }

    private void updateMappedSessionState(String sessionId, Function<MediaSessionState, MediaSessionState> modify) {
        mediaSessionMap.compute(sessionId, (id, session) -> {
            if (session == null) {
                Log.v(TAG, "session doesn't exist yet, creating: " + sessionId);
                session = new MediaSessionState();
            }
            return modify.apply(session);
        });
    }

    private void onMediaMetadataUpdated(MediaMetaEvent event) {
        synchronized (lock) {
            updateMappedSessionState(event.id(), session -> session.newMetadata(event));
            handleCallbacks(event.id(), cb -> cb.onMetadataUpdated(event));
        }
    }

    private void onMediaStateUpdated(MediaStateEvent event) {
        synchronized (lock) {
            updateMappedSessionState(event.id(), session -> session.newState(event));
            handleCallbacks(event.id(), cb -> cb.onStateUpdated(event));
        }
    }

    private void onMediaPositionUpdated(MediaPositionEvent event) {
        synchronized (lock) {
            updateMappedSessionState(event.id(), session -> session.newPosition(event));
            handleCallbacks(event.id(), cb -> cb.onPositionUpdated(event));
        }
    }

    private void onJsonParseError(JsonParseException e) {
        Log.wtf(TAG, "JSON parse error in media update", e);
    }

}
