CREATE TABLE sales_order (
    id uuid PRIMARY KEY,
    owner_scope varchar(38) NOT NULL CHECK (owner_scope ~ '^[CG]:[0-9a-f-]{36}$'),
    customer_id uuid,
    status varchar(12) NOT NULL CHECK (status IN ('CONFIRMED','PREPARING','SHIPPED','DELIVERED','CANCELLED')),
    payment_method varchar(16) NOT NULL DEFAULT 'CASH_ON_DELIVERY' CHECK (payment_method = 'CASH_ON_DELIVERY'),
    snapshot jsonb NOT NULL CHECK (jsonb_typeof(snapshot) = 'object'
        AND snapshot ?& ARRAY['items','address','subtotalTnd','deliveryTnd','totalTnd','quoteHash']
        AND jsonb_typeof(snapshot->'items') = 'array'
        AND jsonb_array_length(snapshot->'items') BETWEEN 1 AND 100),
    subtotal_tnd numeric(18,3) NOT NULL CHECK (subtotal_tnd > 0),
    delivery_tnd numeric(18,3) NOT NULL CHECK (delivery_tnd >= 0),
    total_tnd numeric(18,3) NOT NULL CHECK (total_tnd = subtotal_tnd + delivery_tnd),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((customer_id IS NULL AND owner_scope LIKE 'G:%')
        OR (customer_id IS NOT NULL AND owner_scope = 'C:' || customer_id::text))
);
CREATE INDEX sales_order_owner_time_idx ON sales_order(owner_scope, created_at DESC, id);
CREATE INDEX sales_order_status_time_idx ON sales_order(status, created_at DESC, id);

CREATE TABLE sales_order_request (
    owner_scope varchar(38) NOT NULL,
    request_key uuid NOT NULL,
    fingerprint char(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    order_id uuid REFERENCES sales_order(id),
    PRIMARY KEY (owner_scope, request_key)
);
CREATE TABLE sales_order_event (
    id uuid PRIMARY KEY,
    order_id uuid NOT NULL REFERENCES sales_order(id),
    status varchar(12) NOT NULL CHECK (status IN ('CONFIRMED','PREPARING','SHIPPED','DELIVERED','CANCELLED')),
    actor_scope varchar(38) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (order_id, status)
);
GRANT SELECT, INSERT ON sales_order, sales_order_request, sales_order_event TO "${applicationUser}";
-- V1 default privileges grant table-wide DML: revoke before granting column updates.
REVOKE UPDATE, DELETE ON sales_order, sales_order_request, sales_order_event FROM "${applicationUser}";
GRANT UPDATE (status, updated_at) ON sales_order TO "${applicationUser}";
GRANT UPDATE (order_id) ON sales_order_request TO "${applicationUser}";
-- No UPDATE/DELETE of immutable snapshots, addresses, totals or event history.
