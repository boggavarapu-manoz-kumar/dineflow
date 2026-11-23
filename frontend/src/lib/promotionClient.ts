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

export type DiscountType = 'FIXED_AMOUNT' | 'PERCENTAGE';
export type PromotionScope = 'ORDER_LEVEL' | 'ITEM_SPECIFIC' | 'CATEGORY_SPECIFIC';

export interface PromotionResponse {
  id: string;
  tenantId: string;
  branchId?: string;
  name: string;
  description?: string;
  couponCode?: string;
  discountType: DiscountType;
  discountValue: number;
  scope: PromotionScope;
  minOrderValue: number;
  maxDiscountAmount?: number;
  startsAt: string;
  endsAt: string;
  usageLimit?: number;
  currentUsageCount: number;
  perCustomerLimit?: number;
  applyBeforeTax: boolean;
  combinable: boolean;
  active: boolean;
  expired: boolean;
  eligibleItemIds: string[];
  eligibleCategoryIds: string[];
  createdAt: string;
}

export interface CreatePromotionRequest {
  name: string;
  description?: string;
  branchId?: string;
  couponCode?: string;
  discountType: DiscountType;
  discountValue: number;
  scope?: PromotionScope;
  minOrderValue?: number;
  maxDiscountAmount?: number;
  startsAt: string;
  endsAt: string;
  usageLimit?: number;
  perCustomerLimit?: number;
  applyBeforeTax?: boolean;
  combinable?: boolean;
  active?: boolean;
}

export interface CouponApplicationResponse {
  valid: boolean;
  message: string;
  billId: string;
  couponCode?: string;
  promotionName?: string;
  subtotal: number;
  discountAmount: number;
  serviceChargeAmount: number;
  taxAmount: number;
  grandTotal: number;
  outstandingBalance: number;
  currencyCode: string;
  appliedBeforeTax: boolean;
}

export interface RedemptionResponse {
  id: string;
  promotionId: string;
  billId: string;
  billNumber: string;
  branchId?: string;
  customerSessionId?: string;
  couponCode?: string;
  promotionName: string;
  discountAmount: number;
  currencyCode: string;
  status: 'APPLIED' | 'REMOVED';
  actorType: string;
  redeemedAt: string;
  removedAt?: string;
}

export interface PromotionAnalyticsResponse {
  totalPromotions: number;
  activePromotions: number;
  expiredPromotions: number;
  totalRedemptions: number;
  totalDiscountGranted: number;
  currencyCode: string;
}

export const promotionClient = {
  list: (): Promise<PromotionResponse[]> => request<PromotionResponse[]>('/promotions'),

  create: (data: CreatePromotionRequest): Promise<PromotionResponse> =>
    request<PromotionResponse>('/promotions', {
      method: 'POST',
      body: JSON.stringify(data),
    }),

  update: (promotionId: string, data: Partial<CreatePromotionRequest>): Promise<PromotionResponse> =>
    request<PromotionResponse>(`/promotions/${promotionId}`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),

  toggleStatus: (promotionId: string, active: boolean): Promise<PromotionResponse> =>
    request<PromotionResponse>(`/promotions/${promotionId}/status?active=${active}`, {
      method: 'PATCH',
    }),

  getRedemptions: (promotionId: string): Promise<RedemptionResponse[]> =>
    request<RedemptionResponse[]>(`/promotions/${promotionId}/redemptions`),

  getAnalytics: (): Promise<PromotionAnalyticsResponse> =>
    request<PromotionAnalyticsResponse>('/promotions/analytics'),

  validateCoupon: (billId: string, couponCode: string): Promise<CouponApplicationResponse> =>
    request<CouponApplicationResponse>(`/promotions/bills/${billId}/validate`, {
      method: 'POST',
      body: JSON.stringify({ couponCode }),
    }),

  applyCoupon: (billId: string, couponCode: string): Promise<CouponApplicationResponse> =>
    request<CouponApplicationResponse>(`/promotions/bills/${billId}/apply`, {
      method: 'POST',
      body: JSON.stringify({ couponCode }),
    }),

  removeCoupon: (billId: string, couponCode?: string): Promise<CouponApplicationResponse> => {
    const query = couponCode ? `?couponCode=${encodeURIComponent(couponCode)}` : '';
    return request<CouponApplicationResponse>(`/promotions/bills/${billId}/remove${query}`, {
      method: 'POST',
    });
  },
};
