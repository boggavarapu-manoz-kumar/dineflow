import { getAccessToken } from '@/lib/authClient';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export interface OrderItemOptionSummary {
  optionId: string;
  optionName: string;
  priceContribution: number;
}

export interface OrderItemSummary {
  itemId: string;
  menuItemId: string;
  itemName: string;
  variantId?: string;
  variantName?: string;
  quantity: number;
  baseUnitPrice: number;
  optionsUnitPrice: number;
  effectiveUnitPrice: number;
  lineTotal: number;
  specialInstructions?: string;
  options: OrderItemOptionSummary[];
}

export interface OrderDetail {
  orderId: string;
  orderNumber: string;
  status: 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'READY' | 'SERVED' | 'PAID' | 'CANCELLED';
  tenantId: string;
  branchId?: string;
  tableNumber: string;
  currencyCode: string;
  totalItemCount: number;
  subtotal: number;
  taxPercentage: number;
  taxAmount: number;
  serviceChargePercentage: number;
  serviceChargeAmount: number;
  grandTotal: number;
  specialNotes?: string;
  assignedWaiterId?: string;
  createdAt: string;
  items: OrderItemSummary[];
}

export interface OrderAssignmentRecord {
  id: string;
  orderId: string;
  orderNumber: string;
  waiterId: string;
  waiterName?: string;
  assignedByUserId: string;
  assignmentStatus: 'ACTIVE' | 'REASSIGNED' | 'UNASSIGNED';
  reason?: string;
  createdAt: string;
  unassignedAt?: string;
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = getAccessToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.headers as Record<string, string>),
  };

  if (token) {
    headers['Authorization'] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  const body = await response.json();
  if (!response.ok || !body.success) {
    throw new Error(body.message || body.errorCode || 'Request failed');
  }

  return body.data;
}

export const waiterClient = {
  async getMyAssignedOrders(): Promise<OrderDetail[]> {
    return request<OrderDetail[]>('/waiter/orders');
  },

  async getDispatchQueue(): Promise<OrderDetail[]> {
    return request<OrderDetail[]>('/waiter/dispatch-queue');
  },

  async assignOrder(orderId: string, waiterId: string, reason?: string): Promise<OrderDetail> {
    return request<OrderDetail>(`/waiter/orders/${orderId}/assign`, {
      method: 'POST',
      body: JSON.stringify({ waiterId, reason }),
    });
  },

  async unassignOrder(orderId: string, reason?: string): Promise<OrderDetail> {
    const query = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return request<OrderDetail>(`/waiter/orders/${orderId}/assign${query}`, {
      method: 'DELETE',
    });
  },

  async getAssignmentHistory(orderId: string): Promise<OrderAssignmentRecord[]> {
    return request<OrderAssignmentRecord[]>(`/waiter/orders/${orderId}/history`);
  },

  async transitionStatus(orderId: string, targetStatus: string, reason?: string): Promise<OrderDetail> {
    return request<OrderDetail>(`/orders/${orderId}/transition`, {
      method: 'POST',
      body: JSON.stringify({ targetStatus, reason }),
    });
  },
};
