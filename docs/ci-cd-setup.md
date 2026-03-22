# CI/CD Setup Guide

## Prerequisites

- VPS with Docker and Docker Compose installed
- Domain name with DNS configured
- GitHub repository with Actions enabled

## Initial VPS Setup

1. Install Docker and Docker Compose:
   ```bash
   curl -fsSL https://get.docker.com | sh
   sudo usermod -aG docker $USER
   ```

2. Create directory structure:
   ```bash
   sudo mkdir -p /opt/ai-hiring/{docker,nginx,scripts,certs}
   sudo chown -R $USER:$USER /opt/ai-hiring
   ```

3. Copy scripts to VPS:
   ```bash
   scp scripts/*.sh user@vps:/opt/ai-hiring/scripts/
   chmod +x /opt/ai-hiring/scripts/*.sh
   ```

4. Copy docker-compose files:
   ```bash
   scp -r docker/* user@vps:/opt/ai-hiring/docker/
   ```

5. Create .env files for each environment:
   ```bash
   cp /opt/ai-hiring/docker/dev/.env.example /opt/ai-hiring/docker/dev/.env
   nano /opt/ai-hiring/docker/dev/.env
   ```

## GitHub Secrets

| Secret | Description |
|--------|-------------|
| `VPS_HOST` | VPS IP address |
| `VPS_USER` | SSH username |
| `VPS_SSH_KEY` | SSH private key for deployment |
| `DOMAIN` | Your domain (without subdomain) |
| `OPENAI_API_KEY` | OpenAI API key for AI matching service |
| `TEST_SECRET` | Secret for E2E test cleanup API |
| `TEST_USERNAME` | Test account username for E2E tests |
| `TEST_PASSWORD` | Test account password for E2E tests |

## Deployment Flow

1. **Dev**: Push to `feature/*` -> automatic deploy
2. **Staging**: Push to `master` -> automatic deploy
3. **Production**: Manual trigger via GitHub Actions

## Rollback

```bash
/opt/ai-hiring/scripts/rollback.sh prod <commit-sha>
```
