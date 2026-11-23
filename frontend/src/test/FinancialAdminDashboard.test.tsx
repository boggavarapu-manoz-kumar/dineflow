import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { FinancialAdminDashboardPage } from '@/pages/staff/FinancialAdminDashboardPage';

vi.mock('@/context/AuthContext', () => ({
  useAuth: () => ({
    user: { id: 'u1', role: 'HOTEL_ADMIN', permissions: ['BILL_VIEW', 'PAYMENT_PROCESS', 'INVOICE_GENERATE', 'REFUND_APPROVE', 'REFUND_EXECUTE'] },
    isAuthenticated: true,
    isLoading: false,
    hasRole: (r: string) => r === 'HOTEL_ADMIN',
    hasPermission: () => true,
  }),
}));

vi.mock('@/lib/reportClient', () => ({
  reportClient: {
    getFinancialReport: vi.fn().mockResolvedValue({
      currencyCode: 'INR',
      billCount: 12,
      netSales: 14500.5,
      confirmedCollectionTotal: 13200.0,
      netConfirmedCollection: 12900.0,
      outstandingBalance: 1300.5,
      outstandingBillCount: 2,
      refundTotal: 300.0,
      refundCount: 1,
    }),
  },
}));

vi.mock('@/lib/billClient', () => ({
  billClient: {
    listBills: vi.fn().mockResolvedValue([
      {
        id: 'b1',
        tenantId: 't1',
        billNumber: 'BILL-20261123-0001',
        tableNumber: 'T-04',
        status: 'ISSUED',
        currencyCode: 'INR',
        restaurantName: 'Grand Palace',
        subtotal: 1000,
        discountAmount: 0,
        taxPercentage: 5,
        taxAmount: 50,
        serviceChargePercentage: 0,
        serviceChargeAmount: 0,
        grandTotal: 1050,
        paidAmount: 0,
        outstandingBalance: 1050,
        issuedAt: new Date().toISOString(),
        orderNumbers: ['ORD-001'],
        items: [],
        statusHistory: [],
      },
    ]),
  },
}));

vi.mock('@/lib/paymentClient', () => ({
  paymentClient: {
    listPayments: vi.fn().mockResolvedValue([
      {
        id: 'p1',
        tenantId: 't1',
        billId: 'b1',
        paymentReference: 'PAY-20261123-9999',
        paymentMethod: 'ONLINE_PAYMENT',
        paymentStatus: 'SUCCEEDED',
        currencyCode: 'INR',
        payableAmount: 1050,
        paidAmount: 1050,
        refundedAmount: 200,
        paymentProvider: 'RAZORPAY',
        createdAt: new Date().toISOString(),
      },
      {
        id: 'p2',
        tenantId: 't1',
        billId: 'b2',
        paymentReference: 'PAY-20261123-8888',
        paymentMethod: 'ONLINE_PAYMENT',
        paymentStatus: 'FAILED',
        currencyCode: 'INR',
        payableAmount: 500,
        paidAmount: 0,
        refundedAmount: 0,
        paymentProvider: 'RAZORPAY',
        createdAt: new Date().toISOString(),
      },
    ]),
    listRefunds: vi.fn().mockResolvedValue([
      {
        id: 'r1',
        tenantId: 't1',
        paymentId: 'p1',
        billId: 'b1',
        refundReference: 'REF-20261123-1111',
        refundAmount: 200,
        currencyCode: 'INR',
        refundMethod: 'ONLINE_GATEWAY',
        refundStatus: 'APPROVED',
        reason: 'Item cancelled',
        createdAt: new Date().toISOString(),
      },
    ]),
    approveRefund: vi.fn(),
    executeRefund: vi.fn(),
    requestRefund: vi.fn(),
  },
}));

vi.mock('@/lib/invoiceClient', () => ({
  invoiceClient: {
    listInvoices: vi.fn().mockResolvedValue([
      {
        id: 'inv1',
        tenantId: 't1',
        billId: 'b1',
        invoiceNumber: 'INV-20261123-0001',
        status: 'ISSUED',
        restaurantName: 'Grand Palace',
        currencyCode: 'INR',
        subtotal: 1000,
        discountAmount: 0,
        taxableAmount: 1000,
        taxPercentage: 5,
        taxAmount: 50,
        serviceChargePercentage: 0,
        serviceChargeAmount: 0,
        grandTotal: 1050,
        paidAmount: 1050,
        issuedAt: new Date().toISOString(),
        pdfUrl: '/api/v1/staff/invoices/inv1/pdf',
      },
    ]),
    generateInvoice: vi.fn(),
    cancelInvoice: vi.fn(),
    downloadPdf: vi.fn(),
  },
}));

function renderWithQuery(ui: React.ReactElement) {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={qc}>{ui}</QueryClientProvider>);
}

describe('FinancialAdminDashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads and displays live financial overview KPIs from reporting API', async () => {
    renderWithQuery(<FinancialAdminDashboardPage />);
    expect(screen.getByText('Financial Operations Console')).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText(/14500\.50/)).toBeInTheDocument();
      expect(screen.getByText(/13200\.00/)).toBeInTheDocument();
    });
  });

  it('switches to Bills tab and opens Collect Cash modal for unpaid bill', async () => {
    renderWithQuery(<FinancialAdminDashboardPage />);
    fireEvent.click(screen.getByRole('tab', { name: /Bills/i }));
    await waitFor(() => {
      expect(screen.getByText('BILL-20261123-0001')).toBeInTheDocument();
    });
    expect(screen.getByRole('button', { name: /Collect Cash/i })).toBeInTheDocument();
  });

  it('switches to Payments tab and clearly distinguishes Succeeded vs Failed and remaining refundable', async () => {
    renderWithQuery(<FinancialAdminDashboardPage />);
    fireEvent.click(screen.getByRole('tab', { name: /Payments/i }));
    await waitFor(() => {
      expect(screen.getByText('PAY-20261123-9999')).toBeInTheDocument();
      expect(screen.getByText('PAY-20261123-8888')).toBeInTheDocument();
      expect(screen.getAllByText('Succeeded').length).toBeGreaterThanOrEqual(2);
      expect(screen.getAllByText('Failed').length).toBeGreaterThanOrEqual(2);
      // Remaining refundable: 1050 - 200 = 850.00
      expect(screen.getByText(/850\.00/)).toBeInTheDocument();
    });
  });

  it('switches to Invoices and Refunds tabs and shows authorized workflow actions', async () => {
    renderWithQuery(<FinancialAdminDashboardPage />);
    fireEvent.click(screen.getByRole('tab', { name: /Invoices/i }));
    await waitFor(() => {
      expect(screen.getByText('INV-20261123-0001')).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole('tab', { name: /Refunds/i }));
    await waitFor(() => {
      expect(screen.getByText('REF-20261123-1111')).toBeInTheDocument();
      expect(screen.getByRole('button', { name: /Execute Refund/i })).toBeInTheDocument();
    });
  });
});
