CREATE TABLE customer_cart (
    customer_id uuid PRIMARY KEY,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE customer_cart_line (
    customer_id uuid NOT NULL REFERENCES customer_cart(customer_id) ON DELETE CASCADE,
    kind varchar(8) NOT NULL CHECK (kind IN ('PRODUCT', 'PACK')),
    offer_id uuid NOT NULL,
    quantity bigint NOT NULL CHECK (quantity BETWEEN 1 AND 999),
    PRIMARY KEY (customer_id, kind, offer_id)
);
CREATE INDEX customer_cart_line_offer_idx ON customer_cart_line(kind, offer_id);

CREATE TABLE customer_cart_merge (
    customer_id uuid NOT NULL REFERENCES customer_cart(customer_id) ON DELETE CASCADE,
    merge_id uuid NOT NULL,
    fingerprint char(64) NOT NULL CHECK (fingerprint ~ '^[0-9a-f]{64}$'),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (customer_id, merge_id)
);

-- The account is owned by identity; access is by authenticated UUID via a port,
-- not a cross-module FK or a caller-supplied customer ID.
GRANT SELECT, INSERT, UPDATE, DELETE ON customer_cart, customer_cart_line,
    customer_cart_merge TO "${applicationUser}";
