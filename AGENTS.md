# AGENTS.md — SistemasActivosFijosUAP

Java 21 / Spring Boot 3.4.4. Server port **9696** (not 8080).

## Commands (Windows → `mvnw.cmd`)

| Task | Command |
|---|---|
| Build | `mvnw.cmd clean package` |
| Run | `mvnw.cmd spring-boot:run` |
| All tests | `mvnw.cmd test` |
| Single test | `mvnw.cmd test -Dtest=ClassName` |

The context-load test requires reaching `virtual.uap.edu.bo:5432`. There is no H2/test DB profile. One test (`WordActaSmokeTest`) runs without Spring/DB.

## Package traps

| What you'd guess | What it actually is |
|---|---|
| `component/` | **`componet/`** (misspelled!) |
| `service/` | `model/IService/` + `model/ServiceImpl/` |
| DBF bridge under `model/` | **`interoperabilidad/`** (top-level) |
| One `JavaDbfService` | There are **two**: `interoperabilidad/JavaDbfService` (live) and `model/service/interoperabilidad/JavaDbfService` (stray). Use the top-level one. |

Other packages: `controller/rest/` for REST controllers, `model/service/` for standalone services (PDF, Excel, AI, sync helpers).

## Security

- CSRF **disabled**. Most routes (`/administracion/**`, `/api/**`, `/legacy/**`, etc.) are `permitAll()`.
- Role-based authorization is enforced at the **service layer**, not via URL matchers.
- Auth: session-based with `BCryptPasswordEncoder`. Login page is `/`.
- Default users created on startup: `admin1` / `usuario&25`, `admin2` / `admin&25`.

## Sync architecture

- `@EnableScheduling` on `SyncScheduler`. Full resync every 6h via cron.
- Change detection via `DbfChangeDetectorService` — polls file size + timestamp. General tables every 20s, `ACTUAL.DBF` every 60s.
- **Sync dependency order** (FK-safe): entidad → predio → grupoContable → organismoFinanciero → auxiliar → oficina → responsable → activo
- DBF write mode (`legacy.dbf.write.mode`): `cola` (queue orders for VFPOLEDB worker, preserves indexes) vs `bytes` (raw append, breaks indexes).
- **SSE, not WebSocket**. `/ws/**` and `/topic/**` in `SeguridadConfig` are stale leftovers from an abandoned approach. Active SSE endpoints at `/api/eventos/stream` (global) and `/api/eventos/sse/usuario` (per-user).

## DBF bridge

Two network mounts: `legacy.dbf.path` (/mnt/dbfwin) for master DBFs, `legacy.dbf.transferencias.path` for transfers. File-based access via `com.github.albfernandez:javadbf` (no JDBC-over-DBF). Readers in `interoperabilidad/JavaDbfService`, writers in `interoperabilidad/registroDbf/*DbfWriterService`.

## Londra integration

External UAP web system for transfer approvals. Server-side proxy at `/api/uap/obtenerDatos` forwards to `virtual.uap.edu.bo:7174/api/londraPost/v1`. Outbound callback configured via `londra.callback.url` + `londra.callback.api-key`.

## Key config (in `application.properties`, not externalized)

| Property | Value |
|---|---|
| `spring.jpa.hibernate.hbm2ddl.auto` | `update` (auto-migrates) |
| DB batch size | 500, batch inserts/updates enabled |
| `spring.ai.openai.api-key` | Plain-text OpenAI key |
| `sync.poll.interval.ms` | 20000 |
| `sync.poll.activo.interval.ms` | 60000 |

## Miscellaneous

- **46 JPA entities**, all under `model/entity/`. `Activo` is the main aggregate.
- Thymeleaf templates in `src/main/resources/templates/`. Layout fragments in `templates/layout/`.
- Notifications: `Notificacion` entity + daily cleanup at 3 AM (`cron = 0 0 3 * * *`).
- Audit trail via `HistorialActivo` entity.
- `@ValidarUsuarioAutenticado` annotation used on authenticated controller methods.
- `screenshots/`, `pdfs/`, `tools/`, `docs/` — static assets, not part of build.