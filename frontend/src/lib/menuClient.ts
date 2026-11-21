import { getAccessToken } from '@/lib/authClient';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export type DietaryClassification = 'VEGETARIAN' | 'NON_VEGETARIAN' | 'VEGAN' | 'CONTAINS_EGG' | 'JAIN';

export interface Category {
  id: string;
  tenantId: string;
  name: string;
  description?: string;
  displayOrder: number;
  isActive: boolean;
  iconName?: string;
  imageUrl?: string;
  createdAt: string;
  updatedAt: string;
}

export interface MenuItemSummary {
  id: string;
  categoryId: string;
  categoryName: string;
  name: string;
  shortDescription?: string;
  basePrice: number;
  dietaryClassification: DietaryClassification;
  isActive: boolean;
  isAvailable: boolean;
  displayOrder: number;
  primaryImageUrl?: string;
}

export interface MenuVariant {
  id: string;
  name: string;
  description?: string;
  price: number;
  displayOrder: number;
  isActive: boolean;
  isAvailable: boolean;
}

export interface MenuOption {
  id: string;
  name: string;
  description?: string;
  additionalPrice: number;
  displayOrder: number;
  isActive: boolean;
  isAvailable: boolean;
}

export interface MenuOptionGroup {
  id: string;
  name: string;
  description?: string;
  minSelections: number;
  maxSelections: number;
  isRequired: boolean;
  displayOrder: number;
  isActive: boolean;
  options: MenuOption[];
}

export interface MenuItemImage {
  id: string;
  imageUrl: string;
  storageKey: string;
  fileName: string;
  fileSizeBytes: number;
  contentType: string;
  displayOrder: number;
  isPrimary: boolean;
}

export interface MenuItemDetail {
  id: string;
  tenantId: string;
  categoryId: string;
  categoryName: string;
  name: string;
  shortDescription?: string;
  detailedDescription?: string;
  basePrice: number;
  dietaryClassification: DietaryClassification;
  preparationTimeMinutes: number;
  isActive: boolean;
  isAvailable: boolean;
  displayOrder: number;
  images: MenuItemImage[];
  variants: MenuVariant[];
  optionGroups: MenuOptionGroup[];
  createdAt: string;
  updatedAt: string;
}

export interface BranchConfig {
  id: string;
  branchId: string;
  branchName: string;
  menuItemId: string;
  menuItemName: string;
  priceOverride?: number;
  isAvailable: boolean;
  isVisible: boolean;
  updatedAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
}

async function request<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
  const token = getAccessToken();
  const headers = new Headers(options.headers || {});
  
  if (token) {
    headers.set('Authorization', `Bearer ${token}`);
  }
  if (!headers.has('Content-Type') && !(options.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json');
  }

  const res = await fetch(`${API_BASE_URL}${endpoint}`, {
    ...options,
    headers,
  });

  const body = await res.json();
  if (!res.ok || !body.success) {
    throw new Error(body.message || `Request failed with status ${res.status}`);
  }

  return body.data;
}

// Category APIs
export async function getCategories(activeOnly?: boolean): Promise<Category[]> {
  const query = activeOnly !== undefined ? `?activeOnly=${activeOnly}` : '';
  return request<Category[]>(`/menu/categories${query}`);
}

export async function createCategory(data: Partial<Category>): Promise<Category> {
  return request<Category>('/menu/categories', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateCategory(id: string, data: Partial<Category>): Promise<Category> {
  return request<Category>(`/menu/categories/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function toggleCategoryStatus(id: string, isActive: boolean): Promise<Category> {
  return request<Category>(`/menu/categories/${id}/status`, {
    method: 'PATCH',
    body: JSON.stringify({ isActive }),
  });
}

export async function reorderCategories(categoryIds: string[]): Promise<Category[]> {
  return request<Category[]>('/menu/categories/reorder', {
    method: 'POST',
    body: JSON.stringify({ categoryIds }),
  });
}

export async function deleteCategory(id: string): Promise<void> {
  return request<void>(`/menu/categories/${id}`, {
    method: 'DELETE',
  });
}

// Menu Item APIs
export async function getMenuItems(categoryId?: string, page = 0, size = 50): Promise<PageResponse<MenuItemSummary>> {
  const catParam = categoryId ? `&categoryId=${categoryId}` : '';
  return request<PageResponse<MenuItemSummary>>(`/menu/items?page=${page}&size=${size}${catParam}`);
}

export async function getMenuItemById(id: string): Promise<MenuItemDetail> {
  return request<MenuItemDetail>(`/menu/items/${id}`);
}

export async function createMenuItem(data: {
  categoryId: string;
  name: string;
  shortDescription?: string;
  detailedDescription?: string;
  basePrice: number;
  dietaryClassification: DietaryClassification;
  preparationTimeMinutes?: number;
  isActive?: boolean;
  isAvailable?: boolean;
  displayOrder?: number;
}): Promise<MenuItemDetail> {
  return request<MenuItemDetail>('/menu/items', {
    method: 'POST',
    body: JSON.stringify(data),
  });
}

export async function updateMenuItem(id: string, data: {
  categoryId: string;
  name: string;
  shortDescription?: string;
  detailedDescription?: string;
  basePrice: number;
  dietaryClassification: DietaryClassification;
  preparationTimeMinutes?: number;
  isActive?: boolean;
  isAvailable?: boolean;
  displayOrder?: number;
}): Promise<MenuItemDetail> {
  return request<MenuItemDetail>(`/menu/items/${id}`, {
    method: 'PUT',
    body: JSON.stringify(data),
  });
}

export async function deleteMenuItem(id: string): Promise<void> {
  return request<void>(`/menu/items/${id}`, {
    method: 'DELETE',
  });
}

// Variants
export async function addVariant(menuItemId: string, variant: {
  name: string;
  description?: string;
  price: number;
  displayOrder?: number;
  isActive?: boolean;
  isAvailable?: boolean;
}): Promise<MenuVariant> {
  return request<MenuVariant>(`/menu/items/${menuItemId}/variants`, {
    method: 'POST',
    body: JSON.stringify(variant),
  });
}

export async function deleteVariant(menuItemId: string, variantId: string): Promise<void> {
  return request<void>(`/menu/items/${menuItemId}/variants/${variantId}`, {
    method: 'DELETE',
  });
}

// Option Groups & Options
export async function createOptionGroup(group: {
  name: string;
  description?: string;
  minSelections: number;
  maxSelections: number;
  isRequired?: boolean;
  displayOrder?: number;
  isActive?: boolean;
}): Promise<MenuOptionGroup> {
  return request<MenuOptionGroup>('/menu/items/option-groups', {
    method: 'POST',
    body: JSON.stringify(group),
  });
}

export async function addOptionToGroup(groupId: string, option: {
  name: string;
  description?: string;
  additionalPrice: number;
  displayOrder?: number;
  isActive?: boolean;
  isAvailable?: boolean;
}): Promise<MenuOption> {
  return request<MenuOption>(`/menu/items/option-groups/${groupId}/options`, {
    method: 'POST',
    body: JSON.stringify(option),
  });
}

export async function linkOptionGroupToItem(menuItemId: string, groupId: string): Promise<void> {
  return request<void>(`/menu/items/${menuItemId}/option-groups/${groupId}`, {
    method: 'POST',
  });
}

// Branch Config
export async function setBranchOverride(menuItemId: string, branchId: string, override: {
  priceOverride?: number;
  isAvailable: boolean;
  isVisible?: boolean;
}): Promise<BranchConfig> {
  return request<BranchConfig>(`/menu/items/${menuItemId}/branches/${branchId}`, {
    method: 'PUT',
    body: JSON.stringify(override),
  });
}

// Images
export async function uploadMenuItemImage(menuItemId: string, file: File, isPrimary = false): Promise<MenuItemImage> {
  const formData = new FormData();
  formData.append('file', file);
  formData.append('isPrimary', String(isPrimary));

  return request<MenuItemImage>(`/menu/items/${menuItemId}/images`, {
    method: 'POST',
    body: formData,
  });
}

// Customer Public Menu API
export interface CustomerCategory {
  id: string;
  name: string;
  description?: string;
  displayOrder: number;
  iconName?: string;
  items: CustomerMenuItem[];
}

export interface CustomerMenuItem {
  id: string;
  name: string;
  shortDescription?: string;
  detailedDescription?: string;
  effectivePrice: number;
  originalBasePrice: number;
  isBranchPriceOverridden: boolean;
  dietaryClassification: DietaryClassification;
  preparationTimeMinutes: number;
  isAvailable: boolean;
  primaryImageUrl?: string;
  imageUrls: string[];
  variants: CustomerVariant[];
  optionGroups: CustomerOptionGroup[];
}

export interface CustomerVariant {
  id: string;
  name: string;
  description?: string;
  price: number;
  isAvailable: boolean;
}

export interface CustomerOptionGroup {
  id: string;
  name: string;
  description?: string;
  minSelections: number;
  maxSelections: number;
  isRequired: boolean;
  options: CustomerOption[];
}

export interface CustomerOption {
  id: string;
  name: string;
  description?: string;
  additionalPrice: number;
  isAvailable: boolean;
}

export interface CustomerMenuData {
  tenantId: string;
  restaurantName: string;
  tableNumber: string;
  currencyCode: string;
  taxPercentage: number;
  serviceChargePercentage: number;
  categories: CustomerCategory[];
}

export async function getCustomerMenu(): Promise<CustomerMenuData> {
  return request<CustomerMenuData>('/customer/menu');
}
