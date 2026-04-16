# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.4.4 / Java 21 fixed-assets management system for UAP university. It bridges a PostgreSQL database with a legacy DBF (dBASE) file system used by VSIAF (the university's existing asset tracking system). The two systems synchronize in near-real-time.

## Common Commands

```bash
# Build
./mvnw clean package

# Run (serves on port 9696)
./mvnw spring-boot:run

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=SistemasActivosFijosUapApplicationTests
```

## Architecture

The application follows a layered MVC architecture with Thymeleaf for server-side rendering.

```
Controller → Service (IService/ServiceImpl) → DAO/Repository → Entity
```

**Package root:** `com.usic.SistemasActivosFijosUAP`

### Key layers

| Package | Role |
|---|---|
| `controller/` | Spring MVC controllers (39 total). REST controllers are in `controller/rest/`. |
| `service/IService/` | Service interfaces |
| `service/ServiceImpl/` | Service implementations |
| `model/entity/` | JPA entities (40 total) — Activo is the main aggregate |
| `model/dto/` | DTOs used between controller and service layers |
| `model/dao/` | Data access objects for complex queries |
| `config/` | Spring Security, audit, datasource, MVC, and sync configuration |
| `service/interoperabilidad/` | DBF ↔ PostgreSQL bridge (JavaDbfService, registroDbf/) |
| `config/sincronizacion/` | Scheduled sync and SSE notification push |

### DBF Interoperability Layer

This is the most distinctive part of the codebase. The legacy system writes DBF files to two network mounts:

- `/mnt/dbfwin` — master DBF files (activos, oficinas, responsables, auxiliares)
- `/mnt/vsiaf_transferencias` — transfer requests from legacy system

`JavaDbfService` reads these files using the `com.linuxense.javadbf` library. Classes in `service/interoperabilidad/registroDbf/` write back to DBF format. The sync is driven by:

- `SyncScheduler` — polls for transfer DBFs every 20 s (configurable), full sync every 6 hours
- `SincronizacionService` — applies changes in dependency order (GrupoContable → Oficina → Responsable → Activo)
- SSE events are pushed to the frontend via `/ws/**` and `/topic/**` endpoints when sync events occur

### Synchronization order matters

When writing or changing sync logic, entities must be processed in this dependency order to avoid FK violations:

1. GrupoContable
2. Oficina
3. Responsable / Persona
4. Activo
5. Asignacion / Transferencia

### Security Configuration

`SeguridadConfig` disables CSRF and grants `permitAll()` to most routes. Session-based authentication with BCrypt password encoding. Role-based checks are performed at the service layer, not at the HTTP layer.

Default startup users are created in `SistemasActivosFijosUapApplication` (roles: SUPER USUARIO, ADMINISTRADOR).

## Configuration Notes

Credentials and API keys are currently stored in `application.properties` (not externalized). When modifying datasource or AI settings, edit that file directly. Key properties:

| Property | Value |
|---|---|
| `server.port` | 9696 |
| `spring.datasource.url` | PostgreSQL at `virtual.uap.edu.bo:5432/bd_a4` |
| `legacy.dbf.path` | `/mnt/dbfwin` |
| `legacy.dbf.transferencias.path` | `/mnt/vsiaf_transferencias` |
| `spring.jpa.properties.hibernate.hbm2ddl.auto` | `update` (schema auto-migrates on startup) |

## Key Dependencies

- **Apache POI** — Excel import/export
- **JavaDBF** — Read/write legacy dBASE files
- **iTextPDF** — PDF report generation
- **Spring AI (OpenAI)** — Asset description analysis via `AiDescripcionService`
- **Google Drive API** — Document storage integration
- **HikariCP** — Connection pooling (max 10 connections)

## Thymeleaf Templates

Templates live in `src/main/resources/templates/`. Shared layout fragments are in `templates/layout/`. Domain-specific views mirror the controller structure (activo/, responsable/, usuario/, etc.).

## Testing

Only a basic context-load test exists. There is no established pattern for unit or integration tests yet.
