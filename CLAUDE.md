# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.4.4 / Java 21 fixed-assets management system for UAP university. It bridges a PostgreSQL database with a legacy DBF (dBASE) file system used by VSIAF (the university's existing asset-tracking system), and integrates with **Londra**, an external UAP web system, for transfer approvals. The systems synchronize in near-real-time.

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

On Windows use `mvnw.cmd` instead of `./mvnw`.

## Architecture

Layered MVC architecture with Thymeleaf for server-side rendering.

```
Controller → Service (IService/ServiceImpl or model/service) → DAO/Repository → Entity
```

**Package root:** `com.usic.SistemasActivosFijosUAP`

### Key layers

| Package | Role |
|---|---|
| `controller/` | Spring MVC controllers (40 total). REST controllers are in `controller/rest/` (3); the rest return Thymeleaf views. |
| `model/IService/` | Service interfaces |
| `model/ServiceImpl/` | Service implementations for the interfaces above |
| `model/service/` | Standalone concrete services (PDF generation, Excel import, AI, Drive upload, sync helpers) — many have no `IService` interface |
| `model/dao/` | Spring Data JPA repositories for complex/custom queries |
| `model/repository/` | Additional Spring Data repositories |
| `model/entity/` | JPA entities (44 total) — `Activo` is the main aggregate |
| `model/dto/` | DTOs between controller and service layers (DBF DTOs under `model/dto/interoperabilidad/`) |
| `config/` | Spring Security, datasource, MVC, audit, sync, and exception-handling config |
| `interoperabilidad/` | DBF ↔ PostgreSQL bridge (top-level package — note: **not** under `model/` or `service/`) |
| `config/sincronizacion/` | Scheduled sync orchestration and SSE push |
| `componet/` | Async config, DBF change detection, SSE emitter registry (note the misspelled package name) |

> Caution: package names are inconsistent. Service interfaces/impls live under `model/`, not `service/`. The DBF bridge lives in the top-level `interoperabilidad/` package, but a stray duplicate `JavaDbfService` also exists at `model/service/interoperabilidad/`. Confirm the actual path before importing.

### DBF Interoperability Layer

The most distinctive part of the codebase. The legacy VSIAF system reads/writes DBF files on two network mounts:

- `legacy.dbf.path` = `/mnt/dbfwin` — master DBF files (activos, oficinas, responsables, auxiliares, etc.)
- `legacy.dbf.transferencias.path` = `/mnt/vsiaf_transferencias` — transfer requests from the legacy system

DBF access is **file-based only**, via the `com.github.albfernandez:javadbf` library — there is no JDBC-over-DBF datasource (the HXTT/`DbfDataSourceConfig` approach is commented out and abandoned). PostgreSQL is the only live JDBC datasource (`PrimaryDataSourceConfig`, HikariCP, also exposed as `pgJdbcTemplate`).

- `interoperabilidad/JavaDbfService` — **reads** DBF files into `*Dbf` DTOs
- `interoperabilidad/registroDbf/*DbfWriterService` — **write** changes back to DBF format

### Synchronization

Sync is driven by scheduled tasks (`@EnableScheduling` on `SyncScheduler`):

- `config/sincronizacion/SyncScheduler` — full backup sync every 6 hours (`cron = 0 0 */6 * * *`) and polls for pending transfers every 20 s (`sync.poll.interval.ms`). On new transfers it broadcasts an SSE event and triggers persistent notifications.
- `config/sincronizacion/SyncOrchestrator` — `sincronizarConDependencias(tabla, force)` applies changes in dependency order.
- `componet/DbfChangeDetectorService` — `@Scheduled` change detection: general tables every 20 s, `activo` every 60 s (`sync.poll.activo.interval.ms`).
- `componet/SseEmitterRegistry` — manages SSE emitters and broadcasts (global + per-user/per-role).

There is **no** `SincronizacionService` class (older docs referenced one).

#### Synchronization order matters

When writing or changing sync logic, entities must be processed in dependency order to avoid FK violations. The actual order used by `SyncScheduler` is:

1. entidad
2. predio
3. grupoContable
4. organismoFinanciero
5. auxiliar
6. oficina
7. responsable
8. activo

(Asignacion / Transferencia depend on the above.)

#### Server-Sent Events (SSE), not WebSocket

Real-time push uses SSE (`SseEmitter`), not STOMP/WebSocket. Endpoints are on `SseController` under `/api/eventos`:

- `GET /api/eventos/stream` (`text/event-stream`) — global stream
- `GET /api/eventos/sse/usuario` — per-user stream
- `GET /api/eventos/clientes-conectados`

(The `/ws/**` and `/topic/**` paths in `SeguridadConfig` are leftover allowlist entries from an abandoned WebSocket approach and are not active SSE endpoints.)

### Londra integration (active work area)

Recent development centers on **Londra**, UAP's external web system, for asset-transfer workflows:

- `controller/activo/TransferenciaLondraController` (`/administracion/transferenciasLondra/**`) — approve / reject / observe transfers, list pending, action history.
- `config/UapProxyController` (`/api/uap/obtenerDatos`) — server-side proxy to `virtual.uap.edu.bo:7174/api/londraPost/v1`, keeping the upstream API key out of the frontend.
- Outbound callback to Londra on transfer events: `londra.callback.url` + `londra.callback.api-key` in `application.properties`.
- Entities: `TransferenciaLondra`, `TransferenciaDetalleLondra`, `TransferenciaCabecera`, `TransferenciaAccion`; DAOs `ITransferenciaLondraDao`, `ITransferenciaDetalleLondraDao`; service `TransferenciaLondraService` / `ITransferenciaLondraService`.
- `TransferenciasNotificadorService` creates persistent `Notificacion` records and pushes targeted SSE when new transfers arrive.

### Notifications

`Notificacion` entity + `NotificacionController` + `NotificacionServiceImpl` provide persistent in-app notifications. `NotificacionServiceImpl` runs a daily cleanup (`cron = 0 0 3 * * *`). New transfers generate notifications via `TransferenciasNotificadorService` (see above).

### Security Configuration

`SeguridadConfig` uses session-based auth with `BCryptPasswordEncoder` and **CSRF disabled**. Most application routes (`/administracion/**`, `/asignacion/**`, `/reportes/**`, `/api/**`, `/legacy/**`, etc.) are `permitAll()`; everything else is `authenticated()`. Login page is `/`. Because HTTP-layer authorization is largely open, **role-based authorization is enforced at the service layer**, not via URL matchers — keep access checks there when adding features.

Default startup users (roles `SUPER USUARIO`, `ADMINISTRADOR`) are created in `SistemasActivosFijosUapApplication`.

## Configuration Notes

Credentials and API keys are stored directly in `application.properties` (not externalized). When changing datasource, AI, or Londra settings, edit that file. Key properties:

| Property | Value |
|---|---|
| `server.port` | 9696 |
| `spring.datasource.url` | PostgreSQL at `virtual.uap.edu.bo:5432/bd_a4` (batch-rewrite enabled) |
| `spring.jpa.properties.hibernate.hbm2ddl.auto` | `update` (schema auto-migrates on startup) |
| `legacy.dbf.path` | `/mnt/dbfwin` |
| `legacy.dbf.transferencias.path` | `/mnt/vsiaf_transferencias` |
| `sync.poll.interval.ms` | 20000 (transfer/change polling) |
| `sync.poll.activo.interval.ms` | 60000 (activo change polling) |
| `spring.ai.openai.api-key` | OpenAI key (Spring AI) |
| `londra.callback.url` / `londra.callback.api-key` | Londra outbound callback |

## Key Dependencies

- **JavaDBF** (`com.github.albfernandez`) — read/write legacy dBASE files
- **Apache POI** — Excel import/export
- **iTextPDF** — PDF report generation (`model/service/Pdf*Service`)
- **Spring AI (OpenAI)** — asset-description analysis via `AiDescripcionService`
- **Google Drive API** — document storage via `DriveUploader`
- **Spring WebFlux `WebClient`** — outbound calls to Londra (proxy)
- **HikariCP** — connection pooling (max 10)

## Thymeleaf Templates

Templates live in `src/main/resources/templates/`. Shared layout fragments are in `templates/layout/`. Domain views mirror the controller structure (`activo/`, `responsable/`, `usuario/`, etc.).

## Testing

Only a basic context-load test exists (`SistemasActivosFijosUapApplicationTests`). There is no established pattern for unit or integration tests yet. Note that the context-load test requires reaching the configured PostgreSQL host.
