# DineFlow Database Design Specification

## 1. Strategy & Engine
- **RDBMS**: PostgreSQL 16+ (tested in local dev with PostgreSQL dialect and fallback H2 PostgreSQL mode).
- **Migration Engine**: Flyway migrations residing in `db/migration`.
- **Primary Key Strategy**: Universally Unique Identifiers (`UUID`) using `gen_random_uuid()` / pgcrypto to prevent ID enumeration attacks across tenants.
- **Timestamps**: All timestamps stored with explicit timezone (`TIMESTAMP WITH TIME ZONE`) in UTC.

## 2. Core Schema Entities (Sprint 1)
- `tenants`: Stores subscribing hotels/restaurants, unique URL slugs, subscription tiers, and lifecycle statuses.
- `hotel_profiles`: Configuration metadata per tenant, including contact details, currency, tax rates (GST/VAT), service charge percentages, and custom branding palette.
- `audit_logs`: Append-only security audit log recording tenant administrative actions, IP addresses, actors, and affected resources.

## 3. Indexing Principles
- All foreign keys and tenant discriminators (`tenant_id`) are indexed to ensure queries never do unindexed full table scans.
- High-frequency lookup fields like `slug` and `status` have dedicated unique and B-Tree indexes.