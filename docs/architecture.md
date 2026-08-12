# RootTraceAI — Architecture

## High-Level Architecture

```
┌─────────────┐     ┌──────────────────────────────────────────────────────────┐
│   React UI  │────▶│                  Spring Boot Backend                     │
│  (Phase 10) │     │                                                          │
└─────────────┘     │  ┌─────────┐  ┌──────────┐  ┌────────────┐              │
                    │  │ REST    │  │ Service  │  │ Repository │              │
                    │  │ Control.│─▶│ Layer    │─▶│ Layer      │──┐           │
                    │  └─────────┘  └──────────┘  └────────────┘  │           │
                    │       │            │                         │           │
                    │       │       ┌────▼─────┐            ┌─────▼────────┐  │
                    │       │       │ AI       │            │ PostgreSQL   │  │
                    │       │       │ Services │            │ + pgvector   │  │
                    │       │       │(Phase 5) │            └──────────────┘  │
                    │       │       └──────────┘                              │
                    └───────│─────────────────────────────────────────────────┘
                            │
                    ┌───────▼───────┐
                    │  Swagger UI   │
                    │  /swagger-ui  │
                    └───────────────┘
```

## Package Structure (Domain-Oriented)

```
com.roottrace
├── RootTraceApplication.java
├── common/
│   ├── exception/     — Global exception handling, error responses
│   ├── audit/         — Audit event logging
│   └── config/        — JPA, CORS, and shared configuration
├── incident/          — Incident CRUD, DTOs, domain logic
├── user/              — Users, roles, authentication (Phase 2)
├── ai/                — AI infrastructure, abstractions, and health (Phase 3)
├── knowledge/         — Document upload, chunking, embeddings (Phase 3)
├── search/            — Hybrid search, RRF (Phase 4)
├── investigation/     — AI diagnosis, investigation plans (Phase 5-6)
├── integration/       — GitHub, log analysis (Phase 7)
└── postmortem/        — Postmortem generation (Phase 9)
```

## Key Design Decisions

1. **Domain packaging** — code organized by feature, not by layer
2. **DTOs everywhere** — JPA entities never exposed via REST
3. **Flyway migrations** — production-grade schema management, Hibernate validates only
4. **Soft deletion** — `deleted_at` column for audit trail
5. **pgvector** — PostgreSQL extension for vector similarity search (Phase 3+)
6. **Synchronous audit** — simple audit event logging in Phase 1
7. **OpenAPI/Swagger** — API documentation and testing built-in

## Database

- **PostgreSQL 16** with **pgvector** extension
- **Flyway** for migrations
- Key extensions: `vector`, `uuid-ossp`, `pg_trgm`
