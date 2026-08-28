import { useCallback, useState } from 'react';
import { uploadImage } from '../picvault/images.js';

// Mirrors ImageService's ALLOWED_IMAGE_TYPES and 50MB/20-file limits exactly —
// this is what lets us reject bad files before a single byte goes out.
const ALLOWED_TYPES = new Set(['image/jpeg', 'image/jpg', 'image/png', 'image/webp', 'image/gif']);
const MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024;
const MAX_FILES_PER_BATCH = 20;

let nextQueueId = 0;

export function useImageUpload(onUploaded) {
  const [queue, setQueue] = useState([]);

  const updateEntry = useCallback((id, patch) => {
    setQueue((prev) => prev.map((entry) => (entry.id === id ? { ...entry, ...patch } : entry)));
  }, []);

  const removeEntry = useCallback((id) => {
    setQueue((prev) => prev.filter((entry) => entry.id !== id));
  }, []);

  const enqueueFiles = useCallback(
    (fileList) => {
      const files = Array.from(fileList);
      if (files.length === 0) return;

      if (files.length > MAX_FILES_PER_BATCH) {
        setQueue((prev) => [
          ...prev,
          {
            id: nextQueueId++,
            name: `${files.length} files selected`,
            status: 'error',
            progress: 0,
            errorMessage: `You can upload up to ${MAX_FILES_PER_BATCH} images at a time.`,
          },
        ]);
        return;
      }

      const entries = files.map((file) => {
        const validationError = validate(file);
        return {
          id: nextQueueId++,
          file,
          name: file.name,
          status: validationError ? 'error' : 'uploading',
          progress: 0,
          errorMessage: validationError,
        };
      });

      setQueue((prev) => [...prev, ...entries]);

      entries
        .filter((entry) => entry.status === 'uploading')
        .forEach((entry) => startUpload(entry));
    },
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [],
  );

  function startUpload(entry) {
    uploadImage(entry.file, (progress) => updateEntry(entry.id, { progress }))
      .then((saved) => {
        updateEntry(entry.id, { status: 'done', progress: 100 });
        if (onUploaded && saved?.[0]) {
          onUploaded(saved[0]);
        }
        // Let the "done" checkmark register before the row disappears.
        setTimeout(() => removeEntry(entry.id), 1500);
      })
      .catch((error) => {
        updateEntry(entry.id, { status: 'error', errorMessage: error.message });
      });
  }

  const dismissEntry = useCallback((id) => removeEntry(id), [removeEntry]);

  return { queue, enqueueFiles, dismissEntry };
}

function validate(file) {
  if (!ALLOWED_TYPES.has(file.type)) {
    return 'Not an image file';
  }
  if (file.size > MAX_FILE_SIZE_BYTES) {
    return 'Over the 50MB limit';
  }
  if (file.size === 0) {
    return 'Empty file';
  }
  return null;
}
