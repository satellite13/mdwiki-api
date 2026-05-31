# mdwiki-api

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
