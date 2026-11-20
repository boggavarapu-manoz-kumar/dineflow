# DineFlow Frontend-Backend Contract Validation

## 1. Endpoint & Contract Mapping
- `POST /api/v1/auth/login`: Accepts `LoginRequest`, returns `LoginResponse` + sets `__Host-DineFlow-RT` `HttpOnly` cookie.
- `POST /api/v1/auth/refresh`: Consumes `__Host-DineFlow-RT`, rotates cookie, returns fresh in-memory access token.
- `POST /api/v1/auth/logout`: Revokes refresh session, clears cookie with `Max-Age=0`.
- `POST /api/v1/customer-sessions`: Accepts `CustomerSessionRequest`, verifies HMAC signed token, returns guest JWT.

## 2. In-Memory Token Guarantee
Neither customer guest tokens nor staff access tokens are written to `localStorage` or `sessionStorage`, protecting DineFlow against XSS token harvesting.