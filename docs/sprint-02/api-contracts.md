# DineFlow Sprint 2: API Contracts Specification

## 1. Authentication Endpoints

### 1.1 Staff Login
`POST /api/v1/auth/login`
- **Request Body**:
```json
{
  "email": "owner@grandheritage.com",
  "password": "SecurePassword123!",
  "tenantSlug": "grand-heritage" // Optional: null for Super Admin
}
```
- **Response `200 OK`**:
```json
{
  "success": true,
  "message": "Authentication successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 900,
    "user": {
      "id": "c7198b1b-2612-4fe1-ba7e-ef984531816b",
      "email": "owner@grandheritage.com",
      "fullName": "Vikramaditya Rao",
      "activeTenant": {
        "id": "e4a8b792-6632-4d2c-9be2-4410a51c9d2f",
        "slug": "grand-heritage",
        "businessName": "The Grand Heritage"
      },
      "role": "HOTEL_OWNER",
      "permissions": ["menu:catalog:edit", "staff:users:manage", "..."]
    }
  },
  "timestamp": "2026-11-20T16:00:00Z"
}
```
- **Cookies Set**:
  - `__Host-DineFlow-RT`: Opaque rotating refresh token (`HttpOnly`, `Secure`, `SameSite=Strict`, `Path=/api/v1/auth`, `Max-Age=604800`).

---

### 1.2 Session Token Refresh
`POST /api/v1/auth/refresh`
- **Header**: `Cookie: __Host-DineFlow-RT=<token>`
- **Response `200 OK`**:
  - Returns new `accessToken` and rotates the `__Host-DineFlow-RT` cookie.
- **Response `401 Unauthorized`**:
  - Returns when token is expired, invalid, or already rotated (revoking token family).

---

### 1.3 Session Termination
`POST /api/v1/auth/logout`
- Revokes the current refresh session and clears the cookie with `Max-Age=0`.

`POST /api/v1/auth/logout-all`
- Revokes all active refresh sessions across all devices for the authenticated user.

---

### 1.4 Current Authenticated Principal
`GET /api/v1/auth/me`
- **Header**: `Authorization: Bearer <accessToken>`
- Returns user profile, active tenant, role, and authorized permissions.

---

## 2. Customer Guest Endpoints

### 2.1 Customer Guest Table Entry
`POST /api/v1/guest/entry`
- **Request Body**:
```json
{
  "tenantSlug": "grand-heritage",
  "tableNumber": "04",
  "entryToken": "signed-qr-entry-token-hash...",
  "guestName": "Rahul Sharma",
  "mobileNumber": "+91 98765 43210"
}
```
- **Response `200 OK`**:
```json
{
  "success": true,
  "message": "Welcome to The Grand Heritage",
  "data": {
    "guestToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "sessionId": "a1f9483c-1b77-4401-8e01-29bc57418921",
    "tableNumber": "04",
    "expiresAt": "2026-11-20T20:00:00Z"
  },
  "timestamp": "2026-11-20T16:00:00Z"
}
```

---

### 2.2 Customer Session Validation
`GET /api/v1/guest/session`
- **Header**: `Authorization: Bearer <guestToken>`
- Validates that the guest session is still active and returns table context.