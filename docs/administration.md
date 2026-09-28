# Administration Angular

Décisions avant implémentation : réutiliser les écritures existantes et ajouter
les lectures paginées et les réglages persistés manquants.

| Fonction | Catalogue | Commandes | Admin |
| --- | --- | --- | --- |
| Produits, photos, catégories, marques, prix et packs | Oui | Non | Oui |
| Contenus principaux de l'accueil | Oui | Non | Oui |
| Commandes et transitions | Non | Oui | Oui |
| Soldes et ajustements de stock | Non | Non | Oui |
| Frais et gouvernorats desservis | Non | Non | Oui |

Les routes serveur contrôlent les rôles et CSRF. Les onglets Angular ne sont
qu'une aide. Les mutations conservent leurs versions optimistes ; un refus
403 ou un conflit 409 reste visible. Les ajustements réutilisent leur
identifiant après une réponse réseau incertaine.

Les tableaux utilisent des pages SQL bornées et un ordre stable. Les anciennes
lectures de catégories/marques restent disponibles pour les sélecteurs ; les
tableaux utilisent `/admin/catalog/{categories,brands}/page`.
Les packs utilisent `/admin/packs/page`. Les commandes conservent `page/size`.
Le stock est présenté par page de produits/SKU catalogue, avec lecture des
soldes via inventory, sans jointure intermodule.

Les textes d'accueil appartiennent au petit module `content`, indépendant :
une fiche structurée, texte brut, sans HTML ni URL libre. Les frais appartiennent
à `sales` : forfait TND exact, gouvernorats et activation du checkout. Avant le
premier enregistrement, la configuration d'environnement reste effective.
Ensuite PostgreSQL fait autorité. Un changement de frais invalide le récapitulatif
avant commande ; les commandes créées gardent leur instantané.

V9 ajoute les fiches singleton, contraintes, versions, dernier éditeur et index.
Aucun secret, compte ni tarif de production dans la migration.

## Contrats ajoutés

- `GET /api/v1/admin/catalog/categories/page` et `brands/page` : `q`, `page`,
  `size` (1–100), réponse `{items,page,size,totalElements}`.
- `GET /api/v1/admin/packs/page` : même pagination, état et compositions inclus.
- `GET /api/v1/content/home` : fiche publique ; `GET/PUT /api/v1/admin/content/home`
  : `title`, `accent`, `description`, `solutionTitle`, `solutionDescription`,
  `productTitle`, `productDescription`, `version` attendue.
- `GET/PUT /api/v1/admin/delivery` : `enabled`, `amountTnd` chaîne décimale,
  `governorates` codes tunisiens, `version` attendue. Une liste vide n'est
  acceptée que si les commandes sont désactivées. `400` sur saisie invalide,
  `409` sur version périmée, `401/403` sur session/rôle/CSRF.

Routes Angular : `/gestion?section=catalogue|packs|stock|commandes|livraison|accueil`.
Un accès direct à une section interdite n'en charge pas les données. Le compte
de gestion des commandes dispose désormais lui aussi du lien d'administration.
Les tableaux conservent les pages serveur ; les variantes restent les enfants
de la page de produits/packs. Les listes de sélection de catégories et marques
ne remplacent pas ces tableaux paginés.

L'identifiant d'ajustement reste en mémoire au niveau de l'application pendant
une réponse incertaine, même après changement d'onglet d'administration. Un
rechargement du navigateur reste déconseillé et déclenche un avertissement
depuis l'écran de stock. Vérifier les soldes avant une nouvelle opération.
La gestion des médias reprend les actions existantes : téléversement, ordre et
image principale ; pas de suppression de fichier ajoutée à cette étape.
