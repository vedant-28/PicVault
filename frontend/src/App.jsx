import { useCallback, useEffect, useState } from 'react';
import UploadZone from './components/UploadZone.jsx';
import Gallery from './components/Gallery.jsx';
import { listImages, deleteImage } from './picvault/images.js';

export default function App() {
  const [images, setImages] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [isLoading, setIsLoading] = useState(true);

  const loadPage = useCallback(async (pageNumber) => {
    const data = await listImages(pageNumber);
    setImages((prev) => (pageNumber === 0 ? data.content : [...prev, ...data.content]));
    setTotalPages(data.totalPages);
    setPage(pageNumber);
  }, []);

  useEffect(() => {
    loadPage(0).finally(() => setIsLoading(false));
  }, [loadPage]);

  // POST /api/images returns ImageMetadata (id, originalFilename, storageKey, ...);
  // GET /api/images returns ImageDto (id, filename, url, sizeBytes) — different
  // shapes for the same underlying image. Normalize here so the gallery only
  // ever deals with one shape.
  const handleUploaded = useCallback((newImage) => {
    setImages((prev) => [
      {
        id: newImage.id,
        filename: newImage.originalFilename,
        url: `/picvault/images/${newImage.storageKey}`,
        sizeBytes: newImage.sizeBytes,
      },
      ...prev,
    ]);
  }, []);

  const handleDelete = useCallback(async (image) => {
    setImages((prev) => prev.filter((entry) => entry.id !== image.id));
    try {
      await deleteImage(image.id);
    } catch (error) {
      // Rollback: put it back and surface the failure. A toast component
      // would be nicer than alert() — fine placeholder for now.
      setImages((prev) => [image, ...prev]);
      window.alert(`Couldn't delete ${image.filename}: ${error.message}`);
    }
  }, []);

  return (
    <div className="app-shell">
      <header className="app-header">
        <div>
          <h1 className="app-title">PicVault</h1>
          <p className="app-subtitle">Up to 20 images per upload, 50MB each</p>
        </div>
        <span className="app-count num">{images.length} images</span>
      </header>

      <UploadZone onUploaded={handleUploaded} />

      <Gallery
        images={images}
        isLoading={isLoading}
        hasMore={page + 1 < totalPages}
        onLoadMore={() => loadPage(page + 1)}
        onDelete={handleDelete}
      />
    </div>
  );
}
