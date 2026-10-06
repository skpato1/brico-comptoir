ALTER TABLE content_hero_slide DROP CONSTRAINT content_hero_slide_image_check;
ALTER TABLE content_hero_slide ADD CONSTRAINT content_hero_slide_image_check
    CHECK (image IN ('kits', 'hardware', 'tools', 'brico-projects', 'brico-workshop'));

-- Replace only untouched seed content. An edited hero remains under the manager's control;
-- both new visuals are still available in the editor.
UPDATE content_hero_slide SET
    image = 'brico-projects',
    label = 'Tout pour vos projets',
    title = 'BricoComptoir, tout pour vos projets',
    description = 'Explorez les produits et packs publiés pour préparer votre prochain projet.',
    alt = 'Visuel promotionnel BricoComptoir : atelier, outils et univers bricolage. Produits illustratifs.',
    link = '/catalogue',
    action = 'Découvrir les produits',
    detail = 'Visuel promotionnel · Produits illustratifs'
WHERE id = '4f50a2a1-246a-4267-b157-a46f10793811'
  AND image = 'kits'
  AND EXISTS (SELECT 1 FROM content_hero WHERE id = 1 AND version = 0);

UPDATE content_hero_slide SET
    image = 'brico-workshop',
    label = 'Atelier & outillage',
    title = 'L’atelier BricoComptoir',
    description = 'Retrouvez les références publiées pour bricoler et équiper votre atelier.',
    alt = 'Visuel promotionnel BricoComptoir : établi et outils de bricolage. Produits et marques illustratifs.',
    link = '/catalogue',
    action = 'Explorer le catalogue',
    detail = 'Visuel promotionnel · Produits illustratifs'
WHERE id = '4f50a2a1-246a-4267-b157-a46f10793813'
  AND image = 'tools'
  AND EXISTS (SELECT 1 FROM content_hero WHERE id = 1 AND version = 0);
