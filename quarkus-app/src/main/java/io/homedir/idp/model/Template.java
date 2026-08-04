package io.homedir.idp.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;

public class Template {
    @NotBlank
    private String id;

    @NotBlank
    private String name;

    @NotBlank
    private String description;

    @NotBlank
    private String version;

    private String author;

    @NotNull
    private List<TemplateParameter> parameters;

    private Map<String, String> placeholders;

    @NotBlank
    private String sourceRepo;

    private String sourceBranch = "main";

    private List<String> filesToCustomize;

    private Map<String, Object> features;

    private DeploymentConfig deployment;

    public Template() {}

    public static class DeploymentConfig {
        private String type;
        private List<String> requires;
        private Map<String, String> resources;

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public List<String> getRequires() {
            return requires;
        }

        public void setRequires(List<String> requires) {
            this.requires = requires;
        }

        public Map<String, String> getResources() {
            return resources;
        }

        public void setResources(Map<String, String> resources) {
            this.resources = resources;
        }
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public List<TemplateParameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<TemplateParameter> parameters) {
        this.parameters = parameters;
    }

    public Map<String, String> getPlaceholders() {
        return placeholders;
    }

    public void setPlaceholders(Map<String, String> placeholders) {
        this.placeholders = placeholders;
    }

    public String getSourceRepo() {
        return sourceRepo;
    }

    public void setSourceRepo(String sourceRepo) {
        this.sourceRepo = sourceRepo;
    }

    public String getSourceBranch() {
        return sourceBranch;
    }

    public void setSourceBranch(String sourceBranch) {
        this.sourceBranch = sourceBranch;
    }

    public List<String> filesToCustomize() {
        return filesToCustomize;
    }

    public void setFilesToCustomize(List<String> filesToCustomize) {
        this.filesToCustomize = filesToCustomize;
    }

    public Map<String, Object> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, Object> features) {
        this.features = features;
    }

    public DeploymentConfig getDeployment() {
        return deployment;
    }

    public void setDeployment(DeploymentConfig deployment) {
        this.deployment = deployment;
    }
}
