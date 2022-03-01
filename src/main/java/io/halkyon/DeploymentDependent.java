package io.halkyon;

import static io.halkyon.ExposedAppReconciler.LABELS_CONTEXT_KEY;
import static io.halkyon.ExposedAppReconciler.createMetadata;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Creator;
import io.javaoperatorsdk.operator.api.reconciler.dependent.Matcher;
import io.javaoperatorsdk.operator.processing.dependent.kubernetes.KubernetesDependentResource;
import java.util.Map;

public class DeploymentDependent extends
    KubernetesDependentResource<Deployment, ExposedApp> implements
    Creator<Deployment, ExposedApp>, Matcher<Deployment> {

  @SuppressWarnings("unchecked")
  public Deployment desired(ExposedApp exposedApp, Context context) {
    final var labels = (Map<String, String>) context.managedDependentResourceContext()
        .getMandatory(LABELS_CONTEXT_KEY, Map.class);
    final var name = exposedApp.getMetadata().getName();
    final var spec = exposedApp.getSpec();
    final var imageRef = spec.getImageRef();
    final var env = spec.getEnv();

    var containerBuilder = new DeploymentBuilder()
        .withMetadata(createMetadata(exposedApp, labels))
        .withNewSpec()
        .withNewSelector().withMatchLabels(labels).endSelector()
        .withNewTemplate()
        .withNewMetadata().withLabels(labels).endMetadata()
        .withNewSpec()
        .addNewContainer()
        .withName(name).withImage(imageRef);

    // add env variables
    if (env != null) {
      env.forEach((key, value) -> containerBuilder.addNewEnv()
          .withName(key.toUpperCase())
          .withValue(value)
          .endEnv());
    }

    return containerBuilder
        .addNewPort()
        .withName("http").withProtocol("TCP").withContainerPort(8080)
        .endPort()
        .endContainer()
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
  }


  @Override
  public boolean match(Deployment actual, Deployment desired, Context context) {
    final var maybeDesiredContainer = desired.getSpec().getTemplate().getSpec().getContainers()
        .stream()
        .findFirst();
    if (maybeDesiredContainer.isEmpty()) {
      return true;
    }
    final var desiredContainer = maybeDesiredContainer.get();

    final var container = actual.getSpec().getTemplate().getSpec().getContainers()
        .stream()
        .findFirst();
    return container.map(c -> desiredContainer.getImage().equals(c.getImage())
            && desiredContainer.getEnv().equals(c.getEnv()))
        .orElse(false);
  }
}
