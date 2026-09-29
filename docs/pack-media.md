# Photos des packs et import de visuels fournis

Le module `media` possède les métadonnées `media_pack_image` (Flyway V13) et
les objets privés `packs/{packId}/{imageId}/…`. Les fichiers passent par le
port interchangeable `ObjectStorage`, partagé avec les photos de produits.
L'adaptateur S3/MinIO fonctionne en local ; l'accès B2 de production doit être
configuré et vérifié par un téléversement réel. Aucun binaire n'est stocké en
PostgreSQL ni dans le dépôt Git.

`media -> packs` utilise exclusivement le contrat public `PackPhotoQueries`
via un adaptateur de module. Ce contrat vérifie l'existence administrative et
la visibilité publique effective : pack publié, variante publiée et composants
publiés. Une photo de brouillon reste inaccessible anonymement, y compris par
son UUID connu. La lecture administrative vérifie aussi l'appartenance de la
photo au pack demandé. Aucun changement de composition, prix ou stock lors
du téléversement.

Les gestionnaires de catalogue et administrateurs peuvent envoyer 1 à 4 fichiers
par requête, avec 12 photos maximum par pack. Session et CSRF sont obligatoires
pour les écritures ; clients et gestionnaires de commandes sont refusés.
Validation partagée avec les produits : vrai JPEG/PNG correspondant au type
annoncé, 6 Mio maximum, dimensions 320–6000 pixels et 24 MP maximum. Tout le
lot est décodé et validé avant la première écriture. Un verrou transactionnel
par pack sérialise ajouts et réordonnancements ; contraintes PostgreSQL sur
l'ordre et l'image principale. En cas d'échec d'écriture, la transaction annule
les métadonnées et les objets déjà écrits sont supprimés au mieux. Comme pour
les produits, une panne après écriture S3 ou au commit peut laisser un objet
orphelin : stockage et PostgreSQL ne constituent pas une transaction distribuée.

| Route | Contrat |
| --- | --- |
| `GET /api/v1/media/packs?ids=UUID,…` | Métadonnées des packs effectivement visibles, 100 UUID maximum |
| `GET /api/v1/media/packs/{imageId}/card` ou `/detail` | JPEG dérivé ; aucune route d'original public |
| `GET /api/v1/admin/packs/{packId}/images` | Photos du pack, y compris brouillon |
| `POST /api/v1/admin/packs/{packId}/images` | Multipart `files`, réponse 201 avec métadonnées |
| `PUT /api/v1/admin/packs/{packId}/images/order` | `{imageIds: [UUID,…], primaryImageId: UUID}` ; permutation complète sans doublon |
| `GET /api/v1/admin/packs/{packId}/images/{imageId}/card` ou `/detail` | Dérivé protégé pour la prévisualisation administrative |

Les vues exposent `id`, `packId`, `cardUrl`, `detailUrl`, `width`, `height`,
`sortOrder`, `primary`. Clés S3, hash et original restent privés. Le processeur
existant produit les JPEG de carte (largeur maximale 360) et de fiche (1200),
sans agrandissement. Angular charge progressivement avec `srcset`, `sizes`,
`loading="lazy"`, `decoding="async"`, texte alternatif et remplacement si erreur.
Les cartes et fiches utilisent les images réelles ; un pack sans photo conserve
son emplacement neutre. L'administration permet ajout, ordre et image principale.

## Visuels de l'exploitant du 29 septembre 2026

Quatre brouillons préparés : kit entretien du bois, gants, pinceau plat et
papier abrasif. Leurs illustrations sont fournies par l'exploitant et restent
hors de Git. Aucune variante/SKU, quantité de composition, certification,
caractéristique technique, prix ou stock n'est inventé. Leur validation est
nécessaire avant publication. Le pot visible dans le montage ne suffit pas à
définir sa référence ou ses propriétés, et n'a pas créé de fiche supplémentaire.

Les textes des fiches précisent le caractère illustratif et les détails à
valider. Le comparatif avant/après du kit est simulé : aucune preuve
photographique ni promesse de résultat. Les étapes et conseils visibles dans
l'image doivent être validés selon les références finalement retenues.
