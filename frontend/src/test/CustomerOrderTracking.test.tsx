import React from 'react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor, act } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { CustomerOrderTrackingPage } from '@/pages/customer/CustomerOrderTrackingPage';
import { customerOrderClient, CustomerOrder } from '@/lib/customerOrderClient';
import { billClient, BillResponse } from '@/lib/billClient';
import { RealtimeEventEnvelope } from '@/lib/realtimeClient';

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

vi.mock('@/lib/customerOrderClient', () => ({
  customerOrderClient: {
    getOrder: vi.fn(),
    getOrderHistory: vi.fn(),
    cancelOrder: vi.fn(),
  },
}));

vi.mock('@/lib/billClient', () => ({
  billClient: {
    getCustomerCurrentBill: vi.fn(),
  },
}));

const renderTracking = (orderId = 'ord-guest-1') => {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={[`/customer/orders/${orderId}`]}>
        <Routes>
          <Route path="/customer/orders/:orderId" element={<CustomerOrderTrackingPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
};

const sampleOrder: CustomerOrder = {
  orderId: 'ord-guest-1',
  orderNumber: 'ORD-5501',
  status: 'SERVED',
  tenantId: '11111111-1111-1111-1111-111111111111',
  branchId: '22222222-2222-2222-2222-222222222222',
  tableNumber: 'T-03',
  currencyCode: 'INR',
  totalItemCount: 1,
  subtotal: 300,
  taxPercentage: 5,
  taxAmount: 15,
  serviceChargePercentage: 0,
  serviceChargeAmount: 0,
  grandTotal: 315,
  createdAt: new Date().toISOString(),
  items: [
    {
      itemId: 'item-1',
      menuItemId: 'm-1',
      itemName: 'Masala Dosa',
      variantName: 'Ghee Roast',
      quantity: 1,
      baseUnitPrice: 300,
      optionsUnitPrice: 0,
      effectiveUnitPrice: 300,
      lineTotal: 300,
      options: [{ optionId: 'o-1', optionName: 'Extra Sambar', priceContribution: 0 }],
    },
  ],
};

const unpaidBill: BillResponse = {
  id: 'bill-1',
  tenantId: '11111111-1111-1111-1111-111111111111',
  branchId: '22222222-2222-2222-2222-222222222222',
  customerSessionId: 'sess-guest-1',
  billNumber: 'BILL-2026-009',
  tableNumber: 'T-03',
  status: 'ISSUED',
  currencyCode: 'INR',
  restaurantName: 'Royal Spice Kitchen',
  branchName: 'Indiranagar Branch',
  subtotal: 300,
  discountAmount: 0,
  taxPercentage: 5,
  taxAmount: 15,
  serviceChargePercentage: 0,
  serviceChargeAmount: 0,
  grandTotal: 315,
  paidAmount: 0,
  outstandingBalance: 315,
  issuedAt: new Date().toISOString(),
  orderNumbers: ['ORD-5501'],
  items: [],
  statusHistory: [],
};

describe('CustomerOrderTrackingPage Live Order & Payment Separation', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    capturedOnEvent = null;
    capturedOnReconnect = null;
  });

  it('displays served order separately from unpaid bill and reacts to live payment, refund, and reconnect events', async () => {
    vi.mocked(customerOrderClient.getOrder).mockResolvedValue(sampleOrder);
    vi.mocked(customerOrderClient.getOrderHistory).mockResolvedValue([]);
    vi.mocked(billClient.getCustomerCurrentBill).mockResolvedValue(unpaidBill);

    renderTracking('ord-guest-1');

    // Order is SERVED while payment is strictly separate and still Payment Pending
    expect(await screen.findByText('Served to Table')).toBeInTheDocument();
    expect(await screen.findByText('BILL-2026-009')).toBeInTheDocument();
    expect(screen.getByText('Payment Pending')).toBeInTheDocument();
    expect(screen.getByText(/Royal Spice Kitchen/)).toBeInTheDocument();
    expect(screen.getByText('Indiranagar Branch')).toBeInTheDocument();
    expect(screen.getByText('Live Updates Active')).toBeInTheDocument();

    // Live PAYMENT_SETTLED event arrives -> shows confirmed notice & refreshes authoritative bill
    act(() => {
      capturedOnEvent?.({
        eventId: 'evt-pay-1',
        eventType: 'PAYMENT_SETTLED',
        eventVersion: '1.0',
        tenantId: sampleOrder.tenantId,
        channel: 'session',
        resourceId: 'sess-guest-1',
        payload: { billNumber: 'BILL-2026-009', billStatus: 'PAID' },
        timestamp: new Date().toISOString(),
      });
    });
    expect(await screen.findByText(/Payment confirmed by restaurant billing system/i)).toBeInTheDocument();

    // Live REFUND_EXECUTED event arrives -> displays refund status without cancelling the served order
    act(() => {
      capturedOnEvent?.({
        eventId: 'evt-ref-1',
        eventType: 'REFUND_EXECUTED',
        eventVersion: '1.0',
        tenantId: sampleOrder.tenantId,
        channel: 'session',
        resourceId: 'sess-guest-1',
        payload: { refundAmount: 100, currencyCode: 'INR', refundStatus: 'SUCCEEDED' },
        timestamp: new Date().toISOString(),
      });
    });
    expect(await screen.findByText(/Refund of INR 100\.00 processed/i)).toBeInTheDocument();
    expect(screen.getByText('Served to Table')).toBeInTheDocument();

    // Reconnect triggers authoritative REST refresh
    const callsBefore = vi.mocked(customerOrderClient.getOrder).mock.calls.length;
    act(() => {
      capturedOnReconnect?.();
    });
    await waitFor(() => {
      expect(vi.mocked(customerOrderClient.getOrder).mock.calls.length).toBeGreaterThan(callsBefore);
    });
  });

  it('handles expired dining session (401 SESSION_EXPIRED) with clear QR re-entry guidance', async () => {
    const expiredErr = Object.assign(new Error('Customer session has expired'), {
      status: 401,
      errorCode: 'SESSION_EXPIRED',
    });
    vi.mocked(customerOrderClient.getOrder).mockRejectedValue(expiredErr);

    renderTracking('ord-expired');

    expect(await screen.findByText('Dining Session Expired')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Re-scan Table QR' })).toBeInTheDocument();
  });

  it('blocks unauthorized cross-guest or cross-tenant order access cleanly (403)', async () => {
    const forbiddenErr = Object.assign(new Error('Access denied: You do not own this dining order'), {
      status: 403,
      errorCode: 'ORDER_ACCESS_DENIED',
    });
    vi.mocked(customerOrderClient.getOrder).mockRejectedValue(forbiddenErr);

    renderTracking('ord-other-guest');

    expect(await screen.findByText('Order Not Accessible')).toBeInTheDocument();
    expect(screen.getByText(/You do not own this dining order/i)).toBeInTheDocument();
  });
});
