CREATE TABLE catalog_product_view_daily (
    product_id uuid NOT NULL REFERENCES catalog_product(id) ON DELETE CASCADE,
    day date NOT NULL,
    views bigint NOT NULL DEFAULT 0 CHECK (views >= 0),
    PRIMARY KEY (product_id, day)
);
CREATE INDEX catalog_product_view_daily_day_idx ON catalog_product_view_daily(day, product_id);

-- Daily aggregates only: no visitor, session, IP address or raw event history.
GRANT SELECT, INSERT, UPDATE, DELETE ON catalog_product_view_daily TO "${applicationUser}";
