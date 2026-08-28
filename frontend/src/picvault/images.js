const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';

export async function listImages(page = 0, size = 20) {
  const response = await fetch(`${API_BASE}/picvault/images?page=${page}&size=${size}`);
  if (!response.ok) {
    throw await toApiError(response);
  }
  return response.json();
}

export async function deleteImage(id) {
  const response = await fetch(`${API_BASE}/picvault/images/${id}`, { method: 'DELETE' });
  if (!response.ok) {
    throw await toApiError(response);
  }
}

/*
 * @returns a promise resolving to the array of saved ImageMetadata the
 * backend returns (one entry, since we upload one file per request).
 */
export function uploadImage(file, onProgress) {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', `${API_BASE}/picvault/images`);

    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && onProgress) {
        onProgress(Math.round((event.loaded / event.total) * 100));
      }
    };

    xhr.onload = () => {
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(xhr.responseText));
        } catch {
          reject(new Error('Upload succeeded but the response could not be read.'));
        }
      } else {
        reject(parseErrorResponse(xhr));
      }
    };

    xhr.onerror = () => reject(new Error('Upload failed — check your connection and try again.'));

    const formData = new FormData();
    formData.append('files', file);
    xhr.send(formData);
  });
}

function parseErrorResponse(xhr) {
  try {
    const body = JSON.parse(xhr.responseText);
    return new Error(body.error || `Upload failed (${xhr.status}).`);
  } catch {
    return new Error(`Upload failed (${xhr.status}).`);
  }
}

async function toApiError(response) {
  try {
    const body = await response.json();
    return new Error(body.error || `Request failed (${response.status}).`);
  } catch {
    return new Error(`Request failed (${response.status}).`);
  }
}
