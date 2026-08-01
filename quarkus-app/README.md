# Homedir IDP - Quarkus Application

Internal Developer Platform for Homedir - Self-service portal for deploying community instances.

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+

### Run in Dev Mode

```bash
./mvnw quarkus:dev
```

Open http://localhost:8090

### Build

```bash
./mvnw package
```

### Build Native Image

```bash
./mvnw package -Pnative
```

## Project Structure

```
src/main/
├── java/io/homedir/idp/
│   ├── IndexResource.java          # Home page
│   ├── api/                        # REST API
│   ├── service/                    # Business logic
│   └── model/                      # Domain models
└── resources/
    ├── application.properties      # Configuration
    ├── templates/                  # Qute templates
    │   ├── layout/main.html        # Main layout
    │   ├── index.html              # Home page
    │   └── templates/              # Template catalog pages
    └── META-INF/resources/
        ├── css/idp.css             # Dark theme styles
        └── js/                     # JavaScript
```

## Development

### Hot Reload

Quarkus dev mode automatically reloads on changes:
- Java code changes trigger recompilation
- Template changes are instant
- CSS changes are instant

### Testing

```bash
./mvnw test
```

## Configuration

Edit `src/main/resources/application.properties`:

```properties
# HTTP
quarkus.http.port=8090

# GitHub Integration
github.token=${GITHUB_TOKEN}
github.org=${GITHUB_ORG:os-santiago}

# VPS Deployment
vps.host=${VPS_HOST}
vps.user=${VPS_USER:root}
```

## API Endpoints

- `GET /` - Home page
- `GET /templates` - Template catalog
- `GET /projects` - Project dashboard
- `POST /api/projects` - Create project
- `GET /api/templates` - List templates

## Dark Theme

The IDP uses a custom dark theme with:
- Grayscale & black color palette
- Green accent (#10b981) for success/action
- Golden highlights (#fbbf24) for golden paths
- Smooth transitions and hover effects
