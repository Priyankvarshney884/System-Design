-- ╔══════════════════════════════════════════════════════════════╗
-- ║       PostgreSQL Initialization Script                       ║
-- ╚══════════════════════════════════════════════════════════════╝
-- Runs automatically on first container start (docker-entrypoint-initdb.d/)
-- Hibernate (ddl-auto=update) will create tables — this just sets up extensions

-- UUID generation support
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Full-text search (supplementary to Elasticsearch for simple cases)
CREATE EXTENSION IF NOT EXISTS "pg_trgm";

-- Useful for JSONB columns (product metadata in PostgreSQL fallback)
CREATE EXTENSION IF NOT EXISTS "btree_gin";

-- Separate schema per bounded context (optional — good for large teams)
-- SYSTEM DESIGN: schema-per-domain gives clear ownership boundaries
CREATE SCHEMA IF NOT EXISTS user_domain;
CREATE SCHEMA IF NOT EXISTS order_domain;
CREATE SCHEMA IF NOT EXISTS payment_domain;
