# Catalogue SQES dans BricoComptoir (application Spring/Angular)

Cet import vise `https://brico-comptoir.vercel.app/`, **pas la boutique Shopify**.
L'exploitant a autorisé la reprise des photos SQES le 6 octobre 2026. Le
fichier source privé `.local/sqes-import/catalogue-facts.json` est un instantané
des fiches publiques relevé le **3 octobre 2026** : 6 644 produits, 9 420
variantes et 353 collections. Le flux de produits a été revérifié le
**6 octobre 2026** : un nouveau produit et 12 prix de variantes modifiés sur
9 fiches ont été ajoutés à l'export ; 3 fiches disparues du flux ont été
conservées en brouillon. L'archive réconciliée contient **6 645 produits,
9 421 variantes et 18 162 références photo (14 767 fichiers distincts)**.
Les appartenances aux collections restent celles du 3 octobre ; le nouveau
produit est classé dans « Autres produits SQES ». Ces relevés ne garantissent
pas une synchronisation continue avec SQES. Aucun texte de description commerciale ni aucune indication
de disponibilité fournisseur ne sont copiés dans l'application.

L'exploitant a confirmé la même règle que pour l'import Shopify : majorer le
prix affiché de 20 %, avec calcul entier en millimes et arrondi **au dinar
supérieur**. Les **6 617** fiches entièrement tarifées encore présentes dans
le flux sont publiées au **stock zéro** : elles sont visibles mais non
achetables. Les 25 fiches contenant un
prix nul sont créées **en brouillon sans variante vendable** et demandent une
validation de prix ; les 3 fiches retirées du flux sont également en brouillon.
Aucun stock SQES n'est assimilé à un stock BricoComptoir.
Les produits présentés comme kits chez SQES restent des produits simples ;
aucune composition de pack ni disponibilité de composants n'est inventée.

Le modèle BricoComptoir accepte une catégorie principale par produit. Les
353 collections sont créées comme catégories plates (plus une catégorie de
repli), et la plus spécifique selon son effectif est choisie comme catégorie
principale ; **toutes** les appartenances d'origine restent dans le JSON.
Les 322 marques distinctes sont préfixées `sqes-` dans leurs slugs pour éviter
les collisions. Les SKU internes sont stables : référence fournisseur unique
préfixée `BC-`, sinon identifiant de variante source `BC-SQES-…`.

## Export portable

`tools/export-sqes-brico.mjs` crée
`.local/sqes-brico-export/catalogue.json` et un dossier `photos/` à côté.
Chaque photo téléchargée possède un `path` **relatif au dossier de l'export**, un
SHA-256, son type et ses dimensions. Les URL et identifiants source sont
conservés pour les médias qui ne peuvent pas être téléchargés ou téléversés.
Les photos sont demandées au CDN en 1200 px maximum, avec conversion JPEG
quand elle est disponible. Les GIF restent dans l'archive avec
`uploadable: false` : l'API média actuelle accepte seulement JPEG/PNG.
La validation serveur exige une dimension minimale de **320 px**. Les 62
produits dont toutes les photos source sont plus petites ou incompatibles
restent sans image dans l'application, conformément au choix de l'exploitant ;
les fichiers récupérés sont néanmoins conservés dans l'archive.

Pour rendre immédiatement visibles les images principales sans attendre les
écritures du stockage objet, `tools/write-sqes-image-fallback.mjs` génère
`frontend/public/sqes-image-fallback.json` : 6 555 fiches publiées avec une
photo compatible. L'interface privilégie toujours les médias natifs de
BricoComptoir et utilise le CDN SQES uniquement si l'API média ne renvoie
encore aucune image pour la fiche. Ce repli dépend de la disponibilité du
CDN externe ; l'archive locale demeure la copie transportable. Il ne
remplace pas les galeries natives.

```powershell
node tools/check-sqes-source-drift.mjs
node tools/reconcile-sqes-source.mjs
node tools/export-sqes-brico.mjs .local/sqes-brico-export/source-reconciled.json .local/sqes-brico-export --download
node --test tools/sqes-brico-catalogue.test.mjs
```

L'archive reste dans `.local/`, ignoré par Git. Pour la transférer, copier
**ensemble** `catalogue.json` et `photos/`. Ne pas publier les identifiants
administrateur ni le `import-journal.json` dans cette archive. Le JSON contient
les URL SQES et les prix source : le revoir avant redistribution.

## Import relançable

Le script utilise `.local/production-admin.env` avec `ADMIN_EMAIL` et
`ADMIN_PASSWORD` ; ce fichier demeure hors Git. Il se connecte par session
et jeton CSRF à l'API existante, vérifie le rôle catalogue et crée les
catégories et marques manquantes. Les produits tarifés passent par l'aperçu
CSV et l'application transactionnelle par lots d'au plus 200 variantes.
Un journal privé associe les identifiants source aux identifiants cible ; une
relance réconcilie également les SKU présents avant de créer quoi que ce soit.
Les photos sont ajoutées d'abord une par produit, puis les galeries, afin
de donner la priorité à une image visible pour chaque fiche.

```powershell
node tools/check-sqes-brico-target.mjs
node tools/import-sqes-brico.mjs .local/sqes-brico-export https://brico-comptoir.vercel.app --phase=catalogue
node tools/reconcile-sqes-brico.mjs .local/sqes-brico-export https://brico-comptoir.vercel.app --dry-run
node tools/reconcile-sqes-brico.mjs .local/sqes-brico-export https://brico-comptoir.vercel.app
node tools/write-sqes-image-fallback.mjs
node tools/import-sqes-brico.mjs .local/sqes-brico-export https://brico-comptoir.vercel.app --phase=primary
node tools/import-sqes-brico.mjs .local/sqes-brico-export https://brico-comptoir.vercel.app --phase=gallery
node tools/verify-sqes-brico.mjs .local/sqes-brico-export https://brico-comptoir.vercel.app
```

L'import média accepte `--workers=1..8` (trois par défaut). La phase
`catalogue` reste limitée à trois lots CSV simultanés. Pour tester un seul
article, ajouter `--source-id=IDENTIFIANT_SOURCE` ou `--max-products=1`.
Une erreur 500 isolée est revérifiée dans les métadonnées serveur avant
une reprise limitée, afin d'éviter les doublons. Pour une nouvelle
installation, copier l'archive sans le journal d'exécution, renseigner un
nouvel administrateur dans `.local/production-admin.env`, puis relancer les
deux phases. Les écritures restent soumises au RBAC et à la validation du
backend. Une image principale unique, terminée sur le serveur avant une
interruption, peut être rapprochée de ses dimensions et de sa position lors
de la reprise. Si la correspondance n'est pas unique, le script s'arrête
plutôt que de dupliquer une photo ; inspecter alors la fiche indiquée.

Le backend limite à **12 photos par produit**. Dans cet instantané, 42 fiches
en ont davantage, soit 176 photos au-delà de cette limite. Le JSON et son
archive conservent leurs références et fichiers ; l'application ne peut pas
les afficher toutes sans changer explicitement cette limite. Les images
publiées sont servies en renditions carte et fiche par l'API média.

Ne considérer l'import réussi qu'après contrôle du journal, des totaux dans
l'API et d'un échantillon de fiches et d'images sur le site. L'export ne
constitue pas une synchronisation automatique avec SQES.
