import { useRef, useState } from 'react';
import UploadProgressList from './UploadProgressList.jsx';
import { useImageUpload } from '../hooks/useImageUpload.js';

export default function UploadZone({ onUploaded }) {
  const { queue, enqueueFiles, dismissEntry } = useImageUpload(onUploaded);
  const [isDragActive, setIsDragActive] = useState(false);
  const inputRef = useRef(null);

  function handleDrop(event) {
    event.preventDefault();
    setIsDragActive(false);
    enqueueFiles(event.dataTransfer.files);
  }

  function handleBrowseChange(event) {
    enqueueFiles(event.target.files);
    // Reset so selecting the same file again still fires a change event.
    event.target.value = '';
  }

  return (
    <div>
      <div
        className="upload-zone"
        data-active={isDragActive}
        onDragOver={(event) => {
          event.preventDefault();
          setIsDragActive(true);
        }}
        onDragLeave={() => setIsDragActive(false)}
        onDrop={handleDrop}
        onClick={() => inputRef.current?.click()}
        role="button"
        tabIndex={0}
        onKeyDown={(event) => {
          if (event.key === 'Enter' || event.key === ' ') inputRef.current?.click();
        }}
      >
        <p className="upload-zone-title">Drag and drop images here, or browse</p>
        <p className="upload-zone-hint">JPG, PNG, GIF, or WEBP · up to 20 images, 50MB each</p>
        <input
          ref={inputRef}
          type="file"
          accept="image/jpeg,image/png,image/webp,image/gif"
          multiple
          className="visually-hidden"
          onChange={handleBrowseChange}
        />
      </div>

      <UploadProgressList queue={queue} onDismiss={dismissEntry} />
    </div>
  );
}
