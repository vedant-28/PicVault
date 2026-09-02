import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Gallery from './Gallery.jsx';

const images = [{ id: '1', filename: 'cat.jpg', url: '/picvault/images/cat.jpg', sizeBytes: 1000 }];

describe('Gallery', () => {
  it('shows a skeleton grid while loading', () => {
    const { container } = render(
      <Gallery images={[]} isLoading onLoadMore={() => {}} onDelete={() => {}} hasMore={false} />,
    );
    expect(container.querySelectorAll('.thumbnail-skeleton')).toHaveLength(8);
  });

  it('shows the empty state when there are no images', () => {
    render(<Gallery images={[]} isLoading={false} onLoadMore={() => {}} onDelete={() => {}} hasMore={false} />);
    expect(screen.getByText('No images yet — drop some in above.')).toBeInTheDocument();
  });

  it('only shows Load more when hasMore is true', () => {
    const { rerender } = render(
      <Gallery images={images} isLoading={false} onLoadMore={() => {}} onDelete={() => {}} hasMore={false} />,
    );
    expect(screen.queryByRole('button', { name: 'Load more' })).not.toBeInTheDocument();

    rerender(
      <Gallery images={images} isLoading={false} onLoadMore={() => {}} onDelete={() => {}} hasMore />,
    );
    expect(screen.getByRole('button', { name: 'Load more' })).toBeInTheDocument();
  });

  it('calls onLoadMore when the button is clicked', async () => {
    const onLoadMore = vi.fn();
    render(<Gallery images={images} isLoading={false} onLoadMore={onLoadMore} onDelete={() => {}} hasMore />);

    await userEvent.click(screen.getByRole('button', { name: 'Load more' }));

    expect(onLoadMore).toHaveBeenCalled();
  });

  it('opens the lightbox when a thumbnail is clicked', async () => {
    render(<Gallery images={images} isLoading={false} onLoadMore={() => {}} onDelete={() => {}} hasMore={false} />);

    await userEvent.click(screen.getByAltText('cat.jpg'));

    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument();
  });
});
