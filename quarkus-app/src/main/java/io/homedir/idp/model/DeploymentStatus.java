package io.homedir.idp.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class DeploymentStatus {
    private String deploymentId;
    private String projectName;
    private DeploymentPhase currentPhase;
    private List<PhaseLog> phaseLogs = new ArrayList<>();
    private Instant startedAt;
    private Instant completedAt;
    private String error;

    public DeploymentStatus() {}

    public DeploymentStatus(String deploymentId, String projectName) {
        this.deploymentId = deploymentId;
        this.projectName = projectName;
        this.currentPhase = DeploymentPhase.VALIDATING;
        this.startedAt = Instant.now();
    }

    public static class PhaseLog {
        private DeploymentPhase phase;
        private Instant startedAt;
        private Instant completedAt;
        private boolean success;
        private String message;

        public PhaseLog() {}

        public PhaseLog(DeploymentPhase phase) {
            this.phase = phase;
            this.startedAt = Instant.now();
        }

        public void complete(boolean success, String message) {
            this.completedAt = Instant.now();
            this.success = success;
            this.message = message;
        }

        public DeploymentPhase getPhase() {
            return phase;
        }

        public void setPhase(DeploymentPhase phase) {
            this.phase = phase;
        }

        public Instant getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(Instant startedAt) {
            this.startedAt = startedAt;
        }

        public Instant getCompletedAt() {
            return completedAt;
        }

        public void setCompletedAt(Instant completedAt) {
            this.completedAt = completedAt;
        }

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public void startPhase(DeploymentPhase phase) {
        this.currentPhase = phase;
        this.phaseLogs.add(new PhaseLog(phase));
    }

    public void completeCurrentPhase(boolean success, String message) {
        if (!phaseLogs.isEmpty()) {
            phaseLogs.get(phaseLogs.size() - 1).complete(success, message);
        }
    }

    public void fail(String error) {
        this.currentPhase = DeploymentPhase.FAILED;
        this.error = error;
        this.completedAt = Instant.now();
        completeCurrentPhase(false, error);
    }

    public void complete() {
        this.currentPhase = DeploymentPhase.COMPLETED;
        this.completedAt = Instant.now();
        completeCurrentPhase(true, "Deployment completed successfully");
    }

    public boolean isCompleted() {
        return currentPhase == DeploymentPhase.COMPLETED || currentPhase == DeploymentPhase.FAILED;
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

    public DeploymentPhase getCurrentPhase() {
        return currentPhase;
    }

    public void setCurrentPhase(DeploymentPhase currentPhase) {
        this.currentPhase = currentPhase;
    }

    public List<PhaseLog> getPhaseLogs() {
        return phaseLogs;
    }

    public void setPhaseLogs(List<PhaseLog> phaseLogs) {
        this.phaseLogs = phaseLogs;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
