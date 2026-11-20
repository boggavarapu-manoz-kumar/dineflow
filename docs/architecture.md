# DineFlow Architecture Specification

## 1. Architectural Style & Boundaries
DineFlow is structured as a **modular monolith** with clean layer separation:
- **Presentation / Controller Layer**: Receives HTTP requests, executes request parameter validation, invokes application services, and returns strongly typed `ApiResponse<T>` or `ApiErrorResponse`. Does not contain domain business logic.
- **Application / Service Layer**: Orchestrates use cases, manages transactional boundaries (`@Transactional`), enforces tenant security constraints, and performs business operations.
- **Domain Layer**: Contains entities (`BaseEntity`, `TenantAwareEntity`) and core domain invariants. Entities are never exposed directly to public REST clients.
- **Persistence Layer**: Spring Data JPA repositories with explicit tenant query methods and JPA Specifications.
- **Cross-Cutting Concerns**: Global exception interception (`GlobalExceptionHandler`), CORS management, and thread-safe tenant context resolution (`TenantFilter` + `TenantContext`).

## 2. Multi-Tenant Architecture & Data Isolation
- **Tenant Context**: Backed by `InheritableThreadLocal<UUID>`, ensuring tenant context flows to worker threads during asynchronous tasks without manual passing.
- **Tenant Isolation Policy**: Shared PostgreSQL database using column discrimination (`tenant_id`). All tenant-owned records have non-nullable `tenant_id` foreign keys indexed for high-selectivity scans.
- **Tenant Resolution Strategy**:
  - `HeaderTenantResolver`: Extracts tenant ID from headers or host domain.
  - In Sprint 2, this will be integrated with verified JWT claims (e.g., hotel manager tokens) and signed QR code session tokens for customer requests.
  - Public endpoints (e.g., `/api/v1/health`) can execute without tenant context.

## 3. Technology Stack Alignment
- **Runtime**: Java 25 LTS (backward-compatible with Java 21+).
- **Framework**: Spring Boot 4.x / Spring Framework 7.x.
- **Security**: Spring Security 7.x configured in stateless session mode (`SessionCreationPolicy.STATELESS`).
- **Database Migrations**: Flyway Core + PostgreSQL database support.