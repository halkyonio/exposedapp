package io.halkyon;

import static io.halkyon.ExposedAppReconciler.LABELS_CONTEXT_KEY;
import static io.halkyon.ExposedAppReconciler.createMetadata;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import java.util.Map;
import java.util.Optional;

public class ServiceDependent implements DependentResource<Service, ExposedApp> {

  @Override
  @SuppressWarnings("unchecked")
  public Optional<Service> desired(ExposedApp exposedApp, Context context) {
    final var labels = (Map<String, String>) context.getMandatory(LABELS_CONTEXT_KEY, Map.class);

    return Optional.of(new ServiceBuilder()
        .withMetadata(createMetadata(exposedApp, labels))
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
  }
}
