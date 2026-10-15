ALTER TABLE content_hero_slide DROP CONSTRAINT content_hero_slide_image_check;
ALTER TABLE content_hero_slide ADD CONSTRAINT content_hero_slide_image_check
    CHECK (image IN ('kits', 'hardware', 'tools', 'brico-online', 'brico-projects', 'brico-workshop'));

-- Add the supplied banner first only when the original three-slide selection is untouched.
-- An administrator's edited order or copy remains authoritative; the new image is available
-- in the editor for those installations.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM content_hero WHERE id = 1 AND version = 0)
       AND (SELECT count(*) FROM content_hero_slide) = 3
       AND EXISTS (SELECT 1 FROM content_hero_slide
                   WHERE id = '4f50a2a1-246a-4267-b157-a46f10793811' AND position = 0)
       AND EXISTS (SELECT 1 FROM content_hero_slide
                   WHERE id = '4f50a2a1-246a-4267-b157-a46f10793812' AND position = 1)
       AND EXISTS (SELECT 1 FROM content_hero_slide
                   WHERE id = '4f50a2a1-246a-4267-b157-a46f10793813' AND position = 2)
    THEN
        UPDATE content_hero_slide SET position = 3 WHERE position = 2;
        UPDATE content_hero_slide SET position = 2 WHERE position = 1;
        UPDATE content_hero_slide SET position = 1 WHERE position = 0;
        INSERT INTO content_hero_slide(id,position,visible,image,label,title,description,alt,link,action,detail)
        VALUES ('7c4b77af-e09d-478a-b52d-412c01231b99',0,true,'brico-online',
                'Commande en ligne','BricoComptoir, commandez en ligne',
                'Parcourez le catalogue et choisissez les produits de votre projet.',
                'Visuel BricoComptoir de la commande en ligne : téléphone et outils de bricolage illustratifs.',
                '/catalogue','Voir le catalogue','Visuel promotionnel · Produits illustratifs');
    END IF;
END $$;
