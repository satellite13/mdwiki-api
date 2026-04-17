# mdwiki-api Helm chart

Этот chart разворачивает:

- `mdwiki-api` (Deployment + Service)
- PVC для `wiki-content` (опционально)
- PostgreSQL `pgvector` (StatefulSet + Service)
- Ingress (опционально)

## Быстрый старт

```bash
helm upgrade --install mdwiki-api ./deploy/helm/mdwiki-api \
  --namespace mdwiki \
  --create-namespace \
  --set image.repository=ghcr.io/your-org/mdwiki-api \
  --set image.tag=latest \
  --set app.jwtSecret='replace-me'
```

## Обязательные настройки для production

- `image.repository` / `image.tag`
- `app.jwtSecret` (не оставлять дефолт)
- `postgres.password`
- при `EMBEDDING_PROVIDER=openai`: `app.openaiApiKey`
