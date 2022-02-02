package io.halkyon;

import static io.halkyon.ExposedAppReconciler.LABELS_CONTEXT_KEY;
import static io.halkyon.ExposedAppReconciler.createMetadata;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.javaoperatorsdk.operator.api.config.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Builder;
import java.util.Map;

public class ServiceDependent implements DependentResource<Service, ExposedApp>, Builder<Service, ExposedApp> {

  @Override
  @SuppressWarnings("unchecked")
  public Service buildFor(ExposedApp exposedApp, Context context) {
    final var labels = (Map<String, String>) context.getMandatory(LABELS_CONTEXT_KEY, Map.class);

    final var service = new ServiceBuilder()
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
        .build();
    ExposedAppReconciler.log.info("Service {} created", service.getMetadata().getName());
    return service;
  }
}
