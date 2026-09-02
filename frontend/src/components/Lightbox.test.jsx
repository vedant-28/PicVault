import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import Lightbox from './Lightbox.jsx';

const image = { id: '1', filename: 'cat.jpg', url: '/api/images/cat.jpg' };

describe('Lightbox', () => {
  it('renders the full-size image and caption', () => {
    render(<Lightbox image={image} onClose={() => {}} />);
    expect(screen.getByAltText('cat.jpg')).toBeInTheDocument();
    expect(screen.getByText('cat.jpg')).toBeInTheDocument();
  });

  it('closes on Escape', async () => {
    const onClose = vi.fn();
    render(<Lightbox image={image} onClose={onClose} />);

    await userEvent.keyboard('{Escape}');

    expect(onClose).toHaveBeenCalled();
  });

  it('closes on backdrop click but not on the image itself', async () => {
    const onClose = vi.fn();
    const { container } = render(<Lightbox image={image} onClose={onClose} />);

    await userEvent.click(screen.getByAltText('cat.jpg'));
    expect(onClose).not.toHaveBeenCalled();

    await userEvent.click(container.querySelector('.lightbox-backdrop'));
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it('closes via the Close button', async () => {
    const onClose = vi.fn();
    render(<Lightbox image={image} onClose={onClose} />);

    await userEvent.click(screen.getByRole('button', { name: 'Close' }));

    expect(onClose).toHaveBeenCalled();
  });
});
