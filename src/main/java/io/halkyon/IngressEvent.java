package io.halkyon;

import io.javaoperatorsdk.operator.processing.event.DefaultEvent;
import io.javaoperatorsdk.operator.processing.event.EventSource;

public class IngressEvent extends DefaultEvent {
    public IngressEvent(String relatedCustomResourceUid, EventSource eventSource) {
        super(relatedCustomResourceUid, eventSource);
    }
}
