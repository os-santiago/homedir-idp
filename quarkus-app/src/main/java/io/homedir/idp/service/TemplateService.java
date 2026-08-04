package io.homedir.idp.service;

import io.homedir.idp.model.Template;
import io.homedir.idp.model.TemplateParameter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class TemplateService {

    private static final Logger log = Logger.getLogger(TemplateService.class);

    @ConfigProperty(name = "idp.template.storage.path")
    String templateStoragePath;

    @Inject
    ObjectMapper objectMapper;

    private final Map<String, Template> templateCache = new HashMap<>();

    public List<Template> listTemplates() {
        try {
            loadTemplatesFromDisk();
            return new ArrayList<>(templateCache.values());
        } catch (IOException e) {
            log.error("Failed to load templates", e);
            return Collections.emptyList();
        }
    }

    public Optional<Template> getTemplate(String templateId) {
        try {
            loadTemplatesFromDisk();
            return Optional.ofNullable(templateCache.get(templateId));
        } catch (IOException e) {
            log.error("Failed to load template: " + templateId, e);
            return Optional.empty();
        }
    }

    public void validateParameters(Template template, Map<String, String> providedParams) {
        List<String> errors = new ArrayList<>();

        for (TemplateParameter param : template.getParameters()) {
            String value = providedParams.get(param.getName());

            if (param.isRequired() && (value == null || value.isBlank())) {
                errors.add("Missing required parameter: " + param.getLabel());
                continue;
            }

            if (value != null && param.getPattern() != null) {
                if (!value.matches(param.getPattern())) {
                    errors.add("Parameter '" + param.getLabel() + "' does not match pattern: " + param.getPattern());
                }
            }

            if (value != null) {
                switch (param.getType().toLowerCase()) {
                    case "email":
                        if (!value.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                            errors.add("Parameter '" + param.getLabel() + "' must be a valid email");
                        }
                        break;
                    case "url":
                        if (!value.matches("^https?://.*")) {
                            errors.add("Parameter '" + param.getLabel() + "' must be a valid URL");
                        }
                        break;
                    case "number":
                        try {
                            Integer.parseInt(value);
                        } catch (NumberFormatException e) {
                            errors.add("Parameter '" + param.getLabel() + "' must be a number");
                        }
                        break;
                }
            }
        }

        if (!errors.isEmpty()) {
            throw new IllegalArgumentException("Validation failed: " + String.join(", ", errors));
        }
    }

    public Map<String, String> buildPlaceholderMap(Template template, Map<String, String> userParams) {
        Map<String, String> placeholders = new HashMap<>();

        for (Map.Entry<String, String> entry : template.getPlaceholders().entrySet()) {
            String placeholder = entry.getKey();
            String paramName = entry.getValue().replace("${", "").replace("}", "");
            String value = userParams.get(paramName);

            if (value != null) {
                placeholders.put(placeholder, value);
            } else {
                TemplateParameter param = template.getParameters().stream()
                    .filter(p -> p.getName().equals(paramName))
                    .findFirst()
                    .orElse(null);

                if (param != null && param.getDefaultValue() != null) {
                    placeholders.put(placeholder, param.getDefaultValue());
                }
            }
        }

        return placeholders;
    }

    private void loadTemplatesFromDisk() throws IOException {
        Path templatesDir = Paths.get(templateStoragePath);

        if (!Files.exists(templatesDir)) {
            log.warn("Templates directory does not exist: " + templatesDir);
            Files.createDirectories(templatesDir);
            createDefaultTemplate();
            return;
        }

        List<Path> templateFiles = Files.walk(templatesDir, 2)
            .filter(p -> p.toString().endsWith(".json"))
            .collect(Collectors.toList());

        for (Path templateFile : templateFiles) {
            try {
                Template template = objectMapper.readValue(templateFile.toFile(), Template.class);
                templateCache.put(template.getId(), template);
                log.debug("Loaded template: " + template.getId());
            } catch (IOException e) {
                log.error("Failed to parse template file: " + templateFile, e);
            }
        }

        if (templateCache.isEmpty()) {
            log.warn("No templates found, creating default");
            createDefaultTemplate();
        }
    }

    private void createDefaultTemplate() throws IOException {
        Path templatesDir = Paths.get(templateStoragePath);
        Files.createDirectories(templatesDir);

        Template defaultTemplate = new Template();
        defaultTemplate.setId("homedir-community");
        defaultTemplate.setName("Homedir Community Edition");
        defaultTemplate.setDescription("Full-featured community platform with events, CFP, and projects");
        defaultTemplate.setVersion("1.0.0");
        defaultTemplate.setAuthor("OpenSource Santiago");
        defaultTemplate.setSourceRepo("https://github.com/os-santiago/homedir.git");
        defaultTemplate.setSourceBranch("main");

        List<TemplateParameter> params = new ArrayList<>();
        params.add(createParam("community_name", "string", "Community Name", true, "DevOpsDays Santiago"));
        params.add(createParam("subdomain", "string", "Subdomain", true, "devopsdays-santiago"));
        params.add(createParam("admin_email", "email", "Admin Email", true, null));
        params.add(createParam("github_org", "string", "GitHub Organization", true, "os-santiago"));
        defaultTemplate.setParameters(params);

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("{{COMMUNITY_NAME}}", "${community_name}");
        placeholders.put("{{SUBDOMAIN}}", "${subdomain}");
        placeholders.put("{{ADMIN_EMAIL}}", "${admin_email}");
        placeholders.put("{{GITHUB_ORG}}", "${github_org}");
        defaultTemplate.setPlaceholders(placeholders);

        List<String> filesToCustomize = Arrays.asList(
            "quarkus-app/src/main/resources/application.properties",
            ".github/workflows/release.yml",
            "platform/nginx/homedir.conf",
            "platform/systemd/homedir.service",
            "README.md"
        );
        defaultTemplate.setFilesToCustomize(filesToCustomize);

        Path templateFile = templatesDir.resolve("homedir-community.json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(templateFile.toFile(), defaultTemplate);
        templateCache.put(defaultTemplate.getId(), defaultTemplate);
        log.info("Created default template: " + templateFile);
    }

    private TemplateParameter createParam(String name, String type, String label, boolean required, String placeholder) {
        TemplateParameter param = new TemplateParameter(name, type, label, required);
        param.setPlaceholder(placeholder);
        return param;
    }
}
