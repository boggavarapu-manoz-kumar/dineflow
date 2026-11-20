# DineFlow Sprint 2: Security Architecture Specification

## 1. Authentication & Token Lifecycle

### Dual-Token Architecture
- **Access Token (JWT)**:
  - Lifetime: **15 minutes**.
  - Payload: `sub` (User UUID), `tid` (Active Tenant UUID or null), `mid` (Membership UUID), `roles` (List), `perms` (List of authority strings), `typ` (`STAFF` or `GUEST`).
  - Storage: In-memory only on browser clients. Never stored in `localStorage` or `sessionStorage` (mitigating XSS extraction).
- **Refresh Token**:
  - Lifetime: **7 days** (with idle expiration).
  - Structure: Opaque 256-bit cryptographically secure pseudorandom token (CSPRNG).
  - Storage: Stored hashed via SHA-256 in `refresh_sessions`. Delivered to browsers exclusively via `HttpOnly`, `Secure`, `SameSite=Strict` cookies (`__Host-DineFlow-RT`).
  - **Token Rotation**: Every refresh generates a new cryptographically random token and updates the token record.
  - **Reuse Detection & Family Revocation**: Each refresh token belongs to a `family_id`. If a previously rotated token is presented (indicating token theft or replay), the entire token family is revoked immediately, terminating all active sessions for that principal.

## 2. Password Hashing & Account Protection
- **Algorithm**: BCrypt with work factor 12 (or Argon2id).
- **Brute Force Protection**: Account lockout for 15 minutes after 5 consecutive failed authentication attempts.
- **Audit Logging**: Every authentication attempt (success, bad credentials, lockout) records an immutable entry in `login_audit_logs`.

## 3. Customer Guest Security
- Customers do not have passwords or traditional persistent accounts.
- Entry requires a **Signed Table Entry Token** embedded in QR code URLs:
  - Form: `HMAC-SHA256(hotel_id + table_id + table_number + secret_salt + timestamp)`
  - Prevents attackers from spoofing arbitrary table numbers or generating fake orders for unseated tables.
- Customer mobile numbers are treated as guest identifiers and unverified until SMS OTP is triggered (optional post-order verification).
- Guest sessions are time-boxed to 4 hours and automatically terminated upon bill closure or checkout.

## 4. CSRF & Cookie Security
- Refresh endpoints (`/api/v1/auth/refresh`, `/api/v1/auth/logout`) utilize `SameSite=Strict` and cookie path restrictions (`/api/v1/auth`).
- State-changing cookie requests enforce custom header validation (`X-Requested-With: XMLHttpRequest` or custom anti-CSRF token).