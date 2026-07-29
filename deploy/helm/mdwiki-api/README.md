# mdwiki-api Helm chart

Этот chart разворачивает:

- `mdwiki-api` (Deployment + Service)
- PVC для `wiki-content` (опционально)
- PostgreSQL `pgvector` (StatefulSet + Service)
- Ingress (опционально)

## Быстрый старт

```bash
# Обычно деплоят через scripts/deploy-k8s-with-build.sh
# (тег образа = git describe, например v0.1.0-3-g8d4bfd5).
helm upgrade --install mdwiki-api ./deploy/helm/mdwiki-api \
  --namespace mdwiki \
  --create-namespace \
  --set image.repository=ghcr.io/your-org/mdwiki-api \
  --set image.tag=v0.1.0 \
  --set app.jwtSecret='replace-me'
```

Локальный кластер: `VALUES_FILE=./values-local.yaml ./scripts/deploy-k8s-with-build.sh`
из корня репозитория (`app.jwtSecret` и embedding задаются в values-local).

## Обязательные настройки для production

- `image.repository` / `image.tag` (предпочтительно `git describe --tags --always`)
- `app.jwtSecret` (обязателен; дефолта в приложении больше нет)
- `postgres.password`
- при `EMBEDDING_PROVIDER=openai`: `app.openaiApiKey`
