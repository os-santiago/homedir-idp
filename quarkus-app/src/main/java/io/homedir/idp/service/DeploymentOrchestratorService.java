package io.homedir.idp.service;

import io.homedir.idp.model.*;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.kohsuke.github.GHRepository;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
@RegisterForReflection
public class DeploymentOrchestratorService {

    private static final Logger log = Logger.getLogger(DeploymentOrchestratorService.class);

    @Inject
    TemplateService templateService;

    @Inject
    ScaffolderService scaffolderService;

    @Inject
    GitHubService githubService;

    @Inject
    VpsProvisioningService vpsService;

    @ConfigProperty(name = "idp.vps.host")
    String vpsHost;

    @ConfigProperty(name = "idp.deployment.timeout.minutes")
    int deploymentTimeoutMinutes;

    private final Map<String, DeploymentStatus> deployments = new ConcurrentHashMap<>();

    public CompletableFuture<DeploymentResult> deployAsync(ProjectRequest request) {
        String deploymentId = UUID.randomUUID().toString();
        DeploymentStatus status = new DeploymentStatus(deploymentId, request.getProjectName());
        deployments.put(deploymentId, status);

        log.info("Starting deployment: " + deploymentId + " for project: " + request.getProjectName());

        return CompletableFuture.supplyAsync(() -> {
            try {
                return deploy(deploymentId, request, status);
            } catch (Exception e) {
                log.error("Deployment failed: " + deploymentId, e);
                status.fail(e.getMessage());
                return DeploymentResult.failure(deploymentId, request.getProjectName(), e.getMessage());
            }
        });
    }

    private DeploymentResult deploy(String deploymentId, ProjectRequest request, DeploymentStatus status) {
        Path scaffoldDir = null;

        try {
            status.startPhase(DeploymentPhase.VALIDATING);
            Template template = validateRequest(request);
            status.completeCurrentPhase(true, "Request validated");

            Map<String, String> placeholders = templateService.buildPlaceholderMap(template, request.getParameters());
            String repoName = "homedir";
            String fullRepoName = request.getGithubOrg() + "/" + repoName;

            status.startPhase(DeploymentPhase.SCAFFOLDING);
            scaffoldDir = scaffolderService.scaffoldProject(template, request.getProjectName(), placeholders);
            status.completeCurrentPhase(true, "Project scaffolded at: " + scaffoldDir);

            status.startPhase(DeploymentPhase.CREATING_REPO);
            GHRepository repo = githubService.createRepository(
                request.getGithubOrg(),
                repoName,
                "Homedir instance for " + request.getProjectName()
            );
            String repoUrl = repo.getHttpTransportUrl();
            status.completeCurrentPhase(true, "Repository created: " + repoUrl);

            scaffolderService.initGitRepository(scaffoldDir, repoUrl);
            scaffolderService.pushToGitHub(scaffoldDir, "main");
            log.info("Code pushed to GitHub: " + repoUrl);

            status.startPhase(DeploymentPhase.CONFIGURING_SECRETS);
            Map<String, String> secrets = buildGitHubSecrets(request);
            githubService.configureRepositorySecrets(request.getGithubOrg(), repoName, secrets);
            githubService.enableActions(request.getGithubOrg(), repoName);
            status.completeCurrentPhase(true, "GitHub secrets configured");

            status.startPhase(DeploymentPhase.PROVISIONING_VPS);
            vpsService.createDirectories(request.getSubdomain());
            vpsService.provisionNginx(request.getSubdomain(), "opensourcesantiago.io", 8080);
            vpsService.provisionSSL(request.getSubdomain(), "opensourcesantiago.io");
            status.completeCurrentPhase(true, "VPS infrastructure provisioned");

            status.startPhase(DeploymentPhase.DEPLOYING);
            String deploymentUrl = "https://" + request.getSubdomain() + ".opensourcesantiago.io";
            boolean healthy = vpsService.healthCheck(deploymentUrl + "/q/health");

            if (!healthy) {
                throw new RuntimeException("Health check failed after deployment");
            }

            status.completeCurrentPhase(true, "Application deployed and healthy");

            status.complete();
            log.info("Deployment completed: " + deploymentId);

            return DeploymentResult.success(deploymentId, request.getProjectName(), repoUrl, deploymentUrl);

        } catch (Exception e) {
            log.error("Deployment failed at phase: " + status.getCurrentPhase(), e);
            status.fail(e.getMessage());
            return DeploymentResult.failure(deploymentId, request.getProjectName(), e.getMessage());

        } finally {
            if (scaffoldDir != null) {
                scaffolderService.cleanup(scaffoldDir);
            }
        }
    }

    private Template validateRequest(ProjectRequest request) {
        if (request.getTemplateId() == null || request.getTemplateId().isBlank()) {
            throw new IllegalArgumentException("Template ID is required");
        }

        Template template = templateService.getTemplate(request.getTemplateId())
            .orElseThrow(() -> new IllegalArgumentException("Template not found: " + request.getTemplateId()));

        Map<String, String> params = request.getParameters();
        if (params == null) {
            params = new HashMap<>();
        }

        params.put("community_name", request.getProjectName());
        params.put("subdomain", request.getSubdomain());
        params.put("admin_email", request.getAdminEmail());
        params.put("github_org", request.getGithubOrg());
        request.setParameters(params);

        templateService.validateParameters(template, params);

        return template;
    }

    private Map<String, String> buildGitHubSecrets(ProjectRequest request) {
        Map<String, String> secrets = new HashMap<>();
        secrets.put("DEPLOY_SSH_HOST", vpsHost);
        secrets.put("DEPLOY_SSH_USER", "root");
        secrets.put("DEPLOY_SSH_PORT", "22");

        return secrets;
    }

    public DeploymentStatus getDeploymentStatus(String deploymentId) {
        DeploymentStatus status = deployments.get(deploymentId);
        if (status == null) {
            throw new IllegalArgumentException("Deployment not found: " + deploymentId);
        }
        return status;
    }

    public Map<String, DeploymentStatus> getAllDeployments() {
        return new HashMap<>(deployments);
    }
}
