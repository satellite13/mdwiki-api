# mdwiki-api

Backend mdwiki: Spring Boot + Kotlin, PostgreSQL (pgvector), REST API, SSE,
MCP-инструменты, RAG-поиск.

## Быстрый старт (локально)

```sh
./gradlew bootRun          # http://localhost:8080
./gradlew test             # unit/integration tests
```

Фронтенд в dev-режиме проксирует `/api` на `:8080` (см.
[mdwiki-frontend](../mdwiki-frontend)).

## Деплой в Kubernetes

Скрипты в `scripts/` оборачивают Helm chart
`deploy/helm/mdwiki-api`. Требуются `kubectl`, `helm` и доступ к кластеру.

| Скрипт | Назначение |
|--------|------------|
| `scripts/deploy-k8s.sh` | `helm upgrade --install` без сборки образа (образ уже в registry или загружен вручную) |
| `scripts/deploy-k8s-with-build.sh` | Сборка Docker-образа + деплой |
| `scripts/build-base-image.sh` | Только Gradle base image (`Dockerfile.build-base`) для ускорения сборки app-образа |
| `scripts/undeploy-k8s.sh` | `helm uninstall` релиза |

### Типичный деплой

```sh
# Сборка образа (git short SHA) и выкладка в namespace mdwiki
./scripts/deploy-k8s-with-build.sh

# С кастомными values (JWT, postgres, embedding и т.д.)
VALUES_FILE=deploy/helm/mdwiki-api/values-prod.yaml ./scripts/deploy-k8s-with-build.sh

# Только helm, если образ уже собран и запушен
IMAGE_REPOSITORY=ghcr.io/your-org/mdwiki-api \
IMAGE_TAG=abc1234 \
./scripts/deploy-k8s.sh
```

### Полезные переменные окружения

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `RELEASE_NAME` | `mdwiki-api` | Имя Helm-релиза |
| `NAMESPACE` | `mdwiki` | Namespace в кластере |
| `VALUES_FILE` | — | Дополнительный `-f` values-файл |
| `IMAGE_REPOSITORY` | `mdwiki-api` | Репозиторий образа |
| `IMAGE_TAG` | `git rev-parse --short HEAD` | Тег образа |
| `TIMEOUT` | `5m` | Таймаут `helm --wait` и rollout |

### Опции `deploy-k8s-with-build.sh`

```sh
./scripts/deploy-k8s-with-build.sh --help

# Чистая БД (удалить StatefulSet Postgres + PVC, перезапустить API / Liquibase)
./scripts/deploy-k8s-with-build.sh --recreate-db

# Провайдер эмбеддингов и ключ OpenAI на время деплоя
./scripts/deploy-k8s-with-build.sh \
  --embedding-provider openai \
  --openai-api-key "$OPENAI_API_KEY"
```

Сборка образа: `BUILD_METHOD=auto` (по умолчанию) — Docker, если есть
`Dockerfile`, иначе `./gradlew bootBuildImage`. Base image кэшируется по
fingerprint Gradle-файлов (`BUILD_BASE_IMAGE=auto`).

### Снятие с кластера

```sh
./scripts/undeploy-k8s.sh

# Вместе с PVC (данные Postgres и wiki-content)
PURGE_DATA=true ./scripts/undeploy-k8s.sh
```

Подробнее по values chart — `deploy/helm/mdwiki-api/README.md`.

После API обычно деплоят фронтенд:
[mdwiki-frontend/scripts/deploy-k8s-with-build.sh](../mdwiki-frontend/scripts/deploy-k8s-with-build.sh).

---

## MCP: загрузка attachments

Инструмент `wiki_upload` загружает файл в `uploads/` через MCP и возвращает URL вида `/api/uploads/{storedName}`.

Параметры:
- `fileBase64` — содержимое файла в base64 (также поддерживается `data:...;base64,...`)
- `filename` — исходное имя файла (например, `image.png`)
- `contentType` — опционально, MIME-тип (например, `image/png`)
- `pageId` — опционально, UUID страницы для привязки вложения

Пример вызова:

```json
{
  "server": "user-mdwiki",
  "toolName": "wiki_upload",
  "arguments": {
    "fileBase64": "iVBORw0KGgoAAAANSUhEUgAA...",
    "filename": "diagram.png",
    "contentType": "image/png",
    "pageId": "11111111-2222-3333-4444-555555555555"
  }
}
```

Пример ответа:

```json
{
  "id": "a7e759f1-6c91-4c3a-a8b9-6e6b0fd4bc4b",
  "filename": "diagram.png",
  "storedName": "fd6f1ea3-2b9f-4a3d-bc0e-d4c84bf335ec.png",
  "contentType": "image/png",
  "sizeBytes": 123456,
  "pageId": "11111111-2222-3333-4444-555555555555",
  "url": "/api/uploads/fd6f1ea3-2b9f-4a3d-bc0e-d4c84bf335ec.png",
  "createdAt": "2026-05-31T14:45:00Z"
}
```

## MCP: список attachments

Инструмент `wiki_attachment_list` возвращает список вложений. Можно фильтровать по странице.

Параметры:
- `page` — опционально, номер страницы (0-based), по умолчанию `0`
- `size` — опционально, размер страницы, по умолчанию `50`
- `pageId` — опционально, UUID страницы для фильтрации

Пример вызова:

```json
{
  "server": "user-mdwiki",
  "toolName": "wiki_attachment_list",
  "arguments": {
    "page": 0,
    "size": 20,
    "pageId": "11111111-2222-3333-4444-555555555555"
  }
}
```

Пример ответа:

```json
[
  {
    "id": "a7e759f1-6c91-4c3a-a8b9-6e6b0fd4bc4b",
    "originalName": "diagram.png",
    "storedName": "fd6f1ea3-2b9f-4a3d-bc0e-d4c84bf335ec.png",
    "contentType": "image/png",
    "sizeBytes": 123456,
    "uploadedBy": "alice",
    "pageId": "11111111-2222-3333-4444-555555555555",
    "url": "/api/uploads/fd6f1ea3-2b9f-4a3d-bc0e-d4c84bf335ec.png",
    "createdAt": "2026-05-31T14:45:00Z"
  }
]
```

## MCP: удаление attachments

Инструмент `wiki_attachment_delete` удаляет вложение по UUID (файл и запись в БД).

Параметры:
- `id` — UUID вложения

Пример вызова:

```json
{
  "server": "user-mdwiki",
  "toolName": "wiki_attachment_delete",
  "arguments": {
    "id": "a7e759f1-6c91-4c3a-a8b9-6e6b0fd4bc4b"
  }
}
```

Пример ответа:

```json
{
  "status": "deleted",
  "id": "a7e759f1-6c91-4c3a-a8b9-6e6b0fd4bc4b"
}
```
