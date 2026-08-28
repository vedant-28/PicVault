import { useState } from 'react';
import ThumbnailGrid from './ThumbnailGrid.jsx';
import Lightbox from './Lightbox.jsx';

export default function Gallery({ images, isLoading, hasMore, onLoadMore, onDelete }) {
  const [lightboxImage, setLightboxImage] = useState(null);

  if (isLoading) {
    return <SkeletonGrid />;
  }

  if (images.length === 0) {
    return <p className="gallery-empty">No images yet — drop some in above.</p>;
  }

  return (
    <>
      <ThumbnailGrid images={images} onSelect={setLightboxImage} onDelete={onDelete} />

      {hasMore && (
        <button type="button" className="load-more-button" onClick={onLoadMore}>
          Load more
        </button>
      )}

      {lightboxImage && <Lightbox image={lightboxImage} onClose={() => setLightboxImage(null)} />}
    </>
  );
}

function SkeletonGrid() {
  return (
    <div className="thumbnail-grid" aria-hidden="true">
      {Array.from({ length: 8 }).map((_, index) => (
        <div key={index} className="thumbnail-skeleton" />
      ))}
    </div>
  );
}
