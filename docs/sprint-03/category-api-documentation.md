# DineFlow Menu Category Management API Documentation

## Overview
The Menu Category Management subsystem allows authenticated hotel/restaurant staff with `MENU_MANAGE` or `MENU_VIEW` authorities to organize their catalog. All operations are strictly multi-tenant isolated using the authenticated principal's `TenantContext`.

---

## Authorization & Roles

| Endpoint | Method | Required Authority | Allowed Roles |
| :--- | :--- | :--- | :--- |
| `/api/v1/menu/categories` | `POST` | `MENU_MANAGE` | `HOTEL_OWNER`, `HOTEL_ADMIN` |
| `/api/v1/menu/categories` | `GET` | `MENU_VIEW` or `MENU_MANAGE` | `HOTEL_OWNER`, `HOTEL_ADMIN`, `BRANCH_MANAGER`, `WAITER`, `KITCHEN_STAFF` |
| `/api/v1/menu/categories/paged` | `GET` | `MENU_VIEW` or `MENU_MANAGE` | `HOTEL_OWNER`, `HOTEL_ADMIN`, `BRANCH_MANAGER`, `WAITER`, `KITCHEN_STAFF` |
| `/api/v1/menu/categories/{id}` | `GET` | `MENU_VIEW` or `MENU_MANAGE` | `HOTEL_OWNER`, `HOTEL_ADMIN`, `BRANCH_MANAGER`, `WAITER`, `KITCHEN_STAFF` |
| `/api/v1/menu/categories/{id}` | `PUT` | `MENU_MANAGE` | `HOTEL_OWNER`, `HOTEL_ADMIN` |
| `/api/v1/menu/categories/{id}/status` | `PATCH` | `MENU_MANAGE` | `HOTEL_OWNER`, `HOTEL_ADMIN` |
| `/api/v1/menu/categories/reorder` | `POST` | `MENU_MANAGE` | `HOTEL_OWNER`, `HOTEL_ADMIN` |
| `/api/v1/menu/categories/{id}` | `DELETE` | `MENU_MANAGE` | `HOTEL_OWNER`, `HOTEL_ADMIN` |

*Note: Guest customers (`CUSTOMER_GUEST`) are strictly prohibited from accessing these administrative endpoints.*

---

## Endpoints Specification

### 1. Create Category
- **URL**: `POST /api/v1/menu/categories`
- **Headers**: `Authorization: Bearer <token>`
- **Request Body**:
```json
{
  "name": "Appetizers & Starters",
  "description": "Crispy starters, finger foods, and sharing platters",
  "displayOrder": 1,
  "isActive": true,
  "iconName": "utensils",
  "imageUrl": "https://cdn.dineflow.io/images/categories/appetizers.jpg"
}
```
- **Response** (`201 Created`):
```json
{
  "success": true,
  "message": "Category created successfully",
  "data": {
    "id": "2093934a-d15a-40f0-b2bb-98568f2507ca",
    "tenantId": "e4a8b792-6632-4d2c-9be2-4410a51c9d2f",
    "name": "Appetizers & Starters",
    "description": "Crispy starters, finger foods, and sharing platters",
    "displayOrder": 1,
    "isActive": true,
    "iconName": "utensils",
    "imageUrl": "https://cdn.dineflow.io/images/categories/appetizers.jpg",
    "createdAt": "2026-11-21T13:38:46.774Z",
    "updatedAt": "2026-11-21T13:38:46.774Z"
  },
  "timestamp": "2026-11-21T13:38:46.774Z"
}
```

### 2. List All Categories
- **URL**: `GET /api/v1/menu/categories?activeOnly=true`
- **Response** (`200 OK`):
```json
{
  "success": true,
  "message": "Categories retrieved successfully",
  "data": [
    {
      "id": "2093934a-d15a-40f0-b2bb-98568f2507ca",
      "tenantId": "e4a8b792-6632-4d2c-9be2-4410a51c9d2f",
      "name": "Appetizers & Starters",
      "description": "Crispy starters, finger foods, and sharing platters",
      "displayOrder": 0,
      "isActive": true,
      "iconName": "utensils",
      "imageUrl": null,
      "createdAt": "2026-11-21T13:38:46.774Z",
      "updatedAt": "2026-11-21T13:38:46.774Z"
    }
  ],
  "timestamp": "2026-11-21T13:38:46.774Z"
}
```

### 3. Reorder Categories
- **URL**: `POST /api/v1/menu/categories/reorder`
- **Request Body**:
```json
{
  "categoryIds": [
    "3eac3133-e33a-453e-9b95-51d20596ea0b",
    "e63311be-60dd-40d8-90a9-2bc8b175ffab",
    "c62dd799-de50-42d6-a8f8-0e72dc3789a0"
  ]
}
```

### 4. Toggle Category Status
- **URL**: `PATCH /api/v1/menu/categories/{id}/status`
- **Request Body**:
```json
{
  "isActive": false
}
```

---

## Error Scenarios

- **409 Conflict (`DUPLICATE_CATEGORY_NAME`)**: Thrown when a category name duplicates an existing category within the same tenant (whitespace-trimmed, case-insensitive normalization).
- **404 Not Found (`RESOURCE_NOT_FOUND`)**: Thrown when the category ID does not exist or belongs to another tenant.
- **403 Forbidden**: Thrown when the user does not have `MENU_MANAGE` authority.
- **400 Bad Request (`VALIDATION_FAILED`)**: Thrown when validation constraints (e.g., blank name, length limits) fail.
