import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { CustomerMenuPage } from '@/pages/customer/CustomerMenuPage';
import * as menuClient from '@/lib/menuClient';
import * as cartClient from '@/lib/cartClient';

vi.mock('@/lib/cartClient', () => {
  return {
    getCart: vi.fn().mockResolvedValue({
      cartId: 'cart-1',
      tenantId: 'tenant-1',
      customerSessionId: 'session-1',
      tableNumber: '04',
      currencyCode: 'INR',
      totalItemCount: 2,
      subtotal: 560,
      taxPercentage: 5,
      taxAmount: 28,
      serviceChargePercentage: 0,
      serviceChargeAmount: 0,
      grandTotal: 588,
      items: [
        {
          cartItemId: 'ci-1',
          menuItemId: 'item-1',
          itemName: 'Paneer Tikka',
          variantId: 'v-1',
          variantName: 'Regular Platter',
          unitPrice: 280,
          quantity: 2,
          lineTotal: 560,
          isAvailable: true,
          selectedOptions: [],
        },
      ],
    }),
    addCartItem: vi.fn().mockResolvedValue({}),
    updateCartItemQuantity: vi.fn().mockResolvedValue({}),
    removeCartItem: vi.fn().mockResolvedValue({}),
    clearCart: vi.fn().mockResolvedValue({}),
  };
});

vi.mock('@/lib/menuClient', async () => {
  const actual = await vi.importActual<typeof menuClient>('@/lib/menuClient');
  return {
    ...actual,
    getCustomerMenu: vi.fn().mockResolvedValue({
      tenantId: 'tenant-1',
      restaurantName: 'The Grand Heritage',
      tableNumber: '04',
      currencyCode: 'INR',
      taxPercentage: 5,
      serviceChargePercentage: 0,
      categories: [
        {
          id: 'cat-1',
          name: 'Starters',
          description: 'Crunchy appetizers',
          displayOrder: 0,
          items: [
            {
              id: 'item-1',
              name: 'Paneer Tikka',
              shortDescription: 'Charcoal grilled cottage cheese',
              detailedDescription: 'Marinated in yogurt and spices',
              effectivePrice: 280,
              originalBasePrice: 280,
              isBranchPriceOverridden: false,
              dietaryClassification: 'VEGETARIAN',
              preparationTimeMinutes: 15,
              isAvailable: true,
              primaryImageUrl: 'http://example.com/paneer.jpg',
              imageUrls: ['http://example.com/paneer.jpg'],
              variants: [
                { id: 'v-1', name: 'Regular Platter', price: 280, isAvailable: true },
                { id: 'v-2', name: 'Family Platter', price: 490, isAvailable: true },
              ],
              optionGroups: [
                {
                  id: 'og-1',
                  name: 'Chutney Choice',
                  minSelections: 1,
                  maxSelections: 1,
                  isRequired: true,
                  options: [
                    { id: 'opt-1', name: 'Mint Chutney', additionalPrice: 0, isAvailable: true },
                  ],
                },
              ],
            },
            {
              id: 'item-2',
              name: 'Chicken Wings',
              shortDescription: 'Spicy glazed wings',
              effectivePrice: 350,
              originalBasePrice: 350,
              isBranchPriceOverridden: false,
              dietaryClassification: 'NON_VEGETARIAN',
              preparationTimeMinutes: 20,
              isAvailable: false, // Out of stock
              imageUrls: [],
              variants: [],
              optionGroups: [],
            },
          ],
        },
      ],
    }),
  };
});

describe('CustomerMenuPage Digital Experience', () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  const renderComponent = () =>
    render(
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <CustomerMenuPage />
        </BrowserRouter>
      </QueryClientProvider>
    );

  it('renders restaurant identity, table number and menu items', async () => {
    renderComponent();

    expect(await screen.findByText(/The Grand Heritage/i)).toBeInTheDocument();
    expect(screen.getAllByText(/Table #04/i)[0]).toBeInTheDocument();
    expect(screen.getByText(/Paneer Tikka/i)).toBeInTheDocument();
    expect(screen.getByText(/₹280\.00/i)).toBeInTheDocument();
  });

  it('displays out of stock badge for unavailable dishes', async () => {
    renderComponent();

    expect(await screen.findByText(/Chicken Wings/i)).toBeInTheDocument();
    expect(screen.getByText(/Sold Out/i)).toBeInTheDocument();
  });

  it('filters dishes by dietary preference (Veg Only)', async () => {
    renderComponent();

    expect(await screen.findByText(/Paneer Tikka/i)).toBeInTheDocument();
    expect(screen.getByText(/Chicken Wings/i)).toBeInTheDocument();

    const vegFilterBtn = screen.getAllByRole('button', { name: /Veg Only/i })[0];
    fireEvent.click(vegFilterBtn);

    expect(screen.getByText(/Paneer Tikka/i)).toBeInTheDocument();
    expect(screen.queryByText(/Chicken Wings/i)).toBeNull();
  });

  it('opens item detail modal showing variants and add-on modifiers', async () => {
    renderComponent();

    const dishCard = await screen.findByText(/Paneer Tikka/i);
    fireEvent.click(dishCard);

    expect(await screen.findByText(/Select Portion Size \/ Variant/i)).toBeInTheDocument();
    expect(screen.getByText(/Family Platter/i)).toBeInTheDocument();
    expect(screen.getByText(/₹490\.00/i)).toBeInTheDocument();
    expect(screen.getByText(/Chutney Choice/i)).toBeInTheDocument();
  });
});
