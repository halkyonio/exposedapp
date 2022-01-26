package io.halkyon;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.javaoperatorsdk.operator.processing.event.Event;
import io.javaoperatorsdk.operator.processing.event.ResourceID;
import io.javaoperatorsdk.operator.processing.event.source.AbstractEventSource;

public class IngressEventSource extends AbstractEventSource implements Watcher<Ingress> {

    private IngressEventSource() {
    }

    public static IngressEventSource create(KubernetesClient client) {
        final var eventSource = new IngressEventSource();
        client.network().v1().ingresses().withLabel(ExposedAppController.APP_LABEL).watch(eventSource);
        return eventSource;
    }

    @Override
    public void eventReceived(Action action, Ingress ingress) {
        final var status = ingress.getStatus();
        if (status != null) {
            final var ingressStatus = status.getLoadBalancer().getIngress();
            final var targetExposedApp = ResourceID.fromFirstOwnerReference(ingress);
            if (!ingressStatus.isEmpty() && targetExposedApp.isPresent()) {
                getEventHandler().handleEvent(new Event(targetExposedApp.get()));
            }
        }
    }

    @Override
    public void onClose(WatcherException e) {

    }
}