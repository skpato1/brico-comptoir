# Catalogue : contrats et arbitrages

Le catalogue est une tranche de l'étape 3. Les packs et l'instantané destiné à
la commande viendront ensuite. Un **produit** est la fiche présentée au client ;
une **variante** est la référence vendable et future référence de stock. Chaque
variante porte un SKU unique, une unité de vente entière, un prix final en TND et
son état de publication. Une fiche n'est visible au public que si elle est
`PUBLISHED`, que sa catégorie et sa marque éventuelle sont actives, et qu'elle
a au moins une variante `PUBLISHED`. Les variantes brouillon ne figurent jamais
dans la réponse publique. Le stock et la disponibilité ne sont pas affirmés ici.

Une catégorie a au plus un parent. La base interdit l'auto-parenté, les cycles,
les références orphelines, les SKU et slugs en doublon, les prix nuls ou négatifs
et les statuts inconnus. Les changements de parent sont sérialisés ; une catégorie
avec enfants ou produits, et une marque avec produits, ne peuvent être archivées.
Les éditions utilisent une version optimiste : le corps PUT porte `version`, et
une version périmée reçoit `409`. La version du produit augmente aussi quand une
variante change. Pas de suppression physique à ce stade : un produit ou une
variante repasse en brouillon, une catégorie ou une marque est archivée.

Les caractéristiques de fiche et les options de variante sont des dictionnaires
de texte bornés (20 clés, noms 60 caractères, valeurs 250). Elles servent à
afficher les faits connus utiles à un particulier, par exemple matière, usage,
compatibilité ou dimension ; un champ inconnu est omis. Aucune caractéristique
technique n'est inférée. Les montants sont des chaînes décimales dans les
requêtes (`priceTnd`) et les réponses (`price: {amount, currency: "TND"}`),
validés à trois décimales au plus et stockés en `numeric(12,3)`.

## API `/api/v1`

| Route | Effet | Permission |
| --- | --- | --- |
| `GET /categories`, `GET /brands` | listes actives, catégories avec `parentId` pour former l'arbre | Public |
| `GET /products` | `{items,page,size,totalElements}` ; seulement les offres publiées | Public |
| `GET /products/{id}` | fiche et variantes publiées, ou 404 | Public |
| `GET /admin/catalog/categories`, `/categories/{id}`, `/brands`, `/brands/{id}`, `/products`, `/products/{id}` | lecture incluant brouillons et entrées inactives | `CATALOG_MANAGER` ou `ADMIN` |
| `POST/PUT /admin/catalog/categories[/{id}]` | créer/éditer `{parentId,slug,name,active,version}` | `CATALOG_MANAGER` ou `ADMIN` |
| `POST/PUT /admin/catalog/brands[/{id}]` | créer/éditer `{slug,name,active,version}` | `CATALOG_MANAGER` ou `ADMIN` |
| `POST/PUT /admin/catalog/products[/{id}]` | créer/éditer `{categoryId,brandId,name,description,characteristics,status,version}` | `CATALOG_MANAGER` ou `ADMIN` |
| `POST /admin/catalog/products/{productId}/variants` | créer `{sku,label,unit,options,priceTnd,status}` | `CATALOG_MANAGER` ou `ADMIN` |
| `PUT /admin/catalog/products/{productId}/variants/{id}` | éditer les mêmes champs et `version` | `CATALOG_MANAGER` ou `ADMIN` |
| `GET /media/products?ids=id1,id2` | images réduites de 1 à 100 produits visibles, indexées par UUID ; jamais de clé objet ni d'original | Public |
| `GET /media/{imageId}/card`, `/detail` | JPEG de 360 px ou 1200 px de large maximum, seulement si la fiche est encore visible | Public |
| `GET /admin/catalog/products/{productId}/images` | ordre et image principale, sans clé objet | `CATALOG_MANAGER` ou `ADMIN` |
| `GET /admin/catalog/products/{productId}/images/{imageId}/card`, `/detail` | rendus de gestion, y compris pour un brouillon ; aucun original | `CATALOG_MANAGER` ou `ADMIN` |
| `POST /admin/catalog/products/{productId}/images` | multipart `files` : 1 à 4 images ; réponse 201 avec la liste ordonnée | `CATALOG_MANAGER` ou `ADMIN` + CSRF |
| `PUT /admin/catalog/products/{productId}/images/order` | `{imageIds:[...],primaryImageId}` : chaque image exactement une fois | `CATALOG_MANAGER` ou `ADMIN` + CSRF |
| `POST /admin/catalog/imports/preview` | multipart `file` CSV ; retourne empreinte SHA-256, lignes, erreurs par champ et échantillon | `CATALOG_MANAGER` ou `ADMIN` + CSRF |
| `POST /admin/catalog/imports/apply?expectedDigest=...` | même fichier CSV, revalidation et création atomique ; résultat `{productCount,variantCount,productIds}` | `CATALOG_MANAGER` ou `ADMIN` + CSRF |

Pour `GET /products` et la liste admin : `q` cherche une sous-chaîne du nom,
de la description ou du SKU (sans interpréter `%` comme joker) ; `categoryId`
inclut les sous-catégories ; `brandId`, `minPrice`, `maxPrice` filtrent. Les deux
bornes de prix doivent concerner une même variante. `sort` accepte `name`
(défaut), `price_asc`, `price_desc`, `newest` ; l'UUID départage les égalités.
`page` commence à zéro ; `size` vaut 1 à 100, défaut 20. Prix des variantes
publiées uniquement dans les filtres/tri publics. Un produit avec une autre
variante hors fourchette ne divulgue pas cette variante : la fiche renvoie les
variantes publiées de la fiche, le filtre sélectionne la fiche.

`POST` renvoie `201` et `Location`, `PUT` renvoie `200`; validation `400`, absence
ou brouillon public `404`, rôle insuffisant/CSRF invalide `403`, session absente
`401`, conflit de version ou de contrainte `409`. Les écritures requièrent un
cookie de session et `X-XSRF-TOKEN` après `GET /auth/csrf`. L'interface Angular
ne fait qu'aider la navigation : Spring Security contrôle toutes les routes.
L'API publique n'expose ni stock ni disponibilité avant le module concerné.

## Photos et stockage

Le module `media` possède les métadonnées `media_product_image` en PostgreSQL et
un port `ObjectStorage` ; l'adaptateur local utilise MinIO avec un bucket privé.
Le lien vers le catalogue passe par `CatalogProductQueries`, sans accès direct
aux tables du catalogue depuis `media`. La base conserve UUID du produit,
dimensions, type détecté, empreinte, taille, ordre, indicateur principal et
clés d'objet ; aucun octet d'image n'y est stocké. L'absence de clé étrangère
intermodule évite un couplage SQL, le produit étant validé par le port. Les
produits ne sont pas supprimés physiquement dans cette tranche.

Le serveur accepte JPEG et PNG seulement, jusqu'à 6 Mio par fichier, de
320 × 320 à 6000 × 6000 pixels et 24 MP au plus. Le type déclaré doit
correspondre au décodage réel. Il valide tout le lot avant le premier stockage,
avec 12 images au maximum par produit. Les rendus JPEG « card » et « detail »
sont redimensionnés sans agrandissement, aplatis sur fond blanc pour les PNG
transparents et compressés avec une qualité distincte. Les originaux restent
privés dans MinIO ; aucune route publique ne les dessert. Angular utilise
`srcset`, `sizes`, `loading="lazy"` et `decoding="async"`. Les pages de catalogue
ne chargent que les métadonnées d'images de leurs produits visibles. Le
stockage objet n'est pas transactionnel avec PostgreSQL : un échec pendant
l'écriture provoque une suppression compensatoire ; un échec de commit ou de
nettoyage peut laisser un objet orphelin, à traiter par une tâche de
réconciliation avant exploitation à grande échelle.

## Import CSV

Encodage UTF-8 strict, séparateur `;`, ligne d'en-tête exacte :

```text
productKey;categorySlug;brandSlug;productName;description;sku;variantLabel;unit;priceTnd;status
```

Taille maximale 1 Mio, 500 lignes de données. `brandSlug` et `description`
peuvent être vides. Plusieurs lignes portant le même `productKey` créent des
variantes d'un même produit ; les champs de fiche doivent y être identiques.
Les catégories et marques doivent déjà exister et être actives. Le prix suit
les mêmes règles TND à trois décimales que l'API. L'import est **création
seule** : un SKU déjà présent provoque une erreur d'aperçu, sans modification
de la fiche existante. L'aperçu n'écrit rien et expose jusqu'à 20 lignes ;
l'application exige le même fichier par empreinte, revalide toutes les lignes
et écrit dans une transaction PostgreSQL. Une concurrence créant un SKU entre
les deux étapes peut encore aboutir à `409`, avec rollback intégral.

Exemple de format uniquement, entièrement fictif :

```csv
productKey;categorySlug;brandSlug;productName;description;sku;variantLabel;unit;priceTnd;status
exemple-1;fixations;;DÉMO article fictif;;DEMO-EXEMPLE-1;Format fictif;pièce;2.375;DRAFT
```

Les données [de démonstration](../infra/demo/catalog-demo.sql) sont fictives,
identifiables par le préfixe `DÉMO` et `demo: true`, et ne font **pas** partie des
migrations. Après avoir démarré Compose, on peut les charger explicitement :

```powershell
Get-Content infra/demo/catalog-demo.sql | docker compose exec -T postgres psql -U postgres -d bricocomptoir
```

Cette commande vise uniquement la base locale de Compose. Ne pas utiliser ces
prix ou libellés pour une vente réelle.
