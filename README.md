# Homedir IDP

> **Internal Developer Platform for Homedir Communities**

![License: Apache-2.0](https://img.shields.io/badge/License-Apache_2.0-blue.svg?style=for-the-badge&logo=apache&logoColor=white)
[![Project Status: Active](https://img.shields.io/badge/status-active-brightgreen?style=for-the-badge&logo=github&logoColor=white)](https://github.com/os-santiago/homedir-idp)
[![Production](https://img.shields.io/badge/prod-homedir--idp.opensourcesantiago.io-10b981?style=for-the-badge&logo=quarkus&logoColor=white)](https://homedir-idp.opensourcesantiago.io)

Self-service portal for creating, deploying, and managing Homedir community instances. Empowers communities to spin up their own Homedir deployments with golden path templates and automated provisioning.

## 🎯 Vision

Enable any tech community to deploy their own Homedir instance in **under 5 minutes** with zero infrastructure knowledge required.

## 🚀 Features (Planned)

### Phase 1: MVP (Weeks 1-2)
- [x] Repository structure
- [x] Quarkus project setup
- [x] Dark theme UI mockup
- [x] CI/CD pipeline (PR validation + auto-deploy)
- [ ] Template catalog (JSON-based)
- [ ] Self-service wizard UI (Qute templates)
- [ ] Scaffolder service (Java/Quarkus)
- [ ] GitHub integration (repo creation)
- [ ] VPS deployment integration

### Phase 2: Enhancement (Weeks 3-6)
- [ ] Project dashboard
- [ ] Version upgrade automation
- [ ] Health monitoring
- [ ] Resource usage tracking
- [ ] One-click rollback

### Phase 3: Advanced (Future)
- [ ] Multi-VPS support
- [ ] Custom template creation
- [ ] API access for programmatic provisioning
- [ ] Analytics dashboard

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────┐
│                  Homedir IDP                        │
│  (Quarkus Application - extends Homedir core)       │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌─────────────┐  ┌──────────────┐  ┌───────────┐ │
│  │  Template   │  │  Self-Service │  │  Project  │ │
│  │  Catalog    │  │    Wizard     │  │ Dashboard │ │
│  │             │  │               │  │           │ │
│  │ (Qute UI)   │  │  (Qute UI)    │  │(Qute UI)  │ │
│  └─────────────┘  └──────────────┘  └───────────┘ │
│                                                     │
│  ┌─────────────────────────────────────────────┐   │
│  │         REST API (Quarkus)                  │   │
│  │  /api/templates                             │   │
│  │  /api/projects                              │   │
│  │  /api/deployments                           │   │
│  └─────────────────────────────────────────────┘   │
│                                                     │
│  ┌──────────────┐  ┌─────────────┐  ┌──────────┐  │
│  │  Scaffolder  │  │   GitHub    │  │   VPS    │  │
│  │   Service    │→│ Integration │→│ Deployer │  │
│  │              │  │             │  │          │  │
│  │ (Clone repo, │  │(Create repo,│  │(Podman + │  │
│  │  customize   │  │ push code)  │  │  nginx)  │  │
│  │  templates)  │  │             │  │          │  │
│  └──────────────┘  └─────────────┘  └──────────┘  │
│                                                     │
└─────────────────────────────────────────────────────┘
              ↓                    ↓
    ┌─────────────────┐  ┌──────────────────────┐
    │  GitHub Repos   │  │   VPS Instances      │
    │                 │  │                      │
    │ community-a/    │  │ community-a.homedir  │
    │   homedir       │  │ community-b.homedir  │
    │ community-b/    │  │ community-c.homedir  │
    │   homedir       │  │                      │
    └─────────────────┘  └──────────────────────┘
```

## 🛠️ Technology Stack

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| **Backend** | Java 21 + Quarkus | Reuses Homedir stack, native compilation |
| **Templates** | Qute (Server-side) | Consistent with Homedir UI |
| **Data Storage** | JSON files → PostgreSQL | Start simple, scale when needed |
| **VCS Integration** | GitHub API | Where templates live |
| **Deployment** | Podman + Bash scripts | Reuses Homedir deployment tooling |
| **CI/CD** | GitHub Actions | Consistent with Homedir |

## 📁 Repository Structure

```
homedir-idp/
├── quarkus-app/                    # Main Quarkus application
│   ├── src/main/java/
│   │   └── io/homedir/idp/
│   │       ├── api/                # REST endpoints
│   │       │   ├── TemplateResource.java
│   │       │   ├── ProjectResource.java
│   │       │   └── DeploymentResource.java
│   │       ├── service/            # Business logic
│   │       │   ├── ScaffolderService.java
│   │       │   ├── GitHubService.java
│   │       │   └── DeploymentService.java
│   │       └── model/              # Domain models
│   │           ├── Template.java
│   │           ├── Project.java
│   │           └── DeploymentStatus.java
│   └── src/main/resources/
│       ├── templates/              # Qute templates (UI)
│       │   ├── catalog.html
│       │   ├── wizard.html
│       │   └── dashboard.html
│       └── application.properties
├── templates/                      # Golden path templates
│   ├── homedir-community/
│   │   ├── template.json           # Template metadata
│   │   └── scaffold/               # Files to copy/customize
│   ├── homedir-minimal/
│   └── homedir-enterprise/
├── scripts/                        # Deployment & automation
│   ├── deploy-instance.sh
│   ├── setup-vps.sh
│   └── health-check.sh
├── docs/
│   ├── architecture.md
│   ├── templates-guide.md
│   └── deployment-guide.md
├── .github/workflows/
│   ├── ci.yml
│   └── deploy.yml
├── README.md
├── LICENSE
└── pom.xml
```

## 🚦 Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- GitHub Personal Access Token (for repo creation)
- VPS access (for deployment testing)

### Local Development

```bash
# Clone repository
git clone https://github.com/os-santiago/homedir-idp.git
cd homedir-idp

# Run in dev mode
cd quarkus-app
./mvnw quarkus:dev

# Access at http://localhost:8080
```

### Configuration

Create `.env` file:

```bash
# GitHub Integration
GITHUB_TOKEN=ghp_xxxxxxxxxxxxx
GITHUB_ORG=os-santiago

# VPS Configuration
VPS_HOST=72.60.141.165
VPS_USER=root
VPS_SSH_KEY=/path/to/key

# Template Settings
TEMPLATE_REPO_PREFIX=homedir-
```

## 📋 Use Case Example

### Creating a New Community Instance

**User Journey:**

1. **Discover Templates** → User visits `/templates`
   - Sees "Homedir Community Edition", "Homedir Minimal"
   - Clicks "Create from Template"

2. **Wizard Configuration** → Fills form at `/templates/homedir-community/create`
   ```
   Community Name: DevOpsDays Chile
   Subdomain: devopsdays-chile
   Admin Email: admin@devopsdays.cl
   GitHub Repo: devopsdays-chile/homedir
   Features: [x] Events [x] CFP [ ] Projects
   ```

3. **Preview & Confirm** → Reviews generated config
   - Domain: `https://devopsdays-chile.homedir.io`
   - GitHub: `github.com/devopsdays-chile/homedir`

4. **Scaffolding** → Backend executes:
   ```java
   ScaffolderService.scaffold(
     template: "homedir-community",
     params: { name: "DevOpsDays Chile", ... }
   )
   ```
   - Clones template repo
   - Replaces placeholders in config files
   - Creates GitHub repo via API
   - Pushes customized code

5. **Deployment** → VPS Deployer executes:
   ```bash
   ./scripts/deploy-instance.sh \
     --repo devopsdays-chile/homedir \
     --subdomain devopsdays-chile \
     --admin admin@devopsdays.cl
   ```
   - Pulls code to VPS
   - Builds Quarkus native image
   - Configures Podman container
   - Sets up nginx subdomain
   - Runs health check

6. **Ready** → 🎉 User receives URL
   ```
   ✅ Your Homedir instance is ready!
   
   URL: https://devopsdays-chile.homedir.io
   Admin: admin@devopsdays.cl
   GitHub: github.com/devopsdays-chile/homedir
   
   Next steps:
   - Configure OAuth (Google/GitHub/Discord)
   - Upload event logo
   - Create first event
   ```

**Time:** ~5 minutes from start to deployed instance

## 🎨 Template Structure

### Template Metadata (`template.json`)

```json
{
  "id": "homedir-community",
  "name": "Homedir Community Edition",
  "description": "Full-featured community platform with events, CFP, and projects",
  "version": "1.0.0",
  "author": "OpenSource Santiago",
  "features": {
    "events": {
      "enabled": true,
      "description": "Event management and agenda"
    },
    "cfp": {
      "enabled": true,
      "description": "Call for Proposals workflow"
    },
    "projects": {
      "enabled": true,
      "configurable": true,
      "description": "Community projects showcase"
    },
    "trending": {
      "enabled": false,
      "description": "GitHub trending repositories"
    }
  },
  "parameters": [
    {
      "name": "community_name",
      "type": "string",
      "label": "Community Name",
      "placeholder": "DevOpsDays Santiago",
      "required": true
    },
    {
      "name": "subdomain",
      "type": "string",
      "label": "Subdomain",
      "placeholder": "devopsdays-santiago",
      "pattern": "^[a-z0-9-]+$",
      "required": true
    },
    {
      "name": "admin_email",
      "type": "email",
      "label": "Admin Email",
      "required": true
    }
  ],
  "scaffold": {
    "source": "https://github.com/os-santiago/homedir.git",
    "branch": "main",
    "files_to_customize": [
      "quarkus-app/src/main/resources/application.properties",
      "platform/.env.template",
      "README.md"
    ],
    "placeholders": {
      "{{COMMUNITY_NAME}}": "${community_name}",
      "{{SUBDOMAIN}}": "${subdomain}",
      "{{ADMIN_EMAIL}}": "${admin_email}"
    }
  },
  "deployment": {
    "type": "vps-podman",
    "requires": ["java-21", "podman", "nginx"],
    "resources": {
      "cpu": "2 cores",
      "memory": "4GB",
      "disk": "20GB"
    }
  }
}
```

## 🔗 Integration with Homedir

### Option A: Standalone Deployment
- IDP runs as separate Quarkus application
- Different port/subdomain (e.g., `idp.homedir.io`)
- Creates instances on same or different VPS

### Option B: Embedded Module (Future)
- IDP becomes module within Homedir itself
- Access at `https://homedir.io/idp`
- Tighter integration, shared authentication

**Phase 1 uses Option A** for faster iteration.

## 🛡️ Security Considerations

- **GitHub Token:** Store in secrets, never commit
- **SSH Keys:** Encrypted storage for VPS access
- **User Auth:** Integrate with Homedir OAuth (Google/GitHub)
- **RBAC:** Only authenticated users can create instances
- **Rate Limiting:** Prevent abuse of scaffolding endpoint
- **Input Validation:** Sanitize all user inputs (subdomain, repo name)

## 📊 Metrics & Monitoring

Track:
- Number of instances created
- Templates most used
- Deployment success rate
- Average time from request to live instance
- Resource usage per instance

## 🚀 CI/CD & Deployment

### Production Environment

**URL:** https://homedir-idp.opensourcesantiago.io  
**VPS:** 72.60.141.165  
**Container:** Podman on port 8090  
**Reverse Proxy:** Nginx with Let's Encrypt SSL

### Automated Deployment Pipeline

```
PR → Validation → Merge to master → Build → Container Image → Deploy to VPS → Health Check
```

**Workflows:**

1. **PR Validation** (`.github/workflows/pr-check.yml`)
   - Build & test Quarkus app
   - Code quality checks
   - Triggers: Pull requests to master

2. **Production Release** (`.github/workflows/release.yml`)
   - Build Maven package
   - Create container image (UBI8 + OpenJDK 21)
   - Push to GitHub Container Registry
   - Deploy to VPS via SSH
   - Health check verification
   - Create GitHub release
   - Triggers: Push to master branch

**Container Image:** `ghcr.io/os-santiago/homedir-idp:latest`

### VPS Setup

Run on VPS to prepare for deployments:

```bash
curl -fsSL https://raw.githubusercontent.com/os-santiago/homedir-idp/master/scripts/setup-vps.sh | bash
```

See [docs/deployment.md](docs/deployment.md) for detailed deployment guide.

## 🤝 Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development workflow.

## 📄 License

Apache License 2.0 - see [LICENSE](LICENSE)

## 🔗 Related Projects

- [Homedir](https://github.com/os-santiago/homedir) - Main platform
- [Homedir AI-SDLC](https://github.com/os-santiago/homedir-ai-sdlc) - Autonomous development lifecycle

## 📞 Support

- Discord: [Join #homedir-idp channel](https://discord.gg/3eawzc9ybc)
- Issues: [GitHub Issues](https://github.com/os-santiago/homedir-idp/issues)
- Discussions: [GitHub Discussions](https://github.com/os-santiago/homedir-idp/discussions)

---

**Built with ❤️ by OpenSource Santiago**

🚀 **Status:** Ready for deployment
