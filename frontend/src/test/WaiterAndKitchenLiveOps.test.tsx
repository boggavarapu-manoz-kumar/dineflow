import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { WaiterDashboardPage } from '@/pages/staff/WaiterDashboardPage';
import { KitchenDisplayPage } from '@/pages/staff/KitchenDisplayPage';
import { RealtimeEventGuard, RealtimeEventEnvelope } from '@/lib/realtimeClient';
import { waiterClient, OrderDetail } from '@/lib/waiterClient';
import { kitchenClient, KitchenOrder } from '@/lib/kitchenClient';

// Mutable mock auth state
let mockUser = {
  id: 'staff-1',
  email: 'waiter@hotel.test',
  fullName: 'Aarav Waiter',
  status: 'ACTIVE',
  activeTenantId: '11111111-1111-1111-1111-111111111111',
  branchId: '22222222-2222-2222-2222-222222222222',
  role: 'WAITER',
  permissions: ['WAITER_VIEW', 'ORDER_ACCEPT', 'ORDER_SERVE'],
};

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    user: mockUser,
    isAuthenticated: true,
    isLoading: false,
    login: vi.fn(),
    logout: vi.fn(),
    hasRole: (r: string) => mockUser.role === r,
    hasPermission: (p: string) => mockUser.permissions.includes(p),
  }),
}));

// Capture realtime hook callbacks so tests can simulate live events & reconnects deterministically
let capturedOnEvent: ((event: RealtimeEventEnvelope) => void) | null = null;
let capturedOnReconnect: (() => void) | null = null;

vi.mock('@/hooks/useRealtimeChannel', () => ({
  useRealtimeChannel: (opts: {
    onEvent: (event: RealtimeEventEnvelope) => void;
    onReconnect?: () => void;
  }) => {
    capturedOnEvent = opts.onEvent;
    capturedOnReconnect = opts.onReconnect || null;
    return { connectionState: 'CONNECTED', lastEventAt: Date.now() };
  },
}));

vi.mock('@/lib/waiterClient', () => ({
  waiterClient: {
    getMyAssignedOrders: vi.fn(),
    transitionStatus: vi.fn(),
  },
}));

vi.mock('@/lib/kitchenClient', () => ({
  kitchenClient: {
    getQueue: vi.fn(),
    startPreparation: vi.fn(),
    markReady: vi.fn(),
  },
}));

const renderWithProviders = (ui: React.ReactElement) => {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter>{ui}</MemoryRouter>
    </QueryClientProvider>
  );
};

const sampleWaiterOrders: OrderDetail[] = [
  {
    orderId: 'ord-1',
    orderNumber: 'ORD-1001',
    status: 'PLACED',
    tenantId: '11111111-1111-1111-1111-111111111111',
    branchId: '22222222-2222-2222-2222-222222222222',
    tableNumber: 'T-05',
    currencyCode: 'INR',
    totalItemCount: 2,
    subtotal: 400,
    taxPercentage: 5,
    taxAmount: 20,
    serviceChargePercentage: 0,
    serviceChargeAmount: 0,
    grandTotal: 420,
    specialNotes: 'Less spicy please',
    createdAt: new Date().toISOString(),
    items: [
      {
        itemId: 'i-1',
        menuItemId: 'm-1',
        itemName: 'Paneer Tikka',
        quantity: 2,
        baseUnitPrice: 200,
        optionsUnitPrice: 0,
        effectiveUnitPrice: 200,
        lineTotal: 400,
        options: [],
      },
    ],
  },
  {
    orderId: 'ord-2',
    orderNumber: 'ORD-1002',
    status: 'SERVED',
    tenantId: '11111111-1111-1111-1111-111111111111',
    branchId: '22222222-2222-2222-2222-222222222222',
    tableNumber: 'T-08',
    currencyCode: 'INR',
    totalItemCount: 1,
    subtotal: 250,
    taxPercentage: 5,
    taxAmount: 12.5,
    serviceChargePercentage: 0,
    serviceChargeAmount: 0,
    grandTotal: 262.5,
    createdAt: new Date().toISOString(),
    items: [
      {
        itemId: 'i-2',
        menuItemId: 'm-2',
        itemName: 'Dal Makhani',
        quantity: 1,
        baseUnitPrice: 250,
        optionsUnitPrice: 0,
        effectiveUnitPrice: 250,
        lineTotal: 250,
        options: [],
      },
    ],
  },
];

const sampleKitchenOrders: KitchenOrder[] = [
  {
    orderId: 'k-ord-1',
    orderNumber: 'ORD-2001',
    tableNumber: 'T-12',
    status: 'ACCEPTED',
    branchId: '22222222-2222-2222-2222-222222222222',
    totalItemCount: 1,
    createdAt: new Date(Date.now() - 25 * 60000).toISOString(), // 25m elapsed -> overdue
    items: [
      {
        itemId: 'ki-1',
        itemName: 'Butter Naan',
        variantName: 'Garlic',
        quantity: 3,
        specialInstructions: 'Extra crisp',
        options: ['Extra Butter'],
      },
    ],
  },
  {
    orderId: 'k-ord-2',
    orderNumber: 'ORD-2002',
    tableNumber: 'T-14',
    status: 'PREPARING',
    branchId: '22222222-2222-2222-2222-222222222222',
    totalItemCount: 1,
    createdAt: new Date().toISOString(),
    items: [
      {
        itemId: 'ki-2',
        itemName: 'Veg Biryani',
        quantity: 1,
        options: ['Raita'],
      },
    ],
  },
];

describe('WaiterDashboardPage, KitchenDisplayPage & RealtimeEventGuard', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedOnEvent = null;
    capturedOnReconnect = null;
    mockUser = {
      id: 'staff-1',
      email: 'waiter@hotel.test',
      fullName: 'Aarav Waiter',
      status: 'ACTIVE',
      activeTenantId: '11111111-1111-1111-1111-111111111111',
      branchId: '22222222-2222-2222-2222-222222222222',
      role: 'WAITER',
      permissions: ['WAITER_VIEW', 'ORDER_ACCEPT', 'ORDER_SERVE'],
    };
  });

  it('Waiter sees authorized actions, hides unauthorized Cancel & Cash actions, and transitions orders', async () => {
    vi.mocked(waiterClient.getMyAssignedOrders).mockResolvedValue(sampleWaiterOrders);
    vi.mocked(waiterClient.transitionStatus).mockResolvedValue({ ...sampleWaiterOrders[0], status: 'ACCEPTED' });

    renderWithProviders(<WaiterDashboardPage />);

    expect(await screen.findByText('ORD-1001')).toBeInTheDocument();
    expect(screen.getByText('Live Connected')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Accept Order' })).toBeInTheDocument();

    // Unauthorized actions MUST be hidden
    expect(screen.queryByRole('button', { name: 'Cancel Order' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Cash \/ Billing/i })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Accept Order' }));
    await waitFor(() => {
      expect(waiterClient.transitionStatus).toHaveBeenCalledWith('ord-1', 'ACCEPTED', undefined);
    });
  });

  it('Waiter dashboard displays live ORDER_READY alert and refreshes on WebSocket reconnect', async () => {
    vi.mocked(waiterClient.getMyAssignedOrders).mockResolvedValue(sampleWaiterOrders);

    renderWithProviders(<WaiterDashboardPage />);
    expect(await screen.findByText('ORD-1001')).toBeInTheDocument();
    expect(waiterClient.getMyAssignedOrders).toHaveBeenCalledTimes(1);

    // Simulate incoming live ORDER_READY event
    act(() => {
      capturedOnEvent?.({
        eventId: 'evt-ready-1',
        eventType: 'ORDER_READY',
        eventVersion: '1.0',
        tenantId: mockUser.activeTenantId,
        branchId: mockUser.branchId,
        channel: 'waiter',
        resourceId: 'ord-1',
        domainVersion: 4,
        payload: { orderNumber: 'ORD-1001', tableNumber: 'T-05', status: 'READY' },
        timestamp: new Date().toISOString(),
      });
    });

    expect(await screen.findByText(/ORD-1001.*READY for pickup/i)).toBeInTheDocument();
    await waitFor(() => {
      expect(waiterClient.getMyAssignedOrders).toHaveBeenCalledTimes(2);
    });

    // Simulate WebSocket reconnect -> refreshes authoritative REST state
    act(() => {
      capturedOnReconnect?.();
    });
    await waitFor(() => {
      expect(waiterClient.getMyAssignedOrders).toHaveBeenCalledTimes(3);
    });
  });

  it('Waiter dashboard shows recovery card on API failure', async () => {
    vi.mocked(waiterClient.getMyAssignedOrders).mockRejectedValue(new Error('Network unavailable'));

    renderWithProviders(<WaiterDashboardPage />);
    expect(await screen.findByText(/Failed to load assigned orders/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry Now' })).toBeInTheDocument();
  });

  it('KitchenDisplayPage renders tickets with priority/variants and executes Start Preparation & Mark Ready', async () => {
    mockUser = {
      ...mockUser,
      role: 'KITCHEN_STAFF',
      permissions: ['KITCHEN_VIEW', 'KITCHEN_UPDATE'],
    };
    vi.mocked(kitchenClient.getQueue).mockResolvedValue(sampleKitchenOrders);
    vi.mocked(kitchenClient.startPreparation).mockResolvedValue({});
    vi.mocked(kitchenClient.markReady).mockResolvedValue({});

    renderWithProviders(<KitchenDisplayPage />);

    expect(await screen.findByText('ORD-2001')).toBeInTheDocument();
    expect(screen.getByText(/PRIORITY OVERDUE/i)).toBeInTheDocument();
    expect(screen.getByText(/Extra Butter/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Start Preparation' }));
    await waitFor(() => {
      expect(kitchenClient.startPreparation).toHaveBeenCalledWith('k-ord-1');
    });

    fireEvent.click(screen.getByRole('button', { name: 'Mark Ready' }));
    await waitFor(() => {
      expect(kitchenClient.markReady).toHaveBeenCalledWith('k-ord-2');
    });
  });

  it('KitchenDisplayPage displays empty queue state cleanly', async () => {
    mockUser = { ...mockUser, role: 'KITCHEN_STAFF', permissions: ['KITCHEN_VIEW'] };
    vi.mocked(kitchenClient.getQueue).mockResolvedValue([]);

    renderWithProviders(<KitchenDisplayPage />);
    expect(await screen.findByText('Kitchen Queue Clear')).toBeInTheDocument();
  });

  it('RealtimeEventGuard deduplicates duplicate events and rejects out-of-order status regressions', () => {
    const guard = new RealtimeEventGuard();
    const evt1: RealtimeEventEnvelope = {
      eventId: 'e-1',
      eventType: 'ORDER_READY',
      eventVersion: '1.0',
      tenantId: 't-1',
      channel: 'waiter',
      resourceId: 'ord-99',
      domainVersion: 4,
      payload: { status: 'READY' },
      timestamp: new Date().toISOString(),
    };

    expect(guard.accept(evt1)).toBe(true);
    // Exact duplicate eventId rejected
    expect(guard.accept(evt1)).toBe(false);
    // Out-of-order older domainVersion rejected
    expect(guard.accept({ ...evt1, eventId: 'e-2', domainVersion: 3, payload: { status: 'PREPARING' } })).toBe(false);
    // Newer valid progression accepted
    expect(guard.accept({ ...evt1, eventId: 'e-3', domainVersion: 5, payload: { status: 'SERVED' } })).toBe(true);
  });
});
