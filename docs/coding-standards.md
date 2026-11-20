# DineFlow Coding Standards

## Backend (Java / Spring Boot)
- **Strict Layer Separation**: Controller -> Service -> Repository. Controllers never query repositories directly.
- **Entities vs DTOs**: Never return JPA entities in controller responses; map to Java records or DTOs.
- **Dependency Injection**: Always use constructor injection. Do not use field injection (`@Autowired` on private fields).
- **Exceptions**: Throw domain-specific exceptions extending `BusinessException`.

## Frontend (React / TypeScript)
- **TypeScript Strict Mode**: No implicit `any`. Explicit typing for props and API payloads.
- **Styling**: Tailwind CSS utility classes; avoid inline styles.
- **Component Sizing**: Keep components under 250 lines. Extract reusable UI primitives into `components/ui`.