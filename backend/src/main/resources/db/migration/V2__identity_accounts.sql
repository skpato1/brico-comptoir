CREATE TABLE identity_account (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE,
    password_hash varchar(255) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT identity_email_normalized CHECK (email = lower(trim(email)))
);

CREATE TABLE identity_role (
    account_id uuid NOT NULL REFERENCES identity_account(id) ON DELETE CASCADE,
    role varchar(32) NOT NULL CHECK (role IN ('CUSTOMER', 'CATALOG_MANAGER', 'ORDER_MANAGER', 'ADMIN')),
    PRIMARY KEY (account_id, role)
);

CREATE INDEX identity_role_admin_idx ON identity_role (account_id) WHERE role = 'ADMIN';

CREATE TABLE identity_password_reset (
    token_hash char(64) PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES identity_account(id) ON DELETE CASCADE,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX identity_password_reset_account_idx ON identity_password_reset (account_id);

CREATE TABLE identity_access_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    actor_id uuid REFERENCES identity_account(id),
    target_id uuid NOT NULL REFERENCES identity_account(id),
    action varchar(40) NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA bricocomptoir TO "${applicationUser}";
GRANT USAGE ON ALL SEQUENCES IN SCHEMA bricocomptoir TO "${applicationUser}";
