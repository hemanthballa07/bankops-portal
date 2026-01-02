# Azure DevOps Pipeline Configuration

This directory contains the Azure DevOps pipeline configuration for the BankOps Portal.

## Pipeline Overview

The pipeline includes the following stages:

1. **Build and Test**
   - Backend: Compile, run unit tests, package JAR
   - Frontend: Install dependencies, lint, run tests, build for production
   - Publish artifacts

2. **Deploy to Dev** (auto-deploy after successful build)
   - Deploy backend to Azure App Service (Dev)
   - Deploy frontend to Azure App Service (Dev)

3. **Deploy to Production** (requires approval, only on main branch)
   - Deploy backend to Azure App Service (Prod)
   - Deploy frontend to Azure App Service (Prod)

## Setup Instructions

### 1. Create Azure Service Connection

1. Go to **Project Settings** > **Service connections**
2. Create a new **Azure Resource Manager** service connection
3. Name it `AzureResourceManager` (or update the pipeline YAML)
4. Configure authentication (Service principal recommended)

### 2. Configure Pipeline Variables

Go to **Pipelines** > **Library** and create a variable group or set pipeline variables:

**Backend Variables:**
- `AzureSqlConnectionString` - Dev Azure SQL connection string
- `AzureSqlUsername` - Database username
- `AzureSqlPassword` - Database password (mark as secret)
- `AzureSqlConnectionString-Prod` - Production connection string
- `AzureSqlUsername-Prod` - Production username
- `AzureSqlPassword-Prod` - Production password (mark as secret)

**Application Settings:**
- `backendServiceName` - Your backend Azure App Service name (default: `bankops-portal-backend`)
- `frontendServiceName` - Your frontend Azure App Service name (default: `bankops-portal-frontend`)
- `backendApiUrl` - Backend API URL for frontend build (e.g., `https://bankops-portal-backend-dev.azurewebsites.net/api`)

### 3. Create Azure Resources

**Backend App Service:**
- Runtime stack: Java 17
- Operating System: Linux
- Startup command: `java -jar bankops-portal-1.0.0.jar`
- Application settings: See pipeline deployment tasks

**Frontend App Service:**
- Runtime stack: Node.js 18 LTS
- Operating System: Linux
- Application settings: `API_URL` pointing to backend API

**Azure SQL Database:**
- Create database and configure firewall rules
- Store connection string in pipeline variables (as secrets)

### 4. Create Environments

In Azure DevOps:
1. Go to **Pipelines** > **Environments**
2. Create `dev` environment
3. Create `production` environment
4. Configure approval gates for `production` environment (optional but recommended)

### 5. Update Pipeline YAML

Update the following in `azure-pipelines.yml`:

- Line 28-29: Update `backendServiceName` and `frontendServiceName` if different
- Line 113: Update service connection name if not `AzureResourceManager`
- Line 114: Update dev app service names
- Line 141: Update service connection name
- Line 142: Update dev frontend app service name
- Line 167: Update service connection name  
- Line 168: Update production backend app service name
- Line 183: Update service connection name
- Line 184: Update production frontend app service name

## Pipeline Features

### Quality Gates
- Tests must pass before deployment
- Code coverage is published (JaCoCo for backend, Cobertura for frontend)
- Linting must pass for frontend

### Caching
- Maven dependencies cached for faster builds
- npm dependencies cached for faster builds

### Artifact Management
- Backend JAR published as artifact
- Frontend dist folder published as artifact
- Artifacts available for deployment stages

### Deployment Strategy
- **Dev**: Automatic deployment after successful build
- **Prod**: Requires approval and only deploys from `main` branch

## Running the Pipeline

The pipeline automatically triggers on:
- Push to `main` or `develop` branches
- Pull requests to `main` or `develop` branches

Manual runs can be triggered from the Azure DevOps Pipelines UI.

## Troubleshooting

### Backend Deployment Issues
- Verify Java 17 runtime stack is configured
- Check startup command matches JAR filename
- Verify application settings are set correctly
- Check Azure SQL firewall rules allow App Service IPs

### Frontend Deployment Issues
- Verify API_URL is set correctly in app settings
- Check build output path matches artifact structure
- Verify Node.js runtime stack is configured

### Test Failures
- Check test output in pipeline logs
- Verify database connections for integration tests
- Review code coverage reports

## Security Best Practices

1. **Store secrets as pipeline variables** (marked as secret)
2. **Use Azure Key Vault** for sensitive data (recommended for production)
3. **Enable approval gates** for production deployments
4. **Use service principals** instead of user accounts for service connections
5. **Restrict access** to pipeline variables and service connections





