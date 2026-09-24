-- Flyway creates and owns the application schema. No business tables yet.
REVOKE CREATE ON SCHEMA bricocomptoir FROM PUBLIC;
GRANT USAGE ON SCHEMA bricocomptoir TO "${applicationUser}";

-- Future tables belong to the migration role; the runtime role receives DML only.
ALTER DEFAULT PRIVILEGES IN SCHEMA bricocomptoir
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "${applicationUser}";
ALTER DEFAULT PRIVILEGES IN SCHEMA bricocomptoir
    GRANT USAGE, SELECT ON SEQUENCES TO "${applicationUser}";

COMMENT ON SCHEMA bricocomptoir IS 'BricoComptoir modular monolith';
