package io.halkyon;

import static io.halkyon.ExposedAppController.LABELS_CONTEXT_KEY;
import static io.halkyon.ExposedAppController.createMetadata;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.javaoperatorsdk.operator.api.config.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Builder;
import java.util.Map;

public class IngressDependent implements DependentResource<Ingress, ExposedApp>, Builder<Ingress, ExposedApp> {

  @Override
  public Ingress buildFor(ExposedApp exposedApp, Context context) {
    final var labels = (Map<String, String>) context.getMandatory(LABELS_CONTEXT_KEY, Map.class);
    final var metadata = createMetadata(exposedApp, labels);
    metadata.setAnnotations(Map.of("nginx.ingress.kubernetes.io/rewrite-target", "/"));

    final var ingress = new IngressBuilder()
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
        .build();
    ExposedAppController.log.info("Ingress {} created", ingress.getMetadata().getName());
    return ingress;
  }
}
