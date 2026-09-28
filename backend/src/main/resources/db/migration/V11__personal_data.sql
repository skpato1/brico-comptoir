-- Delivery contacts may be rectified/removed; commercial snapshots stay immutable.
CREATE TABLE sales_order_contact (
    order_id uuid PRIMARY KEY REFERENCES sales_order(id),
    address jsonb NOT NULL CHECK (jsonb_typeof(address)='object'),
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE sales_privacy_closed_scope (
    owner_scope varchar(38) PRIMARY KEY CHECK(owner_scope ~ '^G:[0-9a-f-]{36}$'),
    closed_at timestamptz NOT NULL DEFAULT now()
);
GRANT SELECT,INSERT ON sales_privacy_closed_scope TO "${applicationUser}";
REVOKE UPDATE,DELETE ON sales_privacy_closed_scope FROM "${applicationUser}";
INSERT INTO sales_order_contact(order_id,address) SELECT id,snapshot->'address' FROM sales_order;
UPDATE sales_order SET snapshot=jsonb_set(snapshot,'{address}',
    '{"recipient":"Coordonnées retirées","phone":"","street":"","city":"","postalCode":"","governorate":"","country":"TN","email":null}'::jsonb);
CREATE TABLE sales_order_private_archive (
    order_id uuid PRIMARY KEY REFERENCES sales_order(id),
    ciphertext bytea NOT NULL,
    retain_until timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
-- Runtime can deposit an archive, but cannot read/decrypt it over a normal API.
REVOKE ALL ON sales_order_private_archive FROM "${applicationUser}";
GRANT INSERT ON sales_order_private_archive TO "${applicationUser}";
ALTER TABLE sales_order ADD COLUMN legal_hold boolean NOT NULL DEFAULT false;
GRANT UPDATE(legal_hold) ON sales_order TO "${applicationUser}";
CREATE INDEX sales_contact_retention_idx ON sales_order(status,updated_at,id);
CREATE INDEX sales_archive_retention_idx ON sales_order_private_archive(retain_until,order_id);
-- Narrow erasure function: never changes financial snapshots or held archives.
CREATE FUNCTION privacy_purge_archives(cutoff timestamptz) RETURNS integer
LANGUAGE plpgsql SECURITY DEFINER SET search_path=bricocomptoir,pg_temp AS $$
DECLARE affected integer;
BEGIN
    DELETE FROM sales_order_private_archive a USING sales_order o
        WHERE a.order_id=o.id AND a.retain_until<=least(cutoff,now()) AND NOT o.legal_hold;
    GET DIAGNOSTICS affected=ROW_COUNT;
    RETURN affected;
END $$;
REVOKE ALL ON FUNCTION privacy_purge_archives(timestamptz) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION privacy_purge_archives(timestamptz) TO "${applicationUser}";
-- Targeted access for an audited, reauthenticated ADMIN rights request.
CREATE FUNCTION privacy_read_archive(target uuid) RETURNS bytea
LANGUAGE sql SECURITY DEFINER SET search_path=bricocomptoir,pg_temp AS $$
    SELECT ciphertext FROM sales_order_private_archive WHERE order_id=target;
$$;
REVOKE ALL ON FUNCTION privacy_read_archive(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION privacy_read_archive(uuid) TO "${applicationUser}";

CREATE TABLE privacy_consent (
    account_id uuid PRIMARY KEY,
    marketing boolean NOT NULL DEFAULT false,
    notice_version varchar(40) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE privacy_audit (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subject_scope varchar(38) NOT NULL CHECK(subject_scope ~ '^[CG]:[0-9a-f-]{36}$'),
    actor_id uuid,
    action varchar(40) NOT NULL,
    notice_version varchar(40),
    target_order_id uuid,
    reason_code varchar(40),
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX privacy_audit_subject_idx ON privacy_audit(subject_scope,created_at,id);
CREATE INDEX privacy_audit_retention_idx ON privacy_audit(created_at,id);
CREATE TABLE privacy_email_change (
    account_id uuid PRIMARY KEY,
    new_email varchar(254) NOT NULL,
    token_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL
);
CREATE INDEX privacy_email_expiry_idx ON privacy_email_change(expires_at);
CREATE INDEX identity_reset_expiry_idx ON identity_password_reset(expires_at);
GRANT SELECT,INSERT,UPDATE,DELETE ON sales_order_contact,privacy_consent,privacy_email_change,privacy_audit TO "${applicationUser}";
GRANT USAGE,SELECT ON SEQUENCE privacy_audit_id_seq TO "${applicationUser}";
-- Pseudonymous actor references instead of staff email addresses.
UPDATE inventory_movement SET actor_name='legacy-staff' WHERE actor_name LIKE '%@%';
UPDATE content_home SET updated_by='legacy-staff' WHERE updated_by LIKE '%@%';
UPDATE sales_delivery_settings SET updated_by='legacy-staff' WHERE updated_by LIKE '%@%';
