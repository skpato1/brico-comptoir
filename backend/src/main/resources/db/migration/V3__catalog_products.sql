CREATE EXTENSION IF NOT EXISTS pg_trgm WITH SCHEMA bricocomptoir;

CREATE TABLE catalog_category (
    id uuid PRIMARY KEY,
    parent_id uuid REFERENCES catalog_category(id) ON DELETE RESTRICT,
    slug varchar(80) NOT NULL UNIQUE,
    name varchar(120) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT catalog_category_not_self_parent CHECK (parent_id IS DISTINCT FROM id),
    CONSTRAINT catalog_category_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);
CREATE INDEX catalog_category_parent_idx ON catalog_category(parent_id);

-- The check is at the database boundary too: a second writer cannot create a cycle.
CREATE FUNCTION catalog_reject_category_cycle() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF NEW.parent_id IS NOT NULL AND EXISTS (
        WITH RECURSIVE ancestors(id, parent_id) AS (
            SELECT id, parent_id FROM catalog_category WHERE id = NEW.parent_id
            UNION ALL
            SELECT c.id, c.parent_id FROM catalog_category c JOIN ancestors a ON c.id = a.parent_id
        ) SELECT 1 FROM ancestors WHERE id = NEW.id
    ) THEN
        RAISE EXCEPTION 'Category cycle' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER catalog_category_cycle BEFORE INSERT OR UPDATE OF parent_id ON catalog_category
    FOR EACH ROW EXECUTE FUNCTION catalog_reject_category_cycle();

CREATE TABLE catalog_brand (
    id uuid PRIMARY KEY,
    slug varchar(80) NOT NULL UNIQUE,
    name varchar(120) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT catalog_brand_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$')
);

CREATE TABLE catalog_product (
    id uuid PRIMARY KEY,
    category_id uuid NOT NULL REFERENCES catalog_category(id) ON DELETE RESTRICT,
    brand_id uuid REFERENCES catalog_brand(id) ON DELETE RESTRICT,
    name varchar(180) NOT NULL,
    description varchar(2000) NOT NULL DEFAULT '',
    characteristics jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED')),
    demo boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT catalog_product_characteristics_object CHECK (jsonb_typeof(characteristics) = 'object')
);
CREATE INDEX catalog_product_category_idx ON catalog_product(category_id, status);
CREATE INDEX catalog_product_brand_idx ON catalog_product(brand_id, status);
CREATE INDEX catalog_product_published_name_idx ON catalog_product(lower(name), id) WHERE status = 'PUBLISHED';
CREATE INDEX catalog_product_name_search_idx ON catalog_product USING gin (lower(name) gin_trgm_ops);
CREATE INDEX catalog_product_description_search_idx ON catalog_product USING gin (lower(description) gin_trgm_ops);
CREATE INDEX catalog_product_created_idx ON catalog_product(created_at DESC, id);

CREATE TABLE catalog_variant (
    id uuid PRIMARY KEY,
    product_id uuid NOT NULL REFERENCES catalog_product(id) ON DELETE RESTRICT,
    sku varchar(64) NOT NULL UNIQUE,
    label varchar(120) NOT NULL,
    unit varchar(40) NOT NULL,
    options jsonb NOT NULL DEFAULT '{}'::jsonb,
    price_tnd numeric(12,3) NOT NULL CHECK (price_tnd > 0),
    status varchar(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','PUBLISHED')),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT catalog_variant_options_object CHECK (jsonb_typeof(options) = 'object'),
    CONSTRAINT catalog_variant_sku_format CHECK (sku ~ '^[A-Z0-9][A-Z0-9_-]*$')
);
CREATE INDEX catalog_variant_product_idx ON catalog_variant(product_id, status);
CREATE INDEX catalog_variant_sku_search_idx ON catalog_variant USING gin (lower(sku) gin_trgm_ops);
CREATE INDEX catalog_variant_published_price_idx ON catalog_variant(price_tnd, product_id)
    WHERE status = 'PUBLISHED';

GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA bricocomptoir TO "${applicationUser}";
