import { getAccessToken } from '@/lib/authClient';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const token = getAccessToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string> || {}),
  };
  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });

  const body = await response.json();
  if (!response.ok) {
    throw new Error(body?.message || `Request failed with status ${response.status}`);
  }
  return body.data;
}

export interface InvoiceResponse {
  id: string;
  tenantId: string;
  branchId?: string;
  billId: string;
  invoiceNumber: string;
  status: 'ISSUED' | 'CANCELLED';
  customerName?: string;
  customerTaxId?: string;
  customerPhone?: string;
  restaurantName: string;
  taxRegistrationNumber?: string;
  branchName?: string;
  branchAddress?: string;
  currencyCode: string;
  subtotal: number;
  discountAmount: number;
  taxableAmount: number;
  taxPercentage: number;
  taxAmount: number;
  serviceChargePercentage: number;
  serviceChargeAmount: number;
  grandTotal: number;
  paidAmount: number;
  paymentMethod?: string;
  paymentReference?: string;
  notes?: string;
  cancellationReason?: string;
  issuedAt: string;
  cancelledAt?: string;
  pdfUrl: string;
}

export interface GenerateInvoiceRequest {
  billId: string;
  customerName?: string;
  customerTaxId?: string;
  customerPhone?: string;
  notes?: string;
}

export const invoiceClient = {
  listInvoices: (params?: { search?: string; branchId?: string }): Promise<InvoiceResponse[]> => {
    const qs = new URLSearchParams();
    if (params?.search) qs.set('search', params.search);
    if (params?.branchId) qs.set('branchId', params.branchId);
    const query = qs.toString() ? `?${qs.toString()}` : '';
    return request<InvoiceResponse[]>(`/staff/invoices${query}`);
  },

  generateInvoice: (data: GenerateInvoiceRequest): Promise<InvoiceResponse> =>
    request<InvoiceResponse>('/staff/invoices/generate', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  cancelInvoice: (invoiceId: string, reason: string): Promise<InvoiceResponse> =>
    request<InvoiceResponse>(`/staff/invoices/${invoiceId}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    }),

  downloadPdf: async (invoiceId: string, invoiceNumber: string): Promise<void> => {
    const token = getAccessToken();
    const response = await fetch(`${API_BASE_URL}/staff/invoices/${invoiceId}/pdf`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!response.ok) {
      throw new Error(`Failed to download invoice PDF (${response.status})`);
    }
    const blob = await response.blob();
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${invoiceNumber}.pdf`;
    document.body.appendChild(a);
    a.click();
    a.remove();
    window.URL.revokeObjectURL(url);
  },
};
