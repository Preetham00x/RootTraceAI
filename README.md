# RootTraceAI

**AI-Powered Incident Intelligence & Investigation Platform**

RootTraceAI helps software engineers investigate production incidents by correlating incident descriptions, logs, historical incidents, technical documentation, GitHub code changes, and other operational evidence. It uses AI to identify probable root causes, provide evidence-backed recommendations, generate investigation plans, and create postmortems.

---

## Tech Stack

| Layer          | Technology                                                    |
|----------------|---------------------------------------------------------------|
| Backend        | Java 21, Spring Boot 3.4.1, Spring Security, Spring Data JPA  |
| AI             | Spring AI, Google Gemini, RAG, pgvector                       |
| Database       | PostgreSQL 16 + pgvector                                      |
| Frontend       | React, TypeScript                                             |
| Infrastructure | Docker, Docker Compose                                        |

## Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- **Docker Desktop** (REQUIRED for local development database with `pgvector`)

### 1. Start PostgreSQL

```bash
cp .env.example .env
# Edit .env with your values
docker compose up -d
```

### 2. Run the Backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### 3. Run Tests

```bash
cd backend
mvn clean test
```

### 4. API Documentation

Once the application is running, visit:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/v3/api-docs

---

## Project Structure

```
RootTraceAI/
├── backend/           # Spring Boot application
│   └── src/main/java/com/roottrace/
│       ├── common/    # Shared: exceptions, config, audit
│       ├── incident/  # Incident domain
│       └── ...        # Future modules
├── frontend/          # React application (Phase 10)
├── docs/              # Architecture documentation
├── docker-compose.yml # Local infrastructure
└── .env.example       # Environment variable template
```

## Development Phases

| Phase  | Description                  | Status       |
|--------|------------------------------|--------------|
| 1      | Foundation + Incident CRUD   | In Progress  |
| 2      | Authentication & RBAC        | Planned      |
| 3      | Knowledge Base + Embeddings  | Planned      |
| 4      | Hybrid Search                | Planned      |
| 5      | AI Diagnosis                 | Planned      |
| 6      | Advanced Investigation       | Planned      |
| 7      | Integrations                 | Planned      |
| 8      | Agentic Investigation        | Planned      |
| 9      | Postmortems + AI Evaluation  | Planned      |
| 10     | Frontend                     | Planned      |
| 11     | Docker + Observability       | Planned      |

---

## License

Proprietary — All rights reserved.
