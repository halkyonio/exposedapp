package io.halkyon;

import static io.javaoperatorsdk.operator.api.reconciler.Constants.WATCH_CURRENT_NAMESPACE;

import io.fabric8.kubernetes.api.model.LoadBalancerIngress;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.ObjectMetaBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.javaoperatorsdk.operator.api.config.dependent.Dependent;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.ContextInitializer;
import io.javaoperatorsdk.operator.api.reconciler.ControllerConfiguration;
import io.javaoperatorsdk.operator.api.reconciler.Reconciler;
import io.javaoperatorsdk.operator.api.reconciler.UpdateControl;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ControllerConfiguration(
    namespaces = WATCH_CURRENT_NAMESPACE,
    name = "exposedapp",
    dependents = {
        @Dependent(resourceType = Deployment.class, type = DeploymentDependent.class),
        @Dependent(resourceType = Service.class, type = ServiceDependent.class),
        @Dependent(resourceType = Ingress.class, type = IngressDependent.class)
    }
)
public class ExposedAppReconciler implements Reconciler<ExposedApp>, ContextInitializer<ExposedApp> {
    static final Logger log = LoggerFactory.getLogger(ExposedAppReconciler.class);
    static final String APP_LABEL = "app.kubernetes.io/name";
    static final String LABELS_CONTEXT_KEY = "labels";

    public ExposedAppReconciler() {}

    @Override
    public void initContext(ExposedApp exposedApp, Context context) {
        context.put(LABELS_CONTEXT_KEY, Map.of(APP_LABEL, exposedApp.getMetadata().getName()));
    }

    @Override
    public UpdateControl<ExposedApp> reconcile(ExposedApp exposedApp, Context context) {
        final var spec = exposedApp.getSpec();
        final var name = exposedApp.getMetadata().getName();
        final var imageRef = spec.getImageRef();
        log.info("Exposing {} application from image {}", name, imageRef);

        // add status to resource
        return context.getSecondaryResource(Ingress.class).map(ingress -> {
            final var status = ingress.getStatus();
            if (status != null) {
                final var ingresses = status.getLoadBalancer().getIngress();
                if (ingresses != null && !ingresses.isEmpty()) {
                    // only set the status if the ingress is ready to provide the info we need
                    LoadBalancerIngress ing = ingresses.get(0);
                    String hostname = ing.getHostname();
                    final var url = "https://" + (hostname != null ?  hostname : ing.getIp());
                    log.info("App {} is exposed and ready to used at {}", name, url);
                    exposedApp.setStatus(new ExposedAppStatus("exposed", url));
                    return UpdateControl.updateStatus(exposedApp);
                }
            }
            return UpdateControl.<ExposedApp>noUpdate();
        }).orElseGet(() -> {
            exposedApp.setStatus(new ExposedAppStatus("processing", null));
            return UpdateControl.updateStatus(exposedApp);
        });
    }

    static ObjectMeta createMetadata(ExposedApp resource, Map<String, String> labels) {
        final var metadata = resource.getMetadata();
        return new ObjectMetaBuilder()
                .withName(metadata.getName())
                .withNamespace(metadata.getNamespace())
                .withLabels(labels)
                .build();
    }
}

