package io.benwiegand.atvremote.phone.util;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;

import java.util.function.Consumer;

public class CallbackUtil {
    public static <T> Consumer<String> wrapJsonParse(Gson gson, Consumer<T> wrappedConsumer, Class<T> objectClass, Consumer<JsonParseException> errorConsumer) {
        return s -> {
            T object;
            try {
                object = gson.fromJson(s, objectClass);
            } catch (JsonParseException e) {
                errorConsumer.accept(e);
                return;
            }
            wrappedConsumer.accept(object);
        };
    }
}
