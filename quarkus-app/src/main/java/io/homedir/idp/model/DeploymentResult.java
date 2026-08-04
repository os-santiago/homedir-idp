package io.homedir.idp.model;

public class DeploymentResult {
    private String deploymentId;
    private String projectName;
    private String githubRepoUrl;
    private String deploymentUrl;
    private String healthCheckUrl;
    private DeploymentPhase status;
    private String message;

    public DeploymentResult() {}

    public DeploymentResult(String deploymentId, String projectName) {
        this.deploymentId = deploymentId;
        this.projectName = projectName;
    }

    public static DeploymentResult success(String deploymentId, String projectName,
                                          String repoUrl, String deploymentUrl) {
        DeploymentResult result = new DeploymentResult(deploymentId, projectName);
        result.githubRepoUrl = repoUrl;
        result.deploymentUrl = deploymentUrl;
        result.healthCheckUrl = deploymentUrl + "/q/health";
        result.status = DeploymentPhase.COMPLETED;
        result.message = "Deployment completed successfully";
        return result;
    }

    public static DeploymentResult failure(String deploymentId, String projectName, String error) {
        DeploymentResult result = new DeploymentResult(deploymentId, projectName);
        result.status = DeploymentPhase.FAILED;
        result.message = error;
        return result;
    }

    public String getDeploymentId() {
        return deploymentId;
    }

    public void setDeploymentId(String deploymentId) {
        this.deploymentId = deploymentId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getGithubRepoUrl() {
        return githubRepoUrl;
    }

    public void setGithubRepoUrl(String githubRepoUrl) {
        this.githubRepoUrl = githubRepoUrl;
    }

    public String getDeploymentUrl() {
        return deploymentUrl;
    }

    public void setDeploymentUrl(String deploymentUrl) {
        this.deploymentUrl = deploymentUrl;
    }

    public String getHealthCheckUrl() {
        return healthCheckUrl;
    }

    public void setHealthCheckUrl(String healthCheckUrl) {
        this.healthCheckUrl = healthCheckUrl;
    }

    public DeploymentPhase getStatus() {
        return status;
    }

    public void setStatus(DeploymentPhase status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
