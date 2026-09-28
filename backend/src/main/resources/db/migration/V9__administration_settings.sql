CREATE TABLE content_home (
    id integer PRIMARY KEY CHECK (id = 1),
    title varchar(100) NOT NULL CHECK (length(trim(title)) > 0),
    accent varchar(100) NOT NULL CHECK (length(trim(accent)) > 0),
    description varchar(500) NOT NULL CHECK (length(trim(description)) > 0),
    solution_title varchar(120) NOT NULL CHECK (length(trim(solution_title)) > 0),
    solution_description varchar(500) NOT NULL CHECK (length(trim(solution_description)) > 0),
    product_title varchar(120) NOT NULL CHECK (length(trim(product_title)) > 0),
    product_description varchar(500) NOT NULL CHECK (length(trim(product_description)) > 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(254)
);
INSERT INTO content_home(id,title,accent,description,solution_title,solution_description,product_title,product_description)
VALUES (1,'À vous de faire.','À nous d’équiper.','Pour réparer, assembler ou aménager chez vous. Trouvez les bonnes pièces, une par une ou en pack.',
'Je cherche une solution','Un besoin, un ensemble d’articles. Comparez les compositions et choisissez votre variante.',
'Je cherche un produit','Vous savez ce qu’il vous faut ? Recherchez un article, une catégorie ou une référence.');

CREATE TABLE sales_delivery_settings (
    id integer PRIMARY KEY CHECK (id = 1),
    enabled boolean NOT NULL DEFAULT true,
    fee_tnd numeric(9,3) CHECK (fee_tnd >= 0),
    governorates text[] NOT NULL DEFAULT '{}',
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(254),
    CHECK (fee_tnd IS NULL OR NOT enabled OR cardinality(governorates) > 0),
    CHECK (governorates <@ ARRAY['ARIANA','BEJA','BEN_AROUS','BIZERTE','GABES','GAFSA','JENDOUBA','KAIROUAN','KASSERINE','KEBILI','KEF','MAHDIA','MANOUBA','MEDENINE','MONASTIR','NABEUL','SFAX','SIDI_BOUZID','SILIANA','SOUSSE','TATAOUINE','TOZEUR','TUNIS','ZAGHOUAN']::text[])
);
-- NULL keeps deployment environment values effective until the first explicit save.
INSERT INTO sales_delivery_settings(id) VALUES (1);
REVOKE INSERT, DELETE ON content_home, sales_delivery_settings FROM "${applicationUser}";
GRANT SELECT, UPDATE ON content_home, sales_delivery_settings TO "${applicationUser}";
CREATE INDEX pack_offer_admin_order_idx ON pack_offer(lower(name), id);
CREATE INDEX catalog_category_admin_order_idx ON catalog_category(name, id);
CREATE INDEX catalog_brand_admin_order_idx ON catalog_brand(name, id);
