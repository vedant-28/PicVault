const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export default function ThumbnailGrid({ images, onSelect, onDelete }) {
  return (
    <div className="thumbnail-grid">
      {images.map((image) => {
        const imgSrc = image.url && image.url.startsWith('http') ? image.url : `${API_BASE}${image.url}`

        return (
        <div key={image.id} className="thumbnail">
          <img
            src={imgSrc}
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
      )})}
    </div>
  );
}
