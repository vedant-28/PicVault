import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import ThumbnailGrid from './ThumbnailGrid.jsx';

const images = [
  { id: '1', filename: 'cat.jpg', url: '/api/images/cat.jpg', sizeBytes: 1000 },
  { id: '2', filename: 'dog.jpg', url: '/api/images/dog.jpg', sizeBytes: 2000 },
];

describe('ThumbnailGrid', () => {
  it('renders one thumbnail per image', () => {
    render(<ThumbnailGrid images={images} onSelect={() => {}} onDelete={() => {}} />);
    expect(screen.getAllByRole('img')).toHaveLength(2);
  });

  it('calls onSelect with the clicked image', async () => {
    const onSelect = vi.fn();
    render(<ThumbnailGrid images={images} onSelect={onSelect} onDelete={() => {}} />);

    await userEvent.click(screen.getByAltText('cat.jpg'));

    expect(onSelect).toHaveBeenCalledWith(images[0]);
  });

  it('calls onDelete without also triggering onSelect', async () => {
    // Regression test for the delete button's event.stopPropagation() —
    // without it, clicking Delete would also open the lightbox.
    const onSelect = vi.fn();
    const onDelete = vi.fn();
    render(<ThumbnailGrid images={images} onSelect={onSelect} onDelete={onDelete} />);

    await userEvent.click(screen.getByRole('button', { name: 'Delete cat.jpg' }));

    expect(onDelete).toHaveBeenCalledWith(images[0]);
    expect(onSelect).not.toHaveBeenCalled();
  });
});
