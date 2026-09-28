CREATE TABLE pack_offer (
    id uuid PRIMARY KEY,
    code varchar(80) NOT NULL UNIQUE,
    name varchar(180) NOT NULL,
    slogan varchar(180) NOT NULL DEFAULT '',
    guide varchar(4000) NOT NULL DEFAULT '',
    status varchar(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    demo boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pack_offer_code_format CHECK (code ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);
CREATE INDEX pack_offer_status_name_idx ON pack_offer(status, lower(name), id);

CREATE TABLE pack_variant (
    id uuid PRIMARY KEY,
    pack_id uuid NOT NULL REFERENCES pack_offer(id) ON DELETE RESTRICT,
    code varchar(80) NOT NULL,
    label varchar(120) NOT NULL,
    price_tnd numeric(12,3) NOT NULL CHECK (price_tnd > 0),
    status varchar(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pack_variant_code_unique UNIQUE (pack_id, code),
    CONSTRAINT pack_variant_code_format CHECK (code ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);
CREATE INDEX pack_variant_pack_status_idx ON pack_variant(pack_id, status, id);

-- No FK to catalog tables: modules validate UUID references through public ports.
-- A pack has no stock row: every balance comes from inventory_stock per SKU.
CREATE TABLE pack_component (
    pack_variant_id uuid NOT NULL REFERENCES pack_variant(id) ON DELETE RESTRICT,
    catalog_variant_id uuid NOT NULL,
    quantity bigint NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (pack_variant_id, catalog_variant_id)
);
CREATE INDEX pack_component_catalog_variant_idx ON pack_component(catalog_variant_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON pack_offer, pack_variant, pack_component TO "${applicationUser}";
