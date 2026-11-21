import { getAccessToken } from '@/lib/authClient';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export interface CartOption {
  optionId: string;
  optionName: string;
  additionalPrice: number;
}

export interface CartItem {
  cartItemId: string;
  menuItemId: string;
  itemName: string;
  variantId?: string;
  variantName?: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  specialInstructions?: string;
  isAvailable: boolean;
  selectedOptions: CartOption[];
}

export interface CustomerCart {
  cartId: string;
  tenantId: string;
  branchId?: string;
  customerSessionId: string;
  tableNumber: string;
  currencyCode: string;
  totalItemCount: number;
  subtotal: number;
  taxPercentage: number;
  taxAmount: number;
  serviceChargePercentage: number;
  serviceChargeAmount: number;
  grandTotal: number;
  items: CartItem[];
}

export interface AddCartItemPayload {
  menuItemId: string;
  variantId?: string;
  optionIds?: string[];
  quantity: number;
  specialInstructions?: string;
}

export interface UpdateQuantityPayload {
  quantity: number;
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
    throw new Error(body.message || body.errorCode || 'Cart request failed');
  }

  return body.data;
}

export async function getCart(): Promise<CustomerCart> {
  return request<CustomerCart>('/customer/cart');
}

export async function addCartItem(payload: AddCartItemPayload): Promise<CustomerCart> {
  return request<CustomerCart>('/customer/cart/items', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export async function updateCartItemQuantity(cartItemId: string, quantity: number): Promise<CustomerCart> {
  return request<CustomerCart>(`/customer/cart/items/${cartItemId}`, {
    method: 'PATCH',
    body: JSON.stringify({ quantity }),
  });
}

export async function removeCartItem(cartItemId: string): Promise<CustomerCart> {
  return request<CustomerCart>(`/customer/cart/items/${cartItemId}`, {
    method: 'DELETE',
  });
}

export async function clearCart(): Promise<CustomerCart> {
  return request<CustomerCart>('/customer/cart', {
    method: 'DELETE',
  });
}
