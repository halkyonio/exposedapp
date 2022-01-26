package io.halkyon;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceContext;
import io.javaoperatorsdk.operator.api.reconciler.EventSourceInitializer;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import io.javaoperatorsdk.operator.processing.event.source.EventSource;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration(namespaces = WATCH_CURRENT_NAMESPACE, name = "exposedapp")
public class ExposedAppController implements Reconciler<ExposedApp>, EventSourceInitializer<ExposedApp> {
    private static final Logger log = LoggerFactory.getLogger(ExposedAppController.class);
    public static final String APP_LABEL = "app.kubernetes.io/name";
    private final KubernetesClient client;

    public ExposedAppController(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public List<EventSource> prepareEventSources(EventSourceContext<ExposedApp> eventSourceContext) {
        return List.of(IngressEventSource.create(client));
    }
    
    @Override
    public UpdateControl<ExposedApp> reconcile(ExposedApp resource, Context context) {
        final var spec = resource.getSpec();
        final var name = resource.getMetadata().getName();
        final var imageRef = spec.getImageRef();
        log.info("Exposing {} application from image {}", name, imageRef);

        // create Deployment
        var labels = Map.of(APP_LABEL, name);
        final var deployment = client.apps().deployments().createOrReplace(new DeploymentBuilder()
                .withMetadata(createMetadata(resource, labels))
                .withNewSpec()
                .withNewSelector().withMatchLabels(labels).endSelector()
                .withNewTemplate()
                .withNewMetadata().withLabels(labels).endMetadata()
                .withNewSpec()
                .addNewContainer()
                .withName(name).withImage(imageRef)
                .addNewPort()
                .withName("http").withProtocol("TCP").withContainerPort(8080)
                .endPort()
                .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()
                .build());
        log.info("Deployment {} handled", deployment.getMetadata().getName());

        // create Service
        final var service = client.services().createOrReplace(new ServiceBuilder()
                .withMetadata(createMetadata(resource, labels))
                .withNewSpec()
                .addNewPort()
                .withName("http")
                .withPort(8080)
                .withNewTargetPort().withIntVal(8080).endTargetPort()
                .endPort()
                .withSelector(labels)
                .withType("ClusterIP")
                .endSpec()
                .build());
        log.info("Service {} handled", service.getMetadata().getName());

        // Create Ingress
        final var metadata = createMetadata(resource, labels);
        metadata.setAnnotations(Map.of("nginx.ingress.kubernetes.io/rewrite-target", "/"));

        final var ingress = client.network().v1().ingresses().createOrReplace(new IngressBuilder()
                .withMetadata(metadata)
                .withNewSpec()
                .addNewRule()
                .withNewHttp()
                .addNewPath()
                .withPath("/")
                .withPathType("Prefix")
                .withNewBackend()
                .withNewService()
                .withName(metadata.getName())
                .withNewPort().withNumber(8080).endPort()
                .endService()
                .endBackend()
                .endPath()
                .endHttp()
                .endRule()
                .endSpec()
                .build());
        log.info("Ingress {} handled", ingress.getMetadata().getName());

        // add status to resource
        final var status = ingress.getStatus();
        if (status != null) {
            final var ingresses = status.getLoadBalancer().getIngress();
            if (ingresses != null && !ingresses.isEmpty()) {
                // only set the status if the ingress is ready to provide the info we need
                final var url = "https://" + ingresses.get(0).getHostname();
                log.info("App {} is exposed and ready to used at {}", name, url);
                resource.setStatus(new ExposedAppStatus("exposed", url));
                return UpdateControl.updateStatus(resource);
            }
        }

        resource.setStatus(new ExposedAppStatus("processing", null));
        return UpdateControl.updateStatus(resource);
    }

    private ObjectMeta createMetadata(ExposedApp resource, Map<String, String> labels) {
        final var metadata = resource.getMetadata();
        return new ObjectMetaBuilder()
                .withName(metadata.getName())
                .addNewOwnerReference()
                .withUid(metadata.getUid())
                .withApiVersion(resource.getApiVersion())
                .withName(metadata.getName())
                .withKind(resource.getKind())
                .endOwnerReference()
                .withLabels(labels)
                .build();
    }

}

