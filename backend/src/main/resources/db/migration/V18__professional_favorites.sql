CREATE TABLE content_professional_favorites (
    id integer PRIMARY KEY CHECK (id = 1),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(254)
);
INSERT INTO content_professional_favorites(id) VALUES (1);

CREATE TABLE content_professional_favorite_product (
    position integer PRIMARY KEY CHECK (position BETWEEN 0 AND 3),
    product_id uuid NOT NULL UNIQUE
);

-- Product ownership remains in catalog; publication is checked via its public contract.
GRANT SELECT, UPDATE ON content_professional_favorites TO "${applicationUser}";
GRANT SELECT, INSERT, DELETE ON content_professional_favorite_product TO "${applicationUser}";
