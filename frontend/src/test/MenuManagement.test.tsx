import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import React from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BrowserRouter } from 'react-router-dom';
import { MenuManagementPage } from '@/pages/staff/MenuManagementPage';
import * as menuClient from '@/lib/menuClient';

vi.mock('@/lib/menuClient', async () => {
  const actual = await vi.importActual<typeof menuClient>('@/lib/menuClient');
  return {
    ...actual,
    getCategories: vi.fn().mockResolvedValue([
      {
        id: 'cat-1',
        tenantId: 'tenant-1',
        name: 'Appetizers',
        description: 'Tasty starters',
        displayOrder: 0,
        isActive: true,
        createdAt: '2026-11-21T00:00:00Z',
        updatedAt: '2026-11-21T00:00:00Z',
      },
    ]),
    getMenuItems: vi.fn().mockResolvedValue({
      content: [
        {
          id: 'item-1',
          categoryId: 'cat-1',
          categoryName: 'Appetizers',
          name: 'Spring Rolls',
          shortDescription: 'Crispy rolls with sweet chili',
          basePrice: 180,
          dietaryClassification: 'VEGETARIAN',
          isActive: true,
          isAvailable: true,
          displayOrder: 0,
        },
      ],
      totalElements: 1,
      totalPages: 1,
      size: 50,
      number: 0,
    }),
    createCategory: vi.fn(),
    createMenuItem: vi.fn(),
  };
});

describe('MenuManagementPage Dashboard & Catalog UI', () => {
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
          <MenuManagementPage />
        </BrowserRouter>
      </QueryClientProvider>
    );

  it('renders menu overview with correct KPI counters', async () => {
    renderComponent();

    expect(screen.getByText(/Menu & Catalog Management/i)).toBeInTheDocument();
    expect(await screen.findByText(/Live Operations/i)).toBeInTheDocument();
    expect(screen.getByText(/Live Menu Breakdown/i)).toBeInTheDocument();
  });

  it('switches to Categories tab and allows opening creation form', async () => {
    renderComponent();

    const categoriesTab = screen.getByRole('button', { name: /Categories/i });
    fireEvent.click(categoriesTab);

    expect(await screen.findByText(/Menu Categories/i)).toBeInTheDocument();
    const addCategoryBtn = screen.getByRole('button', { name: /Add Category/i });
    fireEvent.click(addCategoryBtn);

    expect(screen.getByPlaceholderText(/e\.g\. Appetizers & Starters/i)).toBeInTheDocument();
  });

  it('switches to Dishes & Items tab and renders item list with filters', async () => {
    renderComponent();

    const itemsTab = screen.getByRole('button', { name: /Dishes & Items/i });
    fireEvent.click(itemsTab);

    expect(await screen.findByText(/Food Items & Dishes/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/Search items by name/i)).toBeInTheDocument();
    expect(await screen.findByText(/Spring Rolls/i)).toBeInTheDocument();
    expect(screen.getByText(/₹180\.00/i)).toBeInTheDocument();
  });

  it('switches to Branch Overrides tab and displays pricing table', async () => {
    renderComponent();

    const branchTab = screen.getByRole('button', { name: /Branch Overrides/i });
    fireEvent.click(branchTab);

    expect(await screen.findByText(/Branch Pricing & Stock Availability/i)).toBeInTheDocument();
    expect(screen.getByText(/Downtown Flagship/i)).toBeInTheDocument();
    expect(screen.getByText(/Spring Rolls/i)).toBeInTheDocument();
  });
});
