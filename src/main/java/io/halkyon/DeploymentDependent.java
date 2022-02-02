package io.halkyon;

import static io.halkyon.ExposedAppReconciler.LABELS_CONTEXT_KEY;
import static io.halkyon.ExposedAppReconciler.createMetadata;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.config.DependentResource;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Builder;
import java.util.Map;

public class DeploymentDependent implements DependentResource<Deployment, ExposedApp>,
    Builder<Deployment, ExposedApp> {

  @Override
  @SuppressWarnings("unchecked")
  public Deployment buildFor(ExposedApp exposedApp, Context context) {
    final var labels = (Map<String, String>) context.getMandatory(LABELS_CONTEXT_KEY, Map.class);
    final var name = exposedApp.getMetadata().getName();
    final var imageRef = exposedApp.getSpec().getImageRef();

    final var deployment = new DeploymentBuilder()
        .withMetadata(createMetadata(exposedApp, labels))
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
        .build();
    ExposedAppReconciler.log.info("Deployment {} created", deployment.getMetadata().getName());
    return deployment;
  }
}
