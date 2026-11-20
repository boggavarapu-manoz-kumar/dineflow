# DineFlow Security Test Execution Report

## Verified Automated Security Test Scenarios (12 Tests Passing)
1. **Unauthenticated API Access**: Blocked with `401 Unauthorized`.
2. **Cross-Tenant Access Rejection**: Waiter A verified for Hotel A (`hasTenantAccess = true`) and blocked for Hotel B (`hasTenantAccess = false`).
3. **Super Admin Platform Oversight**: Verified global access (`tenant_id = null`) across all hotel tenants.
4. **Customer Session Ownership Isolation**: Customer A can only access `sessionId = A` and is denied access to `sessionId = B`.
5. **Disabled Account Rejection**: Accounts marked `DISABLED` or `LOCKED` fail login with `403 Forbidden`.
6. **Rotating Refresh Token Expiration & Burning**: Used refresh tokens are immediately revoked.
7. **Refresh Token Reuse Detection**: Presenting a burned refresh token triggers immediate `family_id` revocation, terminating all active sessions for the user.
8. **Cryptographic QR Entry Token Verification**: Valid HMAC tokens issue guest sessions; forged tokens are rejected with `401 Unauthorized`.