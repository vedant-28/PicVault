# PicVault frontend

React 19 + Vite. Drag-and-drop upload with progress, thumbnail gallery, lightbox.

## Setup

```bash
cd frontend
npm install
cp .env.example .env   # defaults to http://localhost:8080, edit if your backend differs
npm run dev
```

Runs on `http://localhost:3000` — pinned in `vite.config.js` to match the
backend's `picvault.cors.allowed-origin` default. Change both together if you
move it.

## Required backend change

`ThumbnailGrid`'s delete button calls `DELETE /api/images/{id}`, which needs
a UUID. The current `ImageDto` returned by `GET /api/images` only has
`filename`, `url`, and `sizeBytes` — no `id` — so there's currently no way
for the gallery to know which UUID to delete. Add it:

```java
// ImageDto.java
public record ImageDto(UUID id, String filename, String url, long sizeBytes) {}
```

```java
// ImageService.listAllImages(), inside the .map(...)
new ImageDto(
    metadata.getId(),
    metadata.getOriginalFilename(),
    buildImageUrl(metadata.getStorageKey()),
    metadata.getSizeBytes()
)
```

Without this, delete will silently do nothing (or throw) since `image.id`
would be `undefined` on every row from the list endpoint.

## Structure

- `src/api/images.js` — all backend calls. Upload uses raw `XMLHttpRequest`
  (not `fetch`) specifically for `xhr.upload.onprogress` — `fetch` has no
  request-body upload progress event.
- `src/hooks/useImageUpload.js` — the upload queue: client-side validation
  (type/size/count, mirroring the backend's exact limits) before any network
  call, then one upload per valid file with live progress.
- `src/components/UploadZone.jsx` — drag-and-drop + click-to-browse.
- `src/components/UploadProgressList.jsx` — per-file uploading/done/error rows.
- `src/components/Gallery.jsx` / `ThumbnailGrid.jsx` / `Lightbox.jsx` —
  paginated grid, hover-to-delete, click-to-open full size.
- `src/App.jsx` — owns the image list; the one place that reconciles the two
  different JSON shapes the backend returns for the same image (`POST`
  returns the raw entity, `GET` returns `ImageDto`).

## Not in this scaffold yet

Tests (Vitest + React Testing Library), the production Dockerfile, and
`docker-compose.yml` wiring — later phases per the project plan.
