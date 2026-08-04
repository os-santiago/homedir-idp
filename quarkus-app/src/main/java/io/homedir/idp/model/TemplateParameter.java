package io.homedir.idp.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class TemplateParameter {
    @NotBlank
    private String name;

    @NotBlank
    private String type; // string, email, url, number, boolean

    @NotBlank
    private String label;

    private String description;
    private String placeholder;
    private String defaultValue;
    private boolean required;

    @Pattern(regexp = "^[a-zA-Z0-9-_*+.@]+$")
    private String pattern;

    public TemplateParameter() {}

    public TemplateParameter(String name, String type, String label, boolean required) {
        this.name = name;
        this.type = type;
        this.label = label;
        this.required = required;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public void setPlaceholder(String placeholder) {
        this.placeholder = placeholder;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }
}
