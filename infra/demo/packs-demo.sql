-- Cinq packs fictifs, tous DRAFT. Charger manuellement après catalog-demo.sql.
-- Aucune instruction technique ou proposition commerciale réelle.
BEGIN;
SET LOCAL search_path = bricocomptoir;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM catalog_variant WHERE sku = 'DEMO-FIX-001')
       OR NOT EXISTS (SELECT 1 FROM catalog_variant WHERE sku = 'DEMO-OUTIL-001') THEN
        RAISE EXCEPTION 'Charger catalog-demo.sql avant packs-demo.sql';
    END IF;
END $$;

INSERT INTO pack_offer(id, code, name, slogan, guide, status, demo)
VALUES
  (gen_random_uuid(), 'demo-fixation-legere', 'DÉMO — Fixation légère',
   'Exemple fictif de composition.', 'Guide de démonstration ; aucune instruction technique réelle.', 'DRAFT', true),
  (gen_random_uuid(), 'demo-assemblage-simple', 'DÉMO — Assemblage simple',
   'Exemple fictif de composition.', 'Guide de démonstration ; aucune instruction technique réelle.', 'DRAFT', true),
  (gen_random_uuid(), 'demo-petit-atelier', 'DÉMO — Petit atelier',
   'Exemple fictif de composition.', 'Guide de démonstration ; aucune instruction technique réelle.', 'DRAFT', true),
  (gen_random_uuid(), 'demo-reassort-fictif', 'DÉMO — Réassort fictif',
   'Exemple fictif de composition.', 'Guide de démonstration ; aucune instruction technique réelle.', 'DRAFT', true),
  (gen_random_uuid(), 'demo-decouverte', 'DÉMO — Découverte',
   'Exemple fictif de composition.', 'Guide de démonstration ; aucune instruction technique réelle.', 'DRAFT', true)
ON CONFLICT (code) DO NOTHING;

WITH seed(pack_code, code, label, price_tnd) AS (VALUES
  ('demo-fixation-legere', 'sans-outils', 'Sans outils', 5.000),
  ('demo-fixation-legere', 'tout-compris', 'Tout compris', 25.000),
  ('demo-assemblage-simple', 'sans-outils', 'Sans outils', 9.000),
  ('demo-assemblage-simple', 'tout-compris', 'Tout compris', 29.000),
  ('demo-petit-atelier', 'base', 'Version de démonstration', 24.000),
  ('demo-reassort-fictif', 'base', 'Version de démonstration', 23.000),
  ('demo-decouverte', 'sans-outils', 'Sans outils', 3.000),
  ('demo-decouverte', 'tout-compris', 'Tout compris', 23.000)
)
INSERT INTO pack_variant(id, pack_id, code, label, price_tnd, status)
SELECT gen_random_uuid(), p.id, s.code, s.label, s.price_tnd, 'DRAFT'
FROM seed s JOIN pack_offer p ON p.code = s.pack_code AND p.demo
ON CONFLICT (pack_id, code) DO NOTHING;

WITH seed(pack_code, variant_code, sku, quantity) AS (VALUES
  ('demo-fixation-legere', 'sans-outils', 'DEMO-FIX-001', 2),
  ('demo-fixation-legere', 'tout-compris', 'DEMO-FIX-001', 2),
  ('demo-fixation-legere', 'tout-compris', 'DEMO-OUTIL-001', 1),
  ('demo-assemblage-simple', 'sans-outils', 'DEMO-FIX-001', 4),
  ('demo-assemblage-simple', 'tout-compris', 'DEMO-FIX-001', 4),
  ('demo-assemblage-simple', 'tout-compris', 'DEMO-OUTIL-001', 1),
  ('demo-petit-atelier', 'base', 'DEMO-FIX-001', 1),
  ('demo-petit-atelier', 'base', 'DEMO-OUTIL-001', 1),
  ('demo-reassort-fictif', 'base', 'DEMO-FIX-001', 10),
  ('demo-decouverte', 'sans-outils', 'DEMO-FIX-001', 1),
  ('demo-decouverte', 'tout-compris', 'DEMO-FIX-001', 1),
  ('demo-decouverte', 'tout-compris', 'DEMO-OUTIL-001', 1)
)
INSERT INTO pack_component(pack_variant_id, catalog_variant_id, quantity)
SELECT v.id, c.id, s.quantity
FROM seed s
JOIN pack_offer p ON p.code = s.pack_code AND p.demo
JOIN pack_variant v ON v.pack_id = p.id AND v.code = s.variant_code
JOIN catalog_variant c ON c.sku = s.sku
ON CONFLICT (pack_variant_id, catalog_variant_id) DO NOTHING;

COMMIT;
