package io.homedir.idp.api;

import io.homedir.idp.model.*;
import io.homedir.idp.service.DeploymentOrchestratorService;
import io.homedir.idp.service.TemplateService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Path("/api/golden-path")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class GoldenPathResource {

    private static final Logger log = Logger.getLogger(GoldenPathResource.class);

    @Inject
    TemplateService templateService;

    @Inject
    DeploymentOrchestratorService orchestrator;

    @GET
    @Path("/templates")
    public List<Template> listTemplates() {
        return templateService.listTemplates();
    }

    @GET
    @Path("/templates/{id}")
    public Response getTemplate(@PathParam("id") String templateId) {
        return templateService.getTemplate(templateId)
            .map(template -> Response.ok(template).build())
            .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Path("/deploy")
    public Response deploy(@Valid ProjectRequest request) {
        log.info("Received deployment request for: " + request.getProjectName());

        try {
            CompletableFuture<DeploymentResult> future = orchestrator.deployAsync(request);

            DeploymentResult result = future.get();

            if (result.getStatus() == DeploymentPhase.COMPLETED) {
                return Response.accepted(result).build();
            } else {
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build();
            }

        } catch (Exception e) {
            log.error("Deployment request failed", e);
            return Response.serverError()
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    @POST
    @Path("/deploy/async")
    public Response deployAsync(@Valid ProjectRequest request) {
        log.info("Received async deployment request for: " + request.getProjectName());

        try {
            CompletableFuture<DeploymentResult> future = orchestrator.deployAsync(request);

            String deploymentId = extractDeploymentId(future);

            return Response.accepted()
                .entity(Map.of(
                    "deploymentId", deploymentId,
                    "statusUrl", "/api/golden-path/status/" + deploymentId
                ))
                .build();

        } catch (Exception e) {
            log.error("Failed to start deployment", e);
            return Response.serverError()
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    @GET
    @Path("/status/{deploymentId}")
    public Response getDeploymentStatus(@PathParam("deploymentId") String deploymentId) {
        try {
            DeploymentStatus status = orchestrator.getDeploymentStatus(deploymentId);
            return Response.ok(status).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.NOT_FOUND)
                .entity(Map.of("error", "Deployment not found"))
                .build();
        }
    }

    @GET
    @Path("/deployments")
    public Map<String, DeploymentStatus> listDeployments() {
        return orchestrator.getAllDeployments();
    }

    private String extractDeploymentId(CompletableFuture<DeploymentResult> future) {
        return orchestrator.getAllDeployments().entrySet().stream()
            .filter(e -> !e.getValue().isCompleted())
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse("unknown");
    }
}
