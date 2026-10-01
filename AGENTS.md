# AGENTS.md — SistemasActivosFijosUAP

Java 21 / Spring Boot 3.4.4. Server port **9696** (not 8080). Package root: `com.usic.SistemasActivosFijosUAP`.

## Commands (Windows → `mvnw.cmd`)

| Task | Command |
|---|---|
| Build | `mvnw.cmd clean package` |
| Run | `mvnw.cmd spring-boot:run` |
| All tests | `mvnw.cmd test` |
| Single test | `mvnw.cmd test -Dtest=ClassName` |

The context-load test (`SistemasActivosFijosUapApplicationTests`) requires reaching `virtual.uap.edu.bo:5432` (PostgreSQL). There is no H2/test DB profile. Two tests run without Spring/DB: `WordActaSmokeTest`, `WordInternoTransferenciaServiceTest`.

## Package traps

| What you'd guess | What it actually is |
|---|---|
| `component/` | **`componet/`** (misspelled!) |
| `service/` | `model/IService/` + `model/ServiceImpl/` |
| DBF bridge under `model/` | **`interoperabilidad/`** (top-level) |
| One `JavaDbfService` | There are **two**: `interoperabilidad/JavaDbfService` (live) and `model/service/interoperabilidad/JavaDbfService` (stray). Use the top-level one. |

Other packages: `controller/rest/` for REST controllers, `model/service/` for standalone services (PDF, Excel, AI, sync helpers), `config/sincronizacion/` for sync orchestration, `legacy/` for legacy-DBF UI controllers.

## Security

- CSRF **disabled**. Most routes (`/administracion/**`, `/api/**`, `/legacy/**`, etc.) are `permitAll()`.
- Role-based authorization is enforced at the **service layer**, not via URL matchers.
- Auth: session-based with `BCryptPasswordEncoder`. Login page is `/`.
- Default users created on startup: `admin1` / `usuario&25`, `admin2` / `admin&25` (roles `SUPER USUARIO`, `ADMINISTRADOR`).
- Mobile API (`/api/movil/**`) uses JWT (`movil.jwt.secret` in `application.properties`).

## Sync architecture

- `@EnableScheduling` on `SyncScheduler`. Full resync every 6h via cron (`0 0 */6 * * *`).
- Change detection via `DbfChangeDetectorService` — polls file size + timestamp. General tables every 20s (`sync.poll.interval.ms`), `ACTUAL.DBF` every 60s (`sync.poll.activo.interval.ms`).
- **Sync dependency order** (FK-safe): entidad → predio → grupoContable → organismoFinanciero → auxiliar → oficina → responsable → activo
- DBF write mode (`legacy.dbf.write.mode`): `cola` (queue orders for VFPOLEDB worker, preserves indexes) vs `bytes` (raw append, breaks indexes).
- **SSE, not WebSocket**. `/ws/**` and `/topic/**` in `SeguridadConfig` are stale leftovers from an abandoned approach. Active SSE endpoints at `/api/eventos/stream` (global) and `/api/eventos/sse/usuario` (per-user).
- Scheduling pool size = 6 (`spring.task.scheduling.pool.size=6`) to prevent CIFS hangs from blocking all scheduled tasks.

## DBF bridge

Two network mounts: `legacy.dbf.path` (/mnt/dbfwin) for master DBFs, `legacy.dbf.transferencias.path` (/mnt/vsiaf_transferencias) for transfers. File-based access via `com.github.albfernandez:javadbf` (no JDBC-over-DBF). Readers in `interoperabilidad/JavaDbfService`, writers in `interoperabilidad/registroDbf/*DbfWriterService`.

## Londra integration

External UAP web system for transfer approvals. Server-side proxy at `/api/uap/obtenerDatos` forwards to `virtual.uap.edu.bo:7174/api/londraPost/v1`. Outbound callback configured via `londra.callback.url` + `londra.callback.api-key`. Entities: `TransferenciaLondra`, `TransferenciaDetalleLondra`, `TransferenciaCabecera`, `TransferenciaAccion`.

## Key config (in `application.properties`, not externalized)

| Property | Value |
|---|---|
| `spring.jpa.hibernate.hbm2ddl.auto` | `update` (auto-migrates) |
| DB batch size | 500, batch inserts/updates enabled |
| `spring.ai.openai.api-key` | Plain-text OpenAI key |
| `sync.poll.interval.ms` | 20000 |
| `sync.poll.activo.interval.ms` | 60000 |
| `spring.datasource.url` | `jdbc:postgresql://virtual.uap.edu.bo:5432/bd_a3` (batch rewrite enabled) |
| `legacy.dbf.write.mode` | `cola` |

## Mobile app (Capacitor + Vue 3 + Ionic)

Located in `mobile/`. Commands: `npm run dev`, `npm run build`, `npm run android`. Connects to `/api/movil/**` (JWT auth). Requires backend running on port 9696.

## Miscellaneous

- **53 JPA entities**, all under `model/entity/`. `Activo` is the main aggregate.
- Thymeleaf templates in `src/main/resources/templates/`. Layout fragments in `templates/layout/`.
- Notifications: `Notificacion` entity + daily cleanup at 3 AM (`cron = 0 0 3 * * *`).
- Audit trail via `HistorialActivo` entity.
- `@ValidarUsuarioAutenticado` annotation used on authenticated controller methods.
- `screenshots/`, `pdfs/`, `tools/`, `docs/` — static assets, not part of build.
- `scripts/sql/` — migration/utility SQL scripts (not auto-run).
- PostgreSQL only JDBC datasource (HikariCP, max 10). HXTT DBF datasource commented out/abandoned.