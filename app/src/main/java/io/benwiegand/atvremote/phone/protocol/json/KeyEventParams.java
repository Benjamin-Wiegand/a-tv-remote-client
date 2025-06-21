package io.benwiegand.atvremote.phone.protocol.json;

import io.benwiegand.atvremote.phone.protocol.KeyEventType;

public record KeyEventParams(int keyCode, KeyEventType type) {

}
