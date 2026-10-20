-- ═══════════════════════════════════════════════════════════════════════════
-- Activos duplicados — fase 1 (19-oct-2026). Ver docs/PLAN_ACTIVOS_DUPLICADOS.md
--
-- Mismo bien físico registrado dos veces en el VSIAF con dos códigos (error del revalúo).
-- El código que sobra va a la oficina de duplicados de su predio; no se corrige nada.
--
-- Correr ANTES de desplegar el código (psql -v ON_ERROR_STOP=1 -f ...). Es idempotente.
-- hbm2ddl crearía casi todo solo al arrancar, pero NO los índices parciales (únicos con
-- WHERE), que son los que impiden dos oficinas de duplicados por predio o dos registros
-- pendientes del mismo activo.
-- ═══════════════════════════════════════════════════════════════════════════
BEGIN;

-- 1. Marca de la oficina y del responsable de duplicados ─────────────────────
ALTER TABLE oficina     ADD COLUMN IF NOT EXISTS es_duplicados boolean NOT NULL DEFAULT false;
ALTER TABLE responsable ADD COLUMN IF NOT EXISTS es_duplicados boolean NOT NULL DEFAULT false;

-- Una oficina de duplicados vigente por predio.
CREATE UNIQUE INDEX IF NOT EXISTS uk_oficina_duplicados_predio
    ON oficina (id_predio) WHERE es_duplicados AND _estado = 'ACTIVO';
-- La misma persona una sola vez en la oficina de duplicados.
CREATE UNIQUE INDEX IF NOT EXISTS uk_resp_duplicados_persona
    ON responsable (id_oficina, id_persona) WHERE es_duplicados;

-- 2. Notificaciones ──────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notificacion_duplicado (
    id_notificacion            bigserial PRIMARY KEY,
    numero                     varchar(20)  NOT NULL,
    token                      varchar(40)  NOT NULL,
    id_persona                 bigint       REFERENCES persona (id_persona),
    persona_nombre             varchar(160),
    fecha_emision              timestamp    NOT NULL,
    usuario_emision            varchar(60),
    contenido                  text         NOT NULL,
    hash_contenido             varchar(64)  NOT NULL,
    estado_notificacion        varchar(15)  NOT NULL,
    motivo_anulacion           text,
    fecha_anulacion            timestamp,
    usuario_anulacion          varchar(60),
    _fecha_registro            timestamp(6),
    _registro_id_usuario       bigint,
    _fecha_modificacion        timestamp(6),
    _modificacion_id_usuario   bigint,
    _estado                    varchar(255),
    CONSTRAINT uk_not_dup_numero UNIQUE (numero),
    CONSTRAINT uk_not_dup_token  UNIQUE (token)
);
CREATE INDEX IF NOT EXISTS idx_not_dup_persona ON notificacion_duplicado (id_persona);
CREATE INDEX IF NOT EXISTS idx_not_dup_fecha   ON notificacion_duplicado (fecha_emision);

-- 3. Casos ───────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS activo_duplicado (
    id_duplicado               bigserial PRIMARY KEY,
    id_activo                  bigint       NOT NULL REFERENCES activo (id_activo),
    id_activo_principal        bigint       NOT NULL REFERENCES activo (id_activo),
    motivo                     text         NOT NULL,
    estado_duplicado           varchar(20)  NOT NULL,
    id_responsable_origen      bigint       REFERENCES responsable (id_responsable),
    id_oficina_origen          bigint       REFERENCES oficina (id_oficina),
    id_persona                 bigint       REFERENCES persona (id_persona),
    id_responsable_custodia    bigint       REFERENCES responsable (id_responsable),
    estado_envio               varchar(20),
    mensaje_envio              text,
    intento_envio              timestamp,
    fecha_envio                timestamp,
    usuario_registro           varchar(60),
    id_notificacion            bigint       REFERENCES notificacion_duplicado (id_notificacion),
    id_hallazgo_faltante       bigint       REFERENCES hallazgo_inventario (id_hallazgo),
    faltante_estado_envio      varchar(20),
    regularizado               boolean      NOT NULL DEFAULT false,
    fecha_retiro               timestamp,
    usuario_retiro             varchar(60),
    justificacion_retiro       text,
    id_responsable_retiro      bigint       REFERENCES responsable (id_responsable),
    _fecha_registro            timestamp(6),
    _registro_id_usuario       bigint,
    _fecha_modificacion        timestamp(6),
    _modificacion_id_usuario   bigint,
    _estado                    varchar(255)
);
CREATE INDEX IF NOT EXISTS idx_dup_activo    ON activo_duplicado (id_activo);
CREATE INDEX IF NOT EXISTS idx_dup_principal ON activo_duplicado (id_activo_principal);
CREATE INDEX IF NOT EXISTS idx_dup_estado    ON activo_duplicado (estado_duplicado);
CREATE INDEX IF NOT EXISTS idx_dup_envio     ON activo_duplicado (estado_envio);
-- Un activo tiene a lo sumo un registro de duplicado pendiente.
CREATE UNIQUE INDEX IF NOT EXISTS uk_dup_activo_pendiente
    ON activo_duplicado (id_activo) WHERE estado_duplicado IN ('EN_PROCESO', 'EN_DUPLICADOS');

-- 4. Comprobación ────────────────────────────────────────────────────────────
SELECT (SELECT count(*) FROM pg_indexes
         WHERE indexname IN ('uk_oficina_duplicados_predio', 'uk_resp_duplicados_persona', 'uk_dup_activo_pendiente')) AS indices_3,
       (SELECT count(*) FROM information_schema.tables
         WHERE table_name IN ('activo_duplicado', 'notificacion_duplicado')) AS tablas_2;

COMMIT;
