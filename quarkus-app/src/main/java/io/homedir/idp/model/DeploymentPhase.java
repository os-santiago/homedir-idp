package io.homedir.idp.model;

public enum DeploymentPhase {
    VALIDATING("Validating request"),
    SCAFFOLDING("Scaffolding project"),
    CREATING_REPO("Creating GitHub repository"),
    CONFIGURING_SECRETS("Configuring GitHub secrets"),
    PROVISIONING_VPS("Provisioning VPS infrastructure"),
    DEPLOYING("Deploying application"),
    COMPLETED("Deployment completed"),
    FAILED("Deployment failed");

    private final String description;

    DeploymentPhase(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
