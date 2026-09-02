import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { listImages, deleteImage, uploadImage } from './images.js';

describe('images api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe('listImages', () => {
    it('requests the given page and size and returns the parsed page', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue({
          ok: true,
          json: async () => ({ content: [{ id: '1' }], totalPages: 3 }),
        }),
      );

      const result = await listImages(1, 10);

      expect(fetch).toHaveBeenCalledWith(expect.stringContaining('/picvault/images?page=1&size=10'));
      expect(result.totalPages).toBe(3);
    });

    it('throws the server error message on a non-OK response', async () => {
      vi.stubGlobal(
        'fetch',
        vi.fn().mockResolvedValue({
          ok: false,
          status: 500,
          json: async () => ({ error: 'Something went wrong.' }),
        }),
      );

      await expect(listImages()).rejects.toThrow('Something went wrong.');
    });
  });

  describe('deleteImage', () => {
    it('sends a DELETE request to the correct id', async () => {
      vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true }));

      await deleteImage('abc-123');

      expect(fetch).toHaveBeenCalledWith(
        expect.stringContaining('/picvault/images/abc-123'),
        { method: 'DELETE' },
      );
    });
  });

  describe('uploadImage', () => {
    class FakeXHR {
      constructor() {
        this.upload = {};
      }
      open(method, url) {
        this.method = method;
        this.url = url;
      }
      send(body) {
        this.sentBody = body;
      }
    }

    let lastXHR;

    beforeEach(() => {
      vi.stubGlobal(
        'XMLHttpRequest',
        vi.fn(function() {
          lastXHR = new FakeXHR();
          return lastXHR;
        }),
      );
    });

    it('resolves with the parsed response and reports progress along the way', async () => {
      const onProgress = vi.fn();
      const file = new File(['content'], 'cat.jpg', { type: 'image/jpeg' });

      const promise = uploadImage(file, onProgress);

      // Simulate the browser firing upload progress, then load.
      lastXHR.upload.onprogress({ lengthComputable: true, loaded: 50, total: 100 });
      lastXHR.status = 201;
      lastXHR.responseText = JSON.stringify([{ id: '1', originalFilename: 'cat.jpg' }]);
      lastXHR.onload();

      const result = await promise;

      expect(onProgress).toHaveBeenCalledWith(50);
      expect(result[0].originalFilename).toBe('cat.jpg');
      expect(lastXHR.sentBody).toBeInstanceOf(FormData);
    });

    it('rejects with the server error message on a non-2xx response', async () => {
      const file = new File(['content'], 'cat.jpg', { type: 'image/jpeg' });
      const promise = uploadImage(file, () => {});

      lastXHR.status = 400;
      lastXHR.responseText = JSON.stringify({
        error: 'Unsupported file type; only image files are allowed.',
      });
      lastXHR.onload();

      await expect(promise).rejects.toThrow(
        'Unsupported file type; only image files are allowed.',
      );
    });

    it('rejects on a network error', async () => {
      const file = new File(['content'], 'cat.jpg', { type: 'image/jpeg' });
      const promise = uploadImage(file, () => {});

      lastXHR.onerror();

      await expect(promise).rejects.toThrow('Upload failed');
    });
  });
});
