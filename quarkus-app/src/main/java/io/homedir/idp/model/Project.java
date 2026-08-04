package io.homedir.idp.model;

import java.time.Instant;
import java.util.Map;

public class Project {
    private String id;
    private String name;
    private String subdomain;
    private String githubOrg;
    private String githubRepo;
    private String adminEmail;
    private String templateId;
    private Map<String, String> parameters;
    private String deploymentUrl;
    private String repositoryUrl;
    private Instant createdAt;
    private Instant lastDeployedAt;
    private DeploymentPhase status;

    public Project() {}

    public Project(String id, ProjectRequest request) {
        this.id = id;
        this.name = request.getProjectName();
        this.subdomain = request.getSubdomain();
        this.githubOrg = request.getGithubOrg();
        this.githubRepo = request.getGithubOrg() + "/homedir";
        this.adminEmail = request.getAdminEmail();
        this.templateId = request.getTemplateId();
        this.parameters = request.getParameters();
        this.createdAt = Instant.now();
        this.status = DeploymentPhase.VALIDATING;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSubdomain() {
        return subdomain;
    }

    public void setSubdomain(String subdomain) {
        this.subdomain = subdomain;
    }

    public String getGithubOrg() {
        return githubOrg;
    }

    public void setGithubOrg(String githubOrg) {
        this.githubOrg = githubOrg;
    }

    public String getGithubRepo() {
        return githubRepo;
    }

    public void setGithubRepo(String githubRepo) {
        this.githubRepo = githubRepo;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }

    public String getDeploymentUrl() {
        return deploymentUrl;
    }

    public void setDeploymentUrl(String deploymentUrl) {
        this.deploymentUrl = deploymentUrl;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getLastDeployedAt() {
        return lastDeployedAt;
    }

    public void setLastDeployedAt(Instant lastDeployedAt) {
        this.lastDeployedAt = lastDeployedAt;
    }

    public DeploymentPhase getStatus() {
        return status;
    }

    public void setStatus(DeploymentPhase status) {
        this.status = status;
    }
}
