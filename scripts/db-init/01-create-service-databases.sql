-- Per-service logical database creation
-- Runs once on first Postgres container initialisation via docker-entrypoint-initdb.d
-- Each database name matches the SPRING_DATASOURCE_URL the start script constructs:
--   jdbc:postgresql://localhost:5432/<service>
-- Flyway owns all schemas/tables within each database — do NOT add schemas or tables here.

CREATE DATABASE identity;
CREATE DATABASE catalog;
CREATE DATABASE wallet;
CREATE DATABASE payment;
CREATE DATABASE contact;
CREATE DATABASE messaging;
CREATE DATABASE notification;
CREATE DATABASE admin;
