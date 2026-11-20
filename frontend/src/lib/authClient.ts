const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  status: string;
  activeTenantId: string | null;
  role: string;
  permissions: string[];
}

export interface LoginResult {
  accessToken: string;
  expiresIn: number;
  user: UserProfile;
}

export interface GuestSessionResult {
  guestToken: string;
  sessionId: string;
  tenantId: string;
  tableNumber: string;
  customerName: string;
  expiresAt: string;
}

// In-Memory Access Token Holder (zero localStorage exposure)
let memoryAccessToken: string | null = null;

export const setAccessToken = (token: string | null) => {
  memoryAccessToken = token;
};

export const getAccessToken = (): string | null => {
  return memoryAccessToken;
};

export async function loginStaff(identifier: string, password: string, tenantId?: string): Promise<LoginResult> {
  const res = await fetch(`${API_BASE_URL}/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include', // receives __Host-DineFlow-RT HttpOnly cookie
    body: JSON.stringify({ identifier, password, tenantId: tenantId || null }),
  });

  const body = await res.json();
  if (!res.ok || !body.success) {
    throw new Error(body.message || 'Authentication failed. Please verify your credentials.');
  }

  setAccessToken(body.data.accessToken);
  return body.data;
}

export async function refreshAccessToken(): Promise<LoginResult> {
  const res = await fetch(`${API_BASE_URL}/auth/refresh`, {
    method: 'POST',
    credentials: 'include', // sends __Host-DineFlow-RT
  });

  const body = await res.json();
  if (!res.ok || !body.success) {
    setAccessToken(null);
    throw new Error(body.message || 'Session expired. Please log in again.');
  }

  setAccessToken(body.data.accessToken);
  return body.data;
}

export async function logoutStaff(): Promise<void> {
  try {
    await fetch(`${API_BASE_URL}/auth/logout`, {
      method: 'POST',
      credentials: 'include',
    });
  } finally {
    setAccessToken(null);
  }
}

export async function getMe(): Promise<UserProfile> {
  const token = getAccessToken();
  const res = await fetch(`${API_BASE_URL}/auth/me`, {
    headers: {
      Authorization: `Bearer ${token}`,
    },
    credentials: 'include',
  });

  const body = await res.json();
  if (!res.ok || !body.success) {
    throw new Error(body.message || 'Failed to fetch user profile');
  }

  return body.data;
}

export async function enterCustomerSession(
  name: string,
  mobileNumber: string,
  signedEntryToken: string
): Promise<GuestSessionResult> {
  const res = await fetch(`${API_BASE_URL}/customer-sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name, mobileNumber, signedEntryToken }),
  });

  const body = await res.json();
  if (!res.ok || !body.success) {
    throw new Error(body.message || 'Invalid or expired QR entry token. Please re-scan table QR code.');
  }

  setAccessToken(body.data.guestToken);
  return body.data;
}