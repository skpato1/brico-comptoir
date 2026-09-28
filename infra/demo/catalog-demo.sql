-- Données entièrement fictives pour un poste de développement uniquement.
-- Ne pas utiliser pour un catalogue commercial. Exécuter explicitement avec psql.
BEGIN;
SET LOCAL search_path = bricocomptoir;

INSERT INTO catalog_category(id, slug, name)
VALUES (gen_random_uuid(), 'demo-fixations', 'DÉMO — Fixations'),
       (gen_random_uuid(), 'demo-outillage', 'DÉMO — Outillage')
ON CONFLICT (slug) DO NOTHING;

INSERT INTO catalog_brand(id, slug, name)
VALUES (gen_random_uuid(), 'demo-marque-fictive', 'DÉMO — Marque fictive')
ON CONFLICT (slug) DO NOTHING;

WITH created AS (
    INSERT INTO catalog_product(id, category_id, brand_id, name, description, status, demo)
    SELECT gen_random_uuid(), c.id, b.id, 'DÉMO — Article de fixation fictif',
           'Exemple de navigation uniquement. Aucune spécification technique réelle.',
           'PUBLISHED', true
    FROM catalog_category c CROSS JOIN catalog_brand b
    WHERE c.slug='demo-fixations' AND b.slug='demo-marque-fictive'
      AND NOT EXISTS (SELECT 1 FROM catalog_variant WHERE sku='DEMO-FIX-001')
    RETURNING id
)
INSERT INTO catalog_variant(id, product_id, sku, label, unit, price_tnd, status)
SELECT gen_random_uuid(), id, 'DEMO-FIX-001', 'Format fictif', 'pièce', 2.375, 'PUBLISHED'
FROM created;

WITH created AS (
    INSERT INTO catalog_product(id, category_id, name, description, status, demo)
    SELECT gen_random_uuid(), c.id, 'DÉMO — Outil fictif',
           'Exemple de navigation uniquement. Aucune spécification technique réelle.',
           'PUBLISHED', true
    FROM catalog_category c
    WHERE c.slug='demo-outillage'
      AND NOT EXISTS (SELECT 1 FROM catalog_variant WHERE sku='DEMO-OUTIL-001')
    RETURNING id
)
INSERT INTO catalog_variant(id, product_id, sku, label, unit, price_tnd, status)
SELECT gen_random_uuid(), id, 'DEMO-OUTIL-001', 'Modèle fictif', 'pièce', 19.900, 'PUBLISHED'
FROM created;
COMMIT;
