# mdwiki-api

Backend mdwiki: Spring Boot + Kotlin, PostgreSQL (pgvector), REST API, SSE,
MCP-инструменты, RAG-поиск.

English version: `README.md`

Текущая версия: **v0.1.9** (см. git tag; runtime — `GET /api/version`).

## Быстрый старт (локально)

```sh
# JWT_SECRET обязателен (без него приложение не стартует)
export JWT_SECRET='local-dev-secret-change-me'

./gradlew bootRun          # http://localhost:8080
./gradlew test             # unit/integration tests
```

Фронтенд в dev-режиме проксирует `/api` на `:8080` (см.
[mdwiki-frontend](../mdwiki-frontend)).

Локальный Postgres: `docker-compose up -d` (порт `54328`, БД/user/password
`mdwiki`).

## Версия API

Публичный endpoint (без auth):

```http
GET /api/version
```

```json
{
  "name": "mdwiki-api",
  "version": "0.1.9",
  "versionTag": "v0.1.9",
  "gitSha": "…"
}
```

- `version` — из `build.gradle.kts` (Spring Boot `build-info`)
- `versionTag` / `gitSha` — из `git describe` / `rev-parse` на сборке;
  в Docker передаются как `APP_VERSION_TAG` / `APP_GIT_SHA` (`.git` в
  образе нет)

## Вложения и `/api/uploads`

| Метод | Путь | Назначение |
|-------|------|------------|
| `GET` | `/api/uploads/{storedName}` | Раздача файла (public, для картинок в markdown) |
| `POST` | `/api/attachments` | Загрузка вложения (EDITOR/ADMIN, запись в БД) |
| ~~`POST`~~ | ~~`/api/uploads`~~ | **Удалён** — не использовать |

Загрузка только через `AttachmentService` (HTTP `POST /api/attachments`
или MCP-инструменты ниже). URL в ответе по-прежнему вида
`/api/uploads/{uuid}.png` — это ссылка на **GET**-раздачу, не на POST.

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
# Локальный OrbStack / k8s (JWT, embedding LM Studio и т.д. в values-local.yaml)
VALUES_FILE=./values-local.yaml ./scripts/deploy-k8s-with-build.sh

# С кастомными values (JWT, postgres, embedding и т.д.)
VALUES_FILE=deploy/helm/mdwiki-api/values-prod.yaml ./scripts/deploy-k8s-with-build.sh

# Только helm, если образ уже собран и запушен
IMAGE_REPOSITORY=ghcr.io/your-org/mdwiki-api \
IMAGE_TAG=v0.1.0 \
./scripts/deploy-k8s.sh
```

Образ тегируется одним тегом — **`git describe --tags --always`**
(например `mdwiki-api:v0.1.0` или `mdwiki-api:v0.1.0-3-g8d4bfd5`).
В Docker-сборку передаются `APP_GIT_SHA` и `APP_VERSION_TAG` (для
`/api/version`, не как второй docker-тег).

### Полезные переменные окружения

| Переменная | По умолчанию | Описание |
|------------|--------------|----------|
| `RELEASE_NAME` | `mdwiki-api` | Имя Helm-релиза |
| `NAMESPACE` | `mdwiki` | Namespace в кластере |
| `VALUES_FILE` | — | Дополнительный `-f` values-файл |
| `IMAGE_REPOSITORY` | `mdwiki-api` | Репозиторий образа |
| `IMAGE_TAG` | `git describe --tags --always` (+ `-dirty`) | Тег образа |
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
fingerprint зависимостей Gradle (`BUILD_BASE_IMAGE=auto`; версия проекта не входит).

### Снятие с кластера

```sh
./scripts/undeploy-k8s.sh

# Вместе с PVC (данные Postgres и wiki-content)
PURGE_DATA=true ./scripts/undeploy-k8s.sh
```

Подробнее по values chart — `deploy/helm/mdwiki-api/README.ru.md`.

После API обычно деплоят фронтенд:
[mdwiki-frontend/scripts/deploy-k8s-with-build.sh](../mdwiki-frontend/scripts/deploy-k8s-with-build.sh).

---

## MCP: импорт markdown-страниц

Для **больших** файлов предпочтителен обход MCP-контента:

1. `wiki_auth_token` → короткий Bearer JWT (`scope=pages:import` по умолчанию, ~10 мин)
2. `POST /api/pages/import` с `Authorization: Bearer …` и multipart `files`

### `wiki_auth_token`

Параметр `scope` опционален. Требует EDITOR/ADMIN (через MCP API key владельца).

| `scope` | REST |
|---|---|
| `pages:import` (default) | только `POST /api/pages/import` |
| `attachments:upload` | только `POST /api/attachments` |
| `bundles:export` | `POST /api/bundles/preview` и `POST /api/bundles/export` |
| `bundles:import` | только `POST /api/bundles/import` |

Ответ: `token`, `tokenType`, `scope`, `expiresAt`, `expiresInSeconds`, `usage`.

Scopes живут в реестре `JwtScopes` (scope → method+path). Чужой путь → 403.

Для больших картинок: `wiki_auth_token(scope=attachments:upload)` → `POST /api/attachments` (multipart `file`, опционально `pageId`). Мелкие файлы по-прежнему через `wiki_upload` (base64).

### `wiki_import`

Создаёт wiki-страницу из markdown-файла (не attachment). Удобен для небольших текстов.
Slug — из имени файла; title — frontmatter `title` → H1 → имя файла.
При конфликте slug по умолчанию пропускает; `overwrite=true` перезаписывает.

Параметры:
- `filename` — например `my-note.md`
- `contentMd` — полный markdown
- `folderId` — опционально, UUID папки
- `overwrite` — опционально, default `false`

Пример ответа: `status` = `created` | `updated` | `skipped` | `error`.

HTTP-аналог: `POST /api/pages/import` (multipart `files`, `folderId`, `overwrite`).

---

## MCP: загрузка attachments

Инструменты пишут файл через `AttachmentService` (БД + `uploads/` на диске)
и возвращают URL вида `/api/uploads/{storedName}` для **GET**-раздачи.
HTTP `POST /api/uploads` не используется и удалён.

Крупные файлы: `wiki_auth_token(scope=attachments:upload)` → REST
`POST /api/attachments` (см. выше). Мелкие — `wiki_upload` (base64).

### `wiki_upload` (base64)

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

### `wiki_attachment_upload` (путь на хосте API)

Загружает файл с диска сервера. Путь должен быть внутри
`mdwiki.attachments.allowed-import-dirs`
(`MDWIKI_ATTACHMENTS_ALLOWED_IMPORT_DIRS`). По умолчанию список пуст —
импорт с пути запрещён (осознанно, security).

Параметры: `filePath`, опционально `originalName`, `contentType`, `pageId`.

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
