# mdwiki-api Helm chart

Русская версия: `README.ru.md`

This chart deploys:

- `mdwiki-api` (Deployment + Service)
- PVC for `wiki-content` (optional)
- PostgreSQL `pgvector` (StatefulSet + Service)
- Ingress (optional)

## Quick start

```bash
# Usually deployed via scripts/deploy-k8s-with-build.sh
# (image tag = git describe, e.g. v0.1.0-3-g8d4bfd5).
helm upgrade --install mdwiki-api ./deploy/helm/mdwiki-api \
  --namespace mdwiki \
  --create-namespace \
  --set image.repository=ghcr.io/your-org/mdwiki-api \
  --set image.tag=v0.1.0 \
  --set app.jwtSecret='replace-me'
```

Local cluster: `VALUES_FILE=./values-local.yaml ./scripts/deploy-k8s-with-build.sh`
from the repo root (`app.jwtSecret` and embeddings are set in values-local).

## Required production settings

- `image.repository` / `image.tag` (prefer `git describe --tags --always`)
- `app.jwtSecret` (required; the app no longer has a default)
- `postgres.password`
- when `EMBEDDING_PROVIDER=openai`: `app.openaiApiKey`
