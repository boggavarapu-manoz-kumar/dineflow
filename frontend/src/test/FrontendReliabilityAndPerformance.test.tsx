import React from 'react';
import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, act } from '@testing-library/react';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { MenuItemCard } from '@/pages/customer/components/MenuItemCard';
import { useDebouncedValue } from '@/hooks/useDebouncedValue';
import { CustomerMenuItem } from '@/lib/menuClient';

const ThrowingChild: React.FC<{ shouldThrow: boolean }> = ({ shouldThrow }) => {
  if (shouldThrow) {
    throw new Error('ChunkLoadError: secret internal stack');
  }
  return <div>Recovered Workspace</div>;
};

const DebounceHarness: React.FC<{ value: string }> = ({ value }) => {
  const debounced = useDebouncedValue(value, 100);
  return <div data-testid="debounced">{debounced}</div>;
};

describe('Frontend Reliability, Accessibility & Performance', () => {
  it('ErrorBoundary catches chunk/runtime failures without leaking stack traces and allows recovery', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => {});
    try {
      const { rerender } = render(
        <ErrorBoundary>
          <ThrowingChild shouldThrow={true} />
        </ErrorBoundary>
      );

      expect(screen.getByRole('alert')).toBeInTheDocument();
      expect(screen.getByText(/Something went wrong loading this view/i)).toBeInTheDocument();
      expect(screen.queryByText(/secret internal stack/i)).not.toBeInTheDocument();

      rerender(
        <ErrorBoundary>
          <ThrowingChild shouldThrow={false} />
        </ErrorBoundary>
      );
      fireEvent.click(screen.getByRole('button', { name: 'Try Again' }));
      expect(screen.getByText('Recovered Workspace')).toBeInTheDocument();
    } finally {
      spy.mockRestore();
    }
  });

  it('useDebouncedValue delays rapid updates until quiet period elapses', () => {
    vi.useFakeTimers();
    try {
      const { rerender } = render(<DebounceHarness value="p" />);
      expect(screen.getByTestId('debounced').textContent).toBe('p');

      rerender(<DebounceHarness value="pan" />);
      rerender(<DebounceHarness value="paneer" />);
      expect(screen.getByTestId('debounced').textContent).toBe('p');

      act(() => {
        vi.advanceTimersByTime(120);
      });
      expect(screen.getByTestId('debounced').textContent).toBe('paneer');
    } finally {
      vi.useRealTimers();
    }
  });

  it('MenuItemCard uses lazy async image loading and supports keyboard Enter/Space activation', () => {
    const onOpen = vi.fn();
    const item: CustomerMenuItem = {
      id: 'item-1',
      name: 'Butter Chicken',
      shortDescription: 'Rich tomato gravy',
      effectivePrice: 340,
      basePrice: 340,
      branchPriceOverridden: false,
      dietaryClassification: 'NON_VEGETARIAN',
      isAvailable: true,
      primaryImageUrl: '/api/v1/public/images/t1/i1/photo.webp',
      imageUrls: [],
      variants: [],
      optionGroups: [],
    };

    render(<MenuItemCard item={item} currencyCode="INR" onOpenDetails={onOpen} />);
    const img = screen.getByAltText('Butter Chicken');
    expect(img).toHaveAttribute('loading', 'lazy');
    expect(img).toHaveAttribute('decoding', 'async');
    expect(screen.getByText('INR 340.00')).toBeInTheDocument();

    const cardBtn = screen.getByRole('button', { name: /View details for Butter Chicken/i });
    fireEvent.keyDown(cardBtn, { key: 'Enter' });
    fireEvent.keyDown(cardBtn, { key: ' ' });
    expect(onOpen).toHaveBeenCalledTimes(2);
  });
});
