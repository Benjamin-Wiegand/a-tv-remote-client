package io.benwiegand.atvremote.phone.control;

import io.benwiegand.atvremote.phone.async.SecAdapter;

public record OperationQueueEntry(SecAdapter<String> responseAdapter, String operation) {

}