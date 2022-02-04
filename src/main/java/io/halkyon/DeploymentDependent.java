package io.halkyon;

import static io.halkyon.ExposedAppReconciler.LABELS_CONTEXT_KEY;
import static io.halkyon.ExposedAppReconciler.createMetadata;

import io.fabric8.kubernetes.api.model.Container;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.javaoperatorsdk.operator.api.reconciler.Context;
import io.javaoperatorsdk.operator.api.reconciler.dependent.DependentResource;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class DeploymentDependent implements DependentResource<Deployment, ExposedApp> {

  @SuppressWarnings("unchecked")
  public Optional<Deployment> desired(ExposedApp exposedApp, Context context) {
    final var labels = (Map<String, String>) context.getMandatory(LABELS_CONTEXT_KEY, Map.class);
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

    return Optional.of(containerBuilder
        .addNewPort()
        .withName("http").withProtocol("TCP").withContainerPort(8080)
        .endPort()
        .endContainer()
        .endSpec()
        .endTemplate()
        .endSpec()
        .build());
  }

  @Override
  public boolean match(Deployment actual, ExposedApp primary, Context context) {
    final var spec = primary.getSpec();
    Optional<Container> container = actual.getSpec().getTemplate().getSpec().getContainers()
        .stream()
        .findFirst();
    return container.map(
            c -> spec.getImageRef().equals(c.getImage()) && (spec.getEnv() == null || spec.getEnv()
                .equals(convert(c.getEnv()))))
        .orElse(false);
  }

  private Map<String, String> convert(List<EnvVar> envVars) {
    final var result = new HashMap<String, String>(envVars.size());
    envVars.forEach(
        envVar -> result.put(envVar.getName().toLowerCase(Locale.ROOT), envVar.getValue()));
    return result;
  }
}
