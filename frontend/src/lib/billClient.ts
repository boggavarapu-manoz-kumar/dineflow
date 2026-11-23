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

export interface BillOptionResponse {
  id: string;
  optionName: string;
  priceContribution: number;
}

export interface BillItemResponse {
  id: string;
  orderId: string;
  itemName: string;
  variantName?: string;
  itemDescription?: string;
  quantity: number;
  baseUnitPrice: number;
  optionsUnitPrice: number;
  effectiveUnitPrice: number;
  lineTotal: number;
  specialInstructions?: string;
  options: BillOptionResponse[];
}

export interface BillStatusHistoryResponse {
  id: string;
  previousStatus?: string;
  newStatus: string;
  changedByUserId?: string;
  changedByRole: string;
  actorType: string;
  reason?: string;
  createdAt: string;
}

export interface BillResponse {
  id: string;
  tenantId: string;
  branchId?: string;
  customerSessionId?: string;
  billNumber: string;
  tableNumber: string;
  status: 'DRAFT' | 'ISSUED' | 'PARTIALLY_PAID' | 'PAID' | 'VOIDED';
  currencyCode: string;
  restaurantName: string;
  branchName?: string;
  branchAddress?: string;
  subtotal: number;
  discountAmount: number;
  taxPercentage: number;
  taxAmount: number;
  serviceChargePercentage: number;
  serviceChargeAmount: number;
  grandTotal: number;
  paidAmount: number;
  outstandingBalance: number;
  voidReason?: string;
  issuedAt: string;
  paidAt?: string;
  voidedAt?: string;
  orderNumbers: string[];
  items: BillItemResponse[];
  statusHistory: BillStatusHistoryResponse[];
}

export interface GenerateBillRequest {
  orderIds: string[];
  discountAmount?: number;
  specialNote?: string;
}

export interface VoidBillRequest {
  reason: string;
}

export const billClient = {
  listBills: async (params?: { status?: string; search?: string; branchId?: string }): Promise<BillResponse[]> => {
    const qs = new URLSearchParams();
    if (params?.status) qs.set('status', params.status);
    if (params?.search) qs.set('search', params.search);
    if (params?.branchId) qs.set('branchId', params.branchId);
    const query = qs.toString() ? `?${qs.toString()}` : '';
    return request<BillResponse[]>(`/staff/bills${query}`);
  },

  generateBill: async (data: GenerateBillRequest): Promise<BillResponse> => {
    return request<BillResponse>('/staff/bills/generate', {
      method: 'POST',
      body: JSON.stringify(data),
    });
  },

  getBill: async (billId: string): Promise<BillResponse> => {
    return request<BillResponse>(`/staff/bills/${billId}`);
  },

  getBillByOrder: async (orderId: string): Promise<BillResponse> => {
    return request<BillResponse>(`/staff/bills/by-order/${orderId}`);
  },

  voidBill: async (billId: string, reason: string): Promise<BillResponse> => {
    return request<BillResponse>(`/staff/bills/${billId}/void`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
  },

  getCustomerCurrentBill: async (): Promise<BillResponse> => {
    return request<BillResponse>('/customer/bills/current');
  }
};
