# DineFlow API Conventions

## 1. URL Path & Versioning Conventions
- Base URI: `/api/v1/`
- Resource paths: Nouns in lowercase plural format (e.g., `/api/v1/tenants`, `/api/v1/orders`).
- Public / Health check: `/api/v1/health`

## 2. Envelope Format
All successful responses conform to `ApiResponse<T>`:
```json
{
  "success": true,
  "message": "Operation completed successfully",
  "data": { ... },
  "timestamp": "2026-11-20T15:35:37.654Z"
}
```

Error responses conform to `ApiErrorResponse`:
```json
{
  "success": false,
  "errorCode": "VALIDATION_FAILED",
  "message": "Request validation failed",
  "errors": [
    { "field": "contactEmail", "message": "must be a well-formed email address" }
  ],
  "path": "/api/v1/...",
  "timestamp": "2026-11-20T15:35:37.654Z"
}
```

## 3. Headers
- `X-Tenant-ID`: UUID of the active hotel/restaurant tenant.