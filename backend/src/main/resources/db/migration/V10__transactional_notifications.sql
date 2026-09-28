CREATE TABLE notification_mail_outbox (
    id uuid PRIMARY KEY,
    dedup_key varchar(180) NOT NULL UNIQUE,
    reset_scope char(64),
    payload bytea,
    state varchar(12) NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING','PROCESSING','SENT','FAILED','CANCELLED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 5),
    next_attempt_at timestamptz NOT NULL DEFAULT now(),
    lease_token uuid,
    lease_until timestamptz,
    expires_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    sent_at timestamptz,
    last_error varchar(40),
    CHECK (state <> 'PROCESSING' OR (lease_token IS NOT NULL AND lease_until IS NOT NULL)),
    CHECK (state IN ('SENT','CANCELLED') OR payload IS NOT NULL)
);
CREATE INDEX notification_mail_due_idx ON notification_mail_outbox(next_attempt_at,created_at,id)
    WHERE state IN ('PENDING','PROCESSING');
CREATE INDEX notification_mail_reset_idx ON notification_mail_outbox(reset_scope)
    WHERE state IN ('PENDING','PROCESSING','FAILED');
CREATE INDEX notification_mail_admin_idx ON notification_mail_outbox(created_at DESC,id);

CREATE TABLE notification_event_cursor (
    id integer PRIMARY KEY CHECK (id=1),
    last_id bigint NOT NULL CHECK (last_id>=0)
);
INSERT INTO notification_event_cursor VALUES (1,0);
CREATE TABLE notification_order_event (
    id bigint PRIMARY KEY CHECK (id>0),
    order_id uuid NOT NULL UNIQUE,
    type varchar(30) NOT NULL CHECK (type='ORDER_CREATED'),
    created_at timestamptz NOT NULL DEFAULT now()
);
-- No foreign key or join across module-owned tables.
REVOKE DELETE ON notification_mail_outbox FROM "${applicationUser}";
REVOKE INSERT,DELETE ON notification_event_cursor FROM "${applicationUser}";
REVOKE UPDATE,DELETE ON notification_order_event FROM "${applicationUser}";
GRANT SELECT,INSERT,UPDATE ON notification_mail_outbox TO "${applicationUser}";
GRANT SELECT,UPDATE(last_id) ON notification_event_cursor TO "${applicationUser}";
GRANT SELECT,INSERT ON notification_order_event TO "${applicationUser}";
