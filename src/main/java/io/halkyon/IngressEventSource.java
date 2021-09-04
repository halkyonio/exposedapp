package io.halkyon;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.Watcher;
import io.fabric8.kubernetes.client.WatcherException;
import io.javaoperatorsdk.operator.processing.event.AbstractEventSource;

public class IngressEventSource extends AbstractEventSource implements Watcher<Ingress> {
    private final KubernetesClient client;

    private IngressEventSource(KubernetesClient client) {
        this.client = client;
    }

    public static IngressEventSource create(KubernetesClient client) {
        final var eventSource = new IngressEventSource(client);
        client.network().v1().ingresses().withLabel(ExposedAppController.APP_LABEL).watch(eventSource);
        return eventSource;
    }

    @Override
    public void eventReceived(Action action, Ingress ingress) {
        final var uid = ingress.getMetadata().getOwnerReferences().get(0).getUid();
        final var status = ingress.getStatus();
        if (status != null) {
            final var ingressStatus = status.getLoadBalancer().getIngress();
            if (!ingressStatus.isEmpty()) {
                eventHandler.handleEvent(new IngressEvent(uid, this));
            }
        }
    }

    @Override
    public void onClose(WatcherException e) {

    }
}