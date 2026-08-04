package io.homedir.idp.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.Map;

public class ProjectRequest {
    @NotBlank
    private String templateId;

    @NotBlank
    @Pattern(regexp = "^[a-zA-Z0-9][a-zA-Z0-9-]{0,38}[a-zA-Z0-9]$",
             message = "Project name must be 2-40 chars, alphanumeric and dashes only")
    private String projectName;

    @NotBlank
    @Pattern(regexp = "^[a-z0-9][a-z0-9-]{0,38}[a-z0-9]$",
             message = "Subdomain must be 2-40 chars, lowercase alphanumeric and dashes only")
    private String subdomain;

    @NotBlank
    private String githubOrg;

    @Email
    @NotBlank
    private String adminEmail;

    private Map<String, String> parameters;

    public ProjectRequest() {}

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
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

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public Map<String, String> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, String> parameters) {
        this.parameters = parameters;
    }
}
