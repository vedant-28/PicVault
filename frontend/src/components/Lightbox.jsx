import { useEffect } from 'react';

const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export default function Lightbox({ image, onClose }) {
  const imgSrc = image.url && image.url.startsWith('http') ? image.url : `${API_BASE}${image.url}`

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === 'Escape') onClose();
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [onClose]);

  return (
    <div className="lightbox-backdrop" onClick={onClose}>
      <div className="lightbox-content" onClick={(event) => event.stopPropagation()}>
        <button type="button" className="lightbox-close" aria-label="Close" onClick={onClose}>
          Close
        </button>
        <img src={imgSrc} alt={image.filename} />
        <p className="lightbox-caption">{image.filename}</p>
      </div>
    </div>
  );
}
