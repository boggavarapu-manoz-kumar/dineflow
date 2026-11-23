import { getAccessToken } from '@/lib/authClient';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const staffToken = getAccessToken();
  const customerToken = localStorage.getItem('dineflow_customer_session_token');
  const token = staffToken || customerToken;

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

export interface InitiatePaymentRequest {
  billId: string;
  paymentMethod: 'CASH' | 'PAY_AT_COUNTER' | 'ONLINE_PAYMENT';
  providerType?: 'RAZORPAY' | 'STRIPE' | 'MOCK' | 'CASH_REGISTER';
  amount?: number;
  notes?: string;
  returnUrl?: string;
}

export interface RecordCashPaymentRequest {
  billId: string;
  amountReceived: number;
  notes?: string;
}

export interface ReversePaymentRequest {
  reason: string;
}

export interface CashCollectionSummary {
  tenantId: string;
  branchId?: string;
  staffUserId: string;
  staffName: string;
  periodStart: string;
  periodEnd: string;
  totalTransactions: number;
  totalCashReceived: number;
  totalReversed: number;
  netCashCollected: number;
  currencyCode: string;
}

export interface VerifyPaymentRequest {
  providerTransactionId: string;
  providerOrderId?: string;
  signature?: string;
}

export interface PaymentResponse {
  id: string;
  tenantId: string;
  branchId?: string;
  billId: string;
  paymentReference: string;
  paymentMethod: string;
  paymentStatus: 'CREATED' | 'PENDING' | 'INITIATED' | 'PROCESSING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED' | 'REFUNDED';
  currencyCode: string;
  payableAmount: number;
  paidAmount: number;
  refundedAmount: number;
  paymentProvider: string;
  providerPaymentId?: string;
  providerOrderId?: string;
  clientSecretOrKeyId?: string;
  clientRedirectUrl?: string;
  notes?: string;
  settledAt?: string;
  createdAt: string;
}

export interface RefundResponse {
  id: string;
  tenantId: string;
  branchId?: string;
  paymentId: string;
  billId: string;
  refundReference: string;
  refundAmount: number;
  currencyCode: string;
  refundMethod: 'CASH' | 'ONLINE_GATEWAY' | 'ADJUSTMENT';
  refundStatus: 'PENDING_APPROVAL' | 'APPROVED' | 'INITIATED' | 'SUCCEEDED' | 'FAILED' | 'REJECTED';
  reason: string;
  providerRefundId?: string;
  failureMessage?: string;
  requestedByUserId?: string;
  approvedByUserId?: string;
  settledAt?: string;
  createdAt: string;
}

export interface RequestRefundRequest {
  paymentId: string;
  amount: number;
  refundMethod: 'CASH' | 'ONLINE_GATEWAY' | 'ADJUSTMENT';
  reason: string;
}

export const paymentClient = {
  listPayments: (params?: { status?: string; search?: string; branchId?: string }): Promise<PaymentResponse[]> => {
    const qs = new URLSearchParams();
    if (params?.status) qs.set('status', params.status);
    if (params?.search) qs.set('search', params.search);
    if (params?.branchId) qs.set('branchId', params.branchId);
    const query = qs.toString() ? `?${qs.toString()}` : '';
    return request<PaymentResponse[]>(`/payments${query}`);
  },

  listRefunds: (status?: string): Promise<RefundResponse[]> => {
    const query = status ? `?status=${encodeURIComponent(status)}` : '';
    return request<RefundResponse[]>(`/payments/refunds${query}`);
  },

  requestRefund: (data: RequestRefundRequest): Promise<RefundResponse> =>
    request<RefundResponse>('/payments/refunds/request', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  approveRefund: (refundId: string): Promise<RefundResponse> =>
    request<RefundResponse>(`/payments/refunds/${refundId}/approve`, {
      method: 'POST',
    }),

  executeRefund: (refundId: string): Promise<RefundResponse> =>
    request<RefundResponse>(`/payments/refunds/${refundId}/execute`, {
      method: 'POST',
    }),

  initiate: (data: InitiatePaymentRequest): Promise<PaymentResponse> => {
    return request<PaymentResponse>('/payments/initiate', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  recordCash: (data: RecordCashPaymentRequest): Promise<PaymentResponse> => {
    return request<PaymentResponse>('/payments/staff/cash', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  reverse: (paymentId: string, data: ReversePaymentRequest): Promise<PaymentResponse> => {
    return request<PaymentResponse>(`/payments/${paymentId}/reverse`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  getCashSummary: (start?: string, end?: string): Promise<CashCollectionSummary> => {
    const params = new URLSearchParams();
    if (start) params.append('start', start);
    if (end) params.append('end', end);
    const queryString = params.toString() ? `?${params.toString()}` : '';
    return request<CashCollectionSummary>(`/payments/staff/cash-summary${queryString}`);
  },

  settle: (paymentId: string, data: VerifyPaymentRequest): Promise<PaymentResponse> => {
    return request<PaymentResponse>(`/payments/${paymentId}/settle`, {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  getPayment: (paymentId: string): Promise<PaymentResponse> => {
    return request<PaymentResponse>(`/payments/${paymentId}`);
  },
};
