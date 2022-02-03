package io.halkyon;

import static io.halkyon.ExposedAppReconciler.LABELS_CONTEXT_KEY;
import static io.halkyon.ExposedAppReconciler.createMetadata;

import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.javaoperatorsdk.operator.api.config.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import java.util.Map;

public class IngressDependent implements DependentResource<Ingress, ExposedApp> {

  @Override
  @SuppressWarnings("unchecked")
  public Ingress desired(ExposedApp exposedApp, Context context) {
    final var labels = (Map<String, String>) context.getMandatory(LABELS_CONTEXT_KEY, Map.class);
    final var metadata = createMetadata(exposedApp, labels);
    metadata.setAnnotations(Map.of("nginx.ingress.kubernetes.io/rewrite-target", "/"));

    return new IngressBuilder()
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
  }
}
