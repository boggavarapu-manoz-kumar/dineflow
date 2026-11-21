import { getAccessToken } from '@/lib/authClient';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export interface KitchenItem {
  itemId: string;
  itemName: string;
  variantName?: string;
  quantity: number;
  specialInstructions?: string;
  options: string[];
}

export interface KitchenOrder {
  orderId: string;
  orderNumber: string;
  tableNumber: string;
  status: 'ACCEPTED' | 'PREPARING' | 'READY';
  branchId?: string;
  totalItemCount: number;
  specialNotes?: string;
  createdAt: string;
  acceptedAt?: string;
  items: KitchenItem[];
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
    throw new Error(body.message || body.errorCode || 'Kitchen request failed');
  }

  return body.data;
}

export const kitchenClient = {
  async getQueue(): Promise<KitchenOrder[]> {
    return request<KitchenOrder[]>('/kitchen/queue');
  },

  async startPreparation(orderId: string, note?: string): Promise<unknown> {
    return request(`/kitchen/orders/${orderId}/prepare`, {
      method: 'POST',
      body: JSON.stringify({ note }),
    });
  },

  async markReady(orderId: string, note?: string): Promise<unknown> {
    return request(`/kitchen/orders/${orderId}/ready`, {
      method: 'POST',
      body: JSON.stringify({ note }),
    });
  },
};
