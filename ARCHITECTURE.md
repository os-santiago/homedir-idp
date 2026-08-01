# Homedir IDP - Architecture Documentation

> **Version:** 1.0.0 (MVP)  
> **Date:** 2026-08-01  
> **Status:** Design Phase

---

## Table of Contents

1. [Overview](#overview)
2. [System Context](#system-context)
3. [Architecture Principles](#architecture-principles)
4. [Component Architecture](#component-architecture)
5. [Data Model](#data-model)
6. [API Design](#api-design)
7. [Template System](#template-system)
8. [Deployment Pipeline](#deployment-pipeline)
9. [Security Architecture](#security-architecture)
10. [Scalability & Performance](#scalability--performance)

---

## Overview

Homedir IDP (Internal Developer Platform) is a self-service portal that enables tech communities to provision their own Homedir instances through golden path templates and automated deployment.

### Goals

- **Time to Instance:** <5 minutes from request to live deployment
- **Zero Infrastructure Knowledge:** Community admins shouldn't need DevOps expertise
- **Template Flexibility:** Support multiple deployment profiles (minimal, full, custom)
- **VPS Efficiency:** Deploy multiple instances on single VPS with resource isolation

### Non-Goals (MVP)

- Multi-cloud deployment (AWS/GCP/Azure)
- Kubernetes orchestration
- Advanced monitoring/observability
- Custom plugin marketplace

---

## System Context

```
┌─────────────────────────────────────────────────────────┐
│                     External Systems                     │
├─────────────────────────────────────────────────────────┤
│                                                          │
│  ┌──────────────┐  ┌─────────────┐  ┌───────────────┐  │
│  │   GitHub     │  │  VPS Server │  │  OAuth Providers│
│  │              │  │             │  │                │  │
│  │ - Repo API   │  │ - SSH       │  │ - Google      │  │
│  │ - Templates  │  │ - Podman    │  │ - GitHub      │  │
│  │ - Auth       │  │ - Nginx     │  │ - Discord     │  │
│  └──────────────┘  └─────────────┘  └───────────────┘  │
│         ↑                 ↑                  ↑           │
└─────────┼─────────────────┼──────────────────┼───────────┘
          │                 │                  │
┌─────────┼─────────────────┼──────────────────┼───────────┐
│         │      Homedir IDP │                  │           │
│         ↓                 ↓                  ↓           │
│  ┌──────────────────────────────────────────────────┐   │
│  │              Frontend (Qute SSR)                 │   │
│  │  /templates  /wizard  /dashboard  /docs          │   │
│  └──────────────────────────────────────────────────┘   │
│                         ↕                                │
│  ┌──────────────────────────────────────────────────┐   │
│  │         REST API (Quarkus JAX-RS)                │   │
│  │  TemplateResource | ProjectResource | ...        │   │
│  └──────────────────────────────────────────────────┘   │
│                         ↕                                │
│  ┌──────────────────────────────────────────────────┐   │
│  │              Service Layer                       │   │
│  │  ScaffolderService | GitHubService | ...         │   │
│  └──────────────────────────────────────────────────┘   │
│                         ↕                                │
│  ┌──────────────────────────────────────────────────┐   │
│  │         Data Access (Repository Pattern)         │   │
│  │  JSON files → PostgreSQL (future)                │   │
│  └──────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────┘
                         ↓
           ┌─────────────────────────────┐
           │  Deployed Homedir Instances │
           │  community-a.homedir.io     │
           │  community-b.homedir.io     │
           └─────────────────────────────┘
```

---

## Architecture Principles

### 1. **Simplicity Over Complexity**
- Start with JSON file storage, migrate to PostgreSQL only when needed
- Prefer bash scripts over complex orchestration tools
- Reuse Homedir's existing deployment infrastructure

### 2. **Consistent Stack**
- Same technology as Homedir (Java 21, Quarkus, Qute)
- Developers familiar with Homedir can contribute immediately
- No context switching between projects

### 3. **Fail-Safe Defaults**
- Templates should work out-of-box with minimal configuration
- Validation before deployment prevents broken instances
- Rollback capability for failed deployments

### 4. **Observable Operations**
- Every action logs to structured format
- Deployment status visible in real-time
- Metrics for troubleshooting

### 5. **Security by Design**
- Input validation at every boundary
- Secrets never logged or exposed
- Rate limiting on expensive operations (scaffolding, deployment)

---

## Component Architecture

### Frontend Layer (Qute Templates)

**Purpose:** Server-side rendered UI for template browsing, project creation, and management.

**Components:**

1. **Template Catalog** (`/templates`)
   - Grid view of available templates
   - Filter by features (events, CFP, projects)
   - Template details modal

2. **Creation Wizard** (`/templates/{id}/create`)
   - Multi-step form (name, config, features, review)
   - Real-time validation
   - Preview before submission

3. **Project Dashboard** (`/dashboard`)
   - List of created instances
   - Status indicators (deploying, running, failed)
   - Actions: view logs, restart, upgrade, delete

4. **Documentation** (`/docs`)
   - Template creation guide
   - API reference
   - Deployment troubleshooting

**Technology:**
- Qute for templating (consistent with Homedir)
- Vanilla JavaScript (no frontend framework)
- CSS from Homedir core (shared design system)

---

### REST API Layer

**Endpoints:**

#### Templates API

```java
@Path("/api/templates")
public class TemplateResource {
    
    @GET
    public List<Template> listTemplates();
    
    @GET
    @Path("/{id}")
    public Template getTemplate(@PathParam("id") String id);
    
    @POST
    @Path("/{id}/validate")
    public ValidationResult validateParameters(
        @PathParam("id") String id,
        Map<String, String> params
    );
}
```

#### Projects API

```java
@Path("/api/projects")
public class ProjectResource {
    
    @POST
    public Response createProject(CreateProjectRequest request);
    
    @GET
    public List<Project> listProjects();
    
    @GET
    @Path("/{id}")
    public Project getProject(@PathParam("id") String id);
    
    @POST
    @Path("/{id}/deploy")
    public Response deployProject(@PathParam("id") String id);
    
    @DELETE
    @Path("/{id}")
    public Response deleteProject(@PathParam("id") String id);
}
```

#### Deployments API

```java
@Path("/api/deployments")
public class DeploymentResource {
    
    @GET
    @Path("/{id}/status")
    public DeploymentStatus getStatus(@PathParam("id") String id);
    
    @GET
    @Path("/{id}/logs")
    public Response streamLogs(@PathParam("id") String id);
    
    @POST
    @Path("/{id}/rollback")
    public Response rollback(@PathParam("id") String id);
}
```

---

### Service Layer

#### ScaffolderService

**Responsibilities:**
- Clone template repository
- Replace placeholders with user parameters
- Validate generated files
- Create GitHub repository
- Push customized code

**Algorithm:**

```java
public Project scaffold(Template template, Map<String, String> params) {
    // 1. Validate inputs
    validateParameters(template, params);
    
    // 2. Create temporary workspace
    Path workspace = Files.createTempDirectory("scaffold-");
    
    try {
        // 3. Clone template
        Git.cloneRepository()
           .setURI(template.getSource())
           .setDirectory(workspace.toFile())
           .call();
        
        // 4. Apply transformations
        for (String file : template.getFilesToCustomize()) {
            String content = Files.readString(workspace.resolve(file));
            for (var placeholder : template.getPlaceholders().entrySet()) {
                String value = params.get(placeholder.getValue());
                content = content.replace(placeholder.getKey(), value);
            }
            Files.writeString(workspace.resolve(file), content);
        }
        
        // 5. Validate output
        validateScaffold(workspace);
        
        // 6. Create GitHub repo
        String repoName = params.get("repo_name");
        githubService.createRepository(repoName);
        
        // 7. Push code
        Git git = Git.open(workspace.toFile());
        git.push()
           .setRemote("origin")
           .setCredentialsProvider(githubCredentials)
           .call();
        
        // 8. Register project
        return projectRepository.save(new Project(
            UUID.randomUUID().toString(),
            template.getId(),
            params,
            ProjectStatus.SCAFFOLDED
        ));
        
    } finally {
        FileUtils.deleteDirectory(workspace.toFile());
    }
}
```

#### GitHubService

**Responsibilities:**
- Create repositories via GitHub API
- Configure webhooks for deployment triggers
- Manage access permissions

**API Integration:**

```java
public void createRepository(String name, String org) {
    GitHub github = new GitHubBuilder()
        .withOAuthToken(githubToken)
        .build();
    
    GHOrganization organization = github.getOrganization(org);
    
    organization.createRepository(name)
        .description("Homedir instance - created by IDP")
        .private_(false)
        .autoInit(false)
        .create();
}
```

#### DeploymentService

**Responsibilities:**
- Execute deployment scripts on VPS
- Stream logs back to API
- Monitor health checks
- Handle rollbacks

**Deployment Flow:**

```java
public Deployment deploy(Project project) {
    Deployment deployment = new Deployment(
        UUID.randomUUID().toString(),
        project.getId(),
        DeploymentStatus.IN_PROGRESS
    );
    
    // Execute async
    CompletableFuture.runAsync(() -> {
        try {
            // 1. SSH into VPS
            Session session = sshClient.connect(vpsHost);
            
            // 2. Run deployment script
            String script = generateDeployScript(project);
            ChannelExec channel = session.openChannel("exec");
            channel.setCommand(script);
            
            // 3. Stream logs
            InputStream stdout = channel.getInputStream();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(stdout)
            );
            
            String line;
            while ((line = reader.readLine()) != null) {
                deployment.appendLog(line);
                logPublisher.publish(deployment.getId(), line);
            }
            
            // 4. Verify deployment
            int exitCode = channel.getExitStatus();
            if (exitCode == 0) {
                deployment.setStatus(DeploymentStatus.SUCCESS);
                runHealthCheck(project);
            } else {
                deployment.setStatus(DeploymentStatus.FAILED);
            }
            
        } catch (Exception e) {
            deployment.setStatus(DeploymentStatus.FAILED);
            deployment.setError(e.getMessage());
        } finally {
            deploymentRepository.save(deployment);
        }
    });
    
    return deployment;
}
```

---

## Data Model

### Template

```java
public class Template {
    private String id;                    // "homedir-community"
    private String name;                  // "Homedir Community Edition"
    private String description;
    private String version;               // "1.0.0"
    private String author;
    private Map<String, Feature> features;
    private List<Parameter> parameters;
    private ScaffoldConfig scaffold;
    private DeploymentConfig deployment;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

public class Feature {
    private boolean enabled;
    private boolean configurable;
    private String description;
}

public class Parameter {
    private String name;
    private String type;              // "string" | "email" | "number" | "boolean"
    private String label;
    private String placeholder;
    private String pattern;           // Regex for validation
    private boolean required;
    private String defaultValue;
}

public class ScaffoldConfig {
    private String source;            // GitHub URL
    private String branch;
    private List<String> filesToCustomize;
    private Map<String, String> placeholders;
}

public class DeploymentConfig {
    private String type;              // "vps-podman"
    private List<String> requires;    // ["java-21", "podman"]
    private ResourceRequirements resources;
}
```

### Project

```java
public class Project {
    private String id;                    // UUID
    private String templateId;
    private String name;
    private String subdomain;
    private String githubRepo;
    private String adminEmail;
    private Map<String, String> parameters;
    private ProjectStatus status;
    private List<Deployment> deployments;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;            // User ID
}

public enum ProjectStatus {
    CREATED,          // Project registered
    SCAFFOLDED,       // Code generated and pushed to GitHub
    DEPLOYING,        // Deployment in progress
    RUNNING,          // Successfully deployed and healthy
    FAILED,           // Deployment failed
    DELETED           // Soft delete
}
```

### Deployment

```java
public class Deployment {
    private String id;                    // UUID
    private String projectId;
    private DeploymentStatus status;
    private String version;               // Git commit SHA
    private String vpsHost;
    private String containerName;
    private List<String> logs;
    private String errorMessage;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
}

public enum DeploymentStatus {
    QUEUED,
    IN_PROGRESS,
    SUCCESS,
    FAILED,
    ROLLED_BACK
}
```

---

## Template System

### Template Discovery

Templates live in `/templates` directory:

```
templates/
├── homedir-community/
│   ├── template.json
│   └── scaffold/
│       ├── README.md
│       ├── application.properties.template
│       └── .env.template
├── homedir-minimal/
│   └── template.json
└── homedir-enterprise/
    └── template.json
```

### Template Validation

On startup, IDP validates all templates:

```java
@ApplicationScoped
public class TemplateValidator {
    
    public void validateAll() {
        Files.list(Paths.get("templates"))
             .filter(Files::isDirectory)
             .forEach(dir -> {
                 Path templateFile = dir.resolve("template.json");
                 if (!Files.exists(templateFile)) {
                     throw new IllegalStateException(
                         "Missing template.json in " + dir
                     );
                 }
                 
                 Template template = loadTemplate(templateFile);
                 validate(template);
             });
    }
    
    private void validate(Template template) {
        // Check required fields
        assert template.getId() != null;
        assert template.getName() != null;
        assert template.getScaffold() != null;
        
        // Validate scaffold config
        assert new URI(template.getScaffold().getSource()) != null;
        
        // Validate parameters
        template.getParameters().forEach(param -> {
            if (param.isRequired()) {
                assert param.getName() != null;
                assert param.getLabel() != null;
            }
        });
    }
}
```

---

## Deployment Pipeline

### Local Development

```bash
# 1. User creates project via UI
POST /api/projects
{
  "templateId": "homedir-community",
  "name": "DevOpsDays Chile",
  "subdomain": "devopsdays-chile",
  "githubRepo": "devopsdays-chile/homedir",
  "adminEmail": "admin@devopsdays.cl"
}

# 2. Scaffolder generates code
- Clone template
- Replace placeholders
- Validate output

# 3. GitHub integration
- Create repo
- Push customized code
- Configure webhooks

# 4. VPS deployment
ssh root@vps << 'EOF'
  # Pull repo
  cd /opt/homedir-instances
  git clone https://github.com/devopsdays-chile/homedir.git
  cd homedir
  
  # Build native image
  cd quarkus-app
  ./mvnw package -Pnative
  
  # Deploy with Podman
  podman run -d \
    --name devopsdays-chile \
    -p 8081:8080 \
    -v $(pwd)/data:/work/data \
    -e QUARKUS_HTTP_HOST=0.0.0.0 \
    quay.io/quarkus/ubi-quarkus-native-binary-s2i:latest \
    ./target/homedir-runner
  
  # Configure nginx
  cat > /etc/nginx/sites-available/devopsdays-chile << 'NGINX'
  server {
    listen 80;
    server_name devopsdays-chile.homedir.io;
    location / {
      proxy_pass http://localhost:8081;
    }
  }
NGINX
  ln -s /etc/nginx/sites-available/devopsdays-chile \
        /etc/nginx/sites-enabled/
  nginx -s reload
EOF

# 5. Health check
curl https://devopsdays-chile.homedir.io/q/health
```

### Production Deployment (IDP itself)

```yaml
# .github/workflows/deploy.yml
name: Deploy IDP

on:
  push:
    branches: [main]

jobs:
  deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      - name: Build native image
        run: |
          cd quarkus-app
          ./mvnw package -Pnative
      
      - name: Deploy to VPS
        uses: appleboy/ssh-action@master
        with:
          host: ${{ secrets.VPS_HOST }}
          username: root
          key: ${{ secrets.SSH_KEY }}
          script: |
            cd /opt/homedir-idp
            git pull
            podman stop homedir-idp || true
            podman rm homedir-idp || true
            podman run -d \
              --name homedir-idp \
              -p 8090:8080 \
              -v $(pwd)/data:/work/data \
              quay.io/quarkus/ubi-quarkus-native-binary-s2i:latest \
              ./target/idp-runner
```

---

## Security Architecture

### Authentication

**Phase 1 (MVP):** GitHub OAuth
- Users authenticate via GitHub
- Only authenticated users can create projects
- GitHub org membership determines access level

**Phase 2:** Multi-provider
- Google OAuth
- Discord OAuth
- Email/password (local accounts)

### Authorization

**RBAC Model:**

```
Roles:
- ADMIN: Full access (create templates, manage all projects)
- USER: Create and manage own projects
- VIEWER: Read-only access

Permissions:
- template:read
- template:write (ADMIN only)
- project:create (USER, ADMIN)
- project:read (own projects or ADMIN)
- project:delete (own projects or ADMIN)
- deployment:trigger (own projects or ADMIN)
```

### Input Validation

```java
@ApplicationScoped
public class InputValidator {
    
    private static final Pattern SUBDOMAIN_PATTERN = 
        Pattern.compile("^[a-z0-9][a-z0-9-]{1,61}[a-z0-9]$");
    
    public void validateSubdomain(String subdomain) {
        if (!SUBDOMAIN_PATTERN.matcher(subdomain).matches()) {
            throw new ValidationException(
                "Subdomain must be lowercase, alphanumeric, " +
                "with hyphens (2-63 chars)"
            );
        }
        
        // Check availability
        if (projectRepository.existsBySubdomain(subdomain)) {
            throw new ConflictException(
                "Subdomain already taken"
            );
        }
    }
    
    public void validateEmail(String email) {
        if (!EmailValidator.getInstance().isValid(email)) {
            throw new ValidationException("Invalid email");
        }
    }
    
    public void validateGitHubRepo(String repo) {
        // Format: org/name
        if (!repo.matches("^[a-zA-Z0-9-_]+/[a-zA-Z0-9-_]+$")) {
            throw new ValidationException(
                "GitHub repo format: org/name"
            );
        }
    }
}
```

### Secrets Management

```java
// Never log secrets
@ConfigProperty(name = "github.token")
String githubToken;  // From env var, not committed

// Rotate regularly
@Scheduled(cron = "0 0 1 * * ?")  // Monthly
void rotateApiKeys() {
    // Trigger rotation workflow
}

// Audit access
@Logged
public void accessSecret(String secretName) {
    auditLog.info("Secret accessed: {}, by: {}", 
                  secretName, 
                  securityContext.getUserPrincipal());
}
```

---

## Scalability & Performance

### Horizontal Scaling

**Current (MVP):** Single instance
- Quarkus application on 1 VPS
- Handles 10-20 concurrent scaffolding operations

**Future:** Multiple instances
- Load balancer (nginx)
- Shared storage (NFS or S3 for templates)
- Database migration (JSON → PostgreSQL)

### Resource Limits

**Per Instance:**
- CPU: 2 cores
- Memory: 4GB
- Disk: 20GB

**VPS Capacity:**
- 16 cores, 64GB RAM → ~25 instances
- Monitor with Prometheus + Grafana

### Caching Strategy

```java
@ApplicationScoped
public class TemplateCacheService {
    
    private final Cache<String, Template> templateCache = 
        CacheBuilder.newBuilder()
            .maximumSize(100)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
    
    public Template getTemplate(String id) {
        return templateCache.get(id, () -> {
            return templateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException());
        });
    }
}
```

---

## Monitoring & Observability

### Metrics

```java
@ApplicationScoped
public class IDPMetrics {
    
    @Gauge(name = "idp_projects_total")
    public long totalProjects() {
        return projectRepository.count();
    }
    
    @Gauge(name = "idp_deployments_in_progress")
    public long deploymentsInProgress() {
        return deploymentRepository.countByStatus(IN_PROGRESS);
    }
    
    @Timed(name = "idp_scaffold_duration")
    public Project scaffold(Template template, Map<String, String> params) {
        // Timed by annotation
    }
}
```

### Logging

```java
// Structured logging (JSON)
{
  "timestamp": "2026-08-01T10:30:00Z",
  "level": "INFO",
  "logger": "ScaffolderService",
  "message": "Scaffolding started",
  "context": {
    "templateId": "homedir-community",
    "projectId": "uuid-123",
    "userId": "github:scanales-stack"
  }
}
```

### Alerting

```yaml
# Prometheus alerts
groups:
  - name: idp
    rules:
      - alert: HighScaffoldFailureRate
        expr: rate(idp_scaffold_failures[5m]) > 0.1
        annotations:
          summary: Scaffolding failing frequently
      
      - alert: DeploymentStuck
        expr: idp_deployments_in_progress > 0 for 30m
        annotations:
          summary: Deployment stuck for 30+ minutes
```

---

## Future Enhancements

### Phase 3: Advanced Features

1. **Multi-VPS Support**
   - Deploy across multiple servers
   - Geographic distribution
   - Automatic failover

2. **Template Marketplace**
   - Community-contributed templates
   - Rating and reviews
   - Template versioning

3. **Cost Management**
   - Resource usage tracking
   - Billing per instance
   - Budget alerts

4. **Advanced Monitoring**
   - APM integration (Datadog, New Relic)
   - Custom dashboards
   - Anomaly detection

5. **GitOps Integration**
   - Flux/ArgoCD support
   - Declarative config
   - Automated sync

---

## References

- [Quarkus Documentation](https://quarkus.io/guides/)
- [GitHub API v3](https://docs.github.com/en/rest)
- [Podman Documentation](https://docs.podman.io/)
- [Platform Engineering Guide](https://platformengineering.org/)

---

**Maintained by:** OpenSource Santiago Platform Team  
**Last Updated:** 2026-08-01  
**Next Review:** 2026-09-01
