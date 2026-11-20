# DineFlow Sprint Roadmap

## Sprint 1: Architectural Foundation (Complete)
- [x] Spring Boot 4 / Java 25 modular monolith foundation.
- [x] Multi-tenant context and resolver abstraction.
- [x] PostgreSQL & Flyway migrations with UUID & UTC timestamps.
- [x] React 19 + TypeScript + Vite + Tailwind CSS landing & application shell.
- [x] Unit & integration tests for backend and frontend.

## Sprint 2: Authentication & Tenant Onboarding
- [ ] Hotel staff authentication (JWT with Role-Based Access Control: Hotel Admin, Waiter, Kitchen).
- [ ] Anonymous QR customer table session token generation.
- [ ] Tenant self-service registration and table QR code generation module.

## Sprint 3: Menu & Real-Time Kitchen Display (KDS)
- [ ] Tenant menu category and food item catalog management.
- [ ] Customer cart and order placement engine.
- [ ] WebSocket / Server-Sent Events (SSE) kitchen display tickets.

## Sprint 4: Waiter Dispatch & In-App Customer Entertainment
- [ ] Waiter alert dispatch engine for ready orders.
- [ ] Lightweight HTML5 browser wait games for customers.
- [ ] Billing calculation with tax and service charges.