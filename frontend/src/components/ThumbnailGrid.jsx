const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export default function ThumbnailGrid({ images, onSelect, onDelete }) {
  return (
    <div className="thumbnail-grid">
      {images.map((image) => (
        <div key={image.id} className="thumbnail">
          <img
            src={`${API_BASE}${image.url}`}
            alt={image.filename}
            loading="lazy"
            onClick={() => onSelect(image)}
          />
          <button
            type="button"
            className="thumbnail-delete"
            aria-label={`Delete ${image.filename}`}
            onClick={(event) => {
              event.stopPropagation();
              onDelete(image);
            }}
          >
            Delete
          </button>
        </div>
      ))}
    </div>
  );
}
