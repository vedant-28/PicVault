import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import App from './App.jsx';
import { listImages, deleteImage, uploadImage } from './picvault/images.js';

vi.mock('./picvault/images.js', () => ({
  listImages: vi.fn(),
  deleteImage: vi.fn(),
  uploadImage: vi.fn(),
}));

describe('App', () => {
  beforeEach(() => {
    listImages.mockReset();
    deleteImage.mockReset();
    uploadImage.mockReset();
    listImages.mockResolvedValue({
      content: [{ id: '1', filename: 'cat.jpg', url: '/picvault/images/cat.jpg', sizeBytes: 1000 }],
      totalPages: 1,
    });
  });

  it('loads and displays images on mount', async () => {
    render(<App />);

    await waitFor(() => expect(screen.getByAltText('cat.jpg')).toBeInTheDocument());
    expect(screen.getByText('1 images')).toBeInTheDocument();
  });

  it('prepends a newly uploaded image without refetching the list', async () => {
    uploadImage.mockResolvedValue([
      { id: '2', originalFilename: 'dog.jpg', storageKey: 'key-dog', sizeBytes: 2000 },
    ]);
    render(<App />);
    await waitFor(() => expect(screen.getByAltText('cat.jpg')).toBeInTheDocument());

    const file = new File(['content'], 'dog.jpg', { type: 'image/jpeg' });
    const input = document.querySelector('input[type="file"]');
    await userEvent.upload(input, file);

    await waitFor(() => expect(screen.getByAltText('dog.jpg')).toBeInTheDocument());
    // The new image comes from the upload response, not a second list fetch.
    expect(listImages).toHaveBeenCalledTimes(1);
  });

  it('removes an image on successful delete', async () => {
    deleteImage.mockResolvedValue();
    render(<App />);
    await waitFor(() => expect(screen.getByAltText('cat.jpg')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Delete cat.jpg' }));

    await waitFor(() => expect(screen.queryByAltText('cat.jpg')).not.toBeInTheDocument());
    expect(deleteImage).toHaveBeenCalledWith('1');
  });

  it('rolls back and alerts when delete fails', async () => {
    deleteImage.mockRejectedValue(new Error('Request failed (500).'));
    window.alert = vi.fn();
    render(<App />);
    await waitFor(() => expect(screen.getByAltText('cat.jpg')).toBeInTheDocument());

    await userEvent.click(screen.getByRole('button', { name: 'Delete cat.jpg' }));

    // Optimistically removed, then restored once the rejection settles.
    await waitFor(() => expect(screen.getByAltText('cat.jpg')).toBeInTheDocument());
    expect(window.alert).toHaveBeenCalledWith(expect.stringContaining("Couldn't delete cat.jpg"));
  });
});
