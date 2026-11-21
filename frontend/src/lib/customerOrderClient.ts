import { getAccessToken } from '@/lib/authClient';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export interface CustomerOrderItemOption {
  optionId: string;
  optionName: string;
  priceContribution: number;
}

export interface CustomerOrderItem {
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
  options: CustomerOrderItemOption[];
}

export interface CustomerOrder {
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
  items: CustomerOrderItem[];
}

export interface CustomerOrderStatusHistory {
  id: string;
  orderId: string;
  previousStatus?: string;
  newStatus: string;
  actorType: string;
  changedByUserId?: string;
  changedByRole: string;
  reason?: string;
  createdAt: string;
}

export interface SubmitOrderPayload {
  specialNotes?: string;
  expectedGrandTotal?: number;
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
    const error = new Error(body.message || body.errorCode || 'Request failed');
    (error as unknown as { errorCode?: string; status?: number }).errorCode = body.errorCode;
    (error as unknown as { errorCode?: string; status?: number }).status = response.status;
    throw error;
  }

  return body.data;
}

export const customerOrderClient = {
  async submitOrder(payload: SubmitOrderPayload, idempotencyKey?: string): Promise<CustomerOrder> {
    const headers: Record<string, string> = {};
    if (idempotencyKey) {
      headers['Idempotency-Key'] = idempotencyKey;
    }
    return request<CustomerOrder>('/customer/orders', {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
    });
  },

  async getMyOrders(): Promise<CustomerOrder[]> {
    return request<CustomerOrder[]>('/customer/orders');
  },

  async getOrder(orderId: string): Promise<CustomerOrder> {
    return request<CustomerOrder>(`/customer/orders/${orderId}`);
  },

  async cancelOrder(orderId: string, reason?: string): Promise<CustomerOrder> {
    return request<CustomerOrder>(`/customer/orders/${orderId}/cancel`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    });
  },

  async getOrderHistory(orderId: string): Promise<CustomerOrderStatusHistory[]> {
    return request<CustomerOrderStatusHistory[]>(`/orders/${orderId}/history`);
  },
};
