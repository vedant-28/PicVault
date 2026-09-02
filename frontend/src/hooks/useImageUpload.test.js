import { describe, it, expect, vi, beforeEach } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { useImageUpload } from './useImageUpload.js';
import { uploadImage } from '../picvault/images.js';

vi.mock('../picvault/images.js', () => ({
  uploadImage: vi.fn(),
}));

function makeFile(name, type, size) {
  const file = new File(['x'], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

describe('useImageUpload', () => {
  beforeEach(() => {
    uploadImage.mockReset();
  });

  it('rejects a non-image file without calling the API', () => {
    const { result } = renderHook(() => useImageUpload());

    act(() => {
      result.current.enqueueFiles([makeFile('doc.pdf', 'application/pdf', 1000)]);
    });

    expect(result.current.queue).toHaveLength(1);
    expect(result.current.queue[0].status).toBe('error');
    expect(result.current.queue[0].errorMessage).toBe('Not an image file');
    expect(uploadImage).not.toHaveBeenCalled();
  });

  it('rejects a file over 50MB without calling the API', () => {
    const { result } = renderHook(() => useImageUpload());

    act(() => {
      result.current.enqueueFiles([makeFile('huge.jpg', 'image/jpeg', 51 * 1024 * 1024)]);
    });

    expect(result.current.queue[0].errorMessage).toBe('Over the 50MB limit');
    expect(uploadImage).not.toHaveBeenCalled();
  });

  it('rejects an empty file without calling the API', () => {
    const { result } = renderHook(() => useImageUpload());

    act(() => {
      result.current.enqueueFiles([makeFile('empty.jpg', 'image/jpeg', 0)]);
    });

    expect(result.current.queue[0].errorMessage).toBe('Empty file');
    expect(uploadImage).not.toHaveBeenCalled();
  });

  it('rejects a batch of more than 20 files as a single error entry, not per file', () => {
    const { result } = renderHook(() => useImageUpload());
    const files = Array.from({ length: 21 }, (_, i) => makeFile(`img${i}.jpg`, 'image/jpeg', 1000));

    act(() => {
      result.current.enqueueFiles(files);
    });

    expect(result.current.queue).toHaveLength(1);
    expect(result.current.queue[0].errorMessage).toContain('up to 20 images');
    expect(uploadImage).not.toHaveBeenCalled();
  });

  it('uploads a valid file, reports progress, and reaches done', async () => {
    uploadImage.mockImplementation((file, onProgress) => {
      onProgress(50);
      return Promise.resolve([
        { id: '1', originalFilename: file.name, storageKey: 'key-1', sizeBytes: file.size },
      ]);
    });
    const onUploaded = vi.fn();
    const { result } = renderHook(() => useImageUpload(onUploaded));

    act(() => {
      result.current.enqueueFiles([makeFile('cat.jpg', 'image/jpeg', 1000)]);
    });

    expect(result.current.queue[0].progress).toBe(50);

    await waitFor(() => expect(result.current.queue[0]?.status).toBe('done'));
    expect(onUploaded).toHaveBeenCalledWith(expect.objectContaining({ storageKey: 'key-1' }));
  });

  it('flips a failed upload to the error state with the server message', async () => {
    uploadImage.mockRejectedValue(
      new Error('Unsupported file type; only image files are allowed.'),
    );
    const { result } = renderHook(() => useImageUpload());

    act(() => {
      result.current.enqueueFiles([makeFile('cat.jpg', 'image/jpeg', 1000)]);
    });

    await waitFor(() => expect(result.current.queue[0].status).toBe('error'));
    expect(result.current.queue[0].errorMessage).toBe(
      'Unsupported file type; only image files are allowed.',
    );
  });

  it('dismisses a rejected entry from the queue', () => {
    const { result } = renderHook(() => useImageUpload());
    act(() => {
      result.current.enqueueFiles([makeFile('doc.pdf', 'application/pdf', 1000)]);
    });
    const id = result.current.queue[0].id;

    act(() => {
      result.current.dismissEntry(id);
    });

    expect(result.current.queue).toHaveLength(0);
  });
});
