CREATE TABLE content_best_sellers (
    id integer PRIMARY KEY CHECK (id = 1),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(254)
);
INSERT INTO content_best_sellers(id) VALUES (1);

CREATE TABLE content_best_seller_product (
    position integer PRIMARY KEY CHECK (position BETWEEN 0 AND 2),
    product_id uuid NOT NULL UNIQUE
);

-- Product ownership stays in catalog; visibility is checked through its public contract.
GRANT SELECT, UPDATE ON content_best_sellers TO "${applicationUser}";
GRANT SELECT, INSERT, DELETE ON content_best_seller_product TO "${applicationUser}";
