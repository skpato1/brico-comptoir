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
La console `/gestion` a une navigation latérale sur ordinateur et un menu
mobile. Le tableau de bord affiche les commandes confirmées renvoyées par
`GET /admin/orders`, sans total commercial inventé. La liste et le détail de
commande donnent accès au nom, au téléphone, à l'adresse de livraison et à
l'e-mail s'il a été saisi. Ces coordonnées restent dans le contact de la
commande ; aucune fiche client parallèle n'est créée. Seuls `ORDER_MANAGER` et
`ADMIN` lisent ces données, selon les contrôles serveur existants.
Les tableaux conservent les pages serveur ; les variantes restent les enfants
de la page de produits/packs. Les listes de sélection de catégories et marques
ne remplacent pas ces tableaux paginés.

L'identifiant d'ajustement reste en mémoire au niveau de l'application pendant
une réponse incertaine, même après changement d'onglet d'administration. Un
rechargement du navigateur reste déconseillé et déclenche un avertissement
depuis l'écran de stock. Vérifier les soldes avant une nouvelle opération.
La gestion des médias reprend les actions existantes : téléversement, ordre et
image principale ; pas de suppression de fichier ajoutée à cette étape.

## Espace de gestion élargi (6 octobre 2026)

`/gestion` ouvre une vue de tâches par rôle. Les sections produits, photos,
catégories, marques, prix/SKU et import CSV réutilisent les API catalogue
existantes ; les listes restent paginées côté serveur. Packs, stocks,
commandes, livraison et textes d'accueil conservent leurs contrats. Les
onglets du navigateur ne confèrent aucun droit.

| Opération | CATALOG_MANAGER | ORDER_MANAGER | ADMIN |
| --- | --- | --- | --- |
| Bannières du hero, catalogue, photos, packs et textes d'accueil | Oui | Non | Oui |
| Sélection des trois produits « Meilleures ventes » | Oui | Non | Oui |
| Sélection des quatre « Favoris des professionnels » | Oui | Non | Oui |
| Commandes, notifications et messages de contact | Non | Oui | Oui |
| Statistiques de consultation des produits | Oui | Non | Oui |
| Coordonnées de contact, stock, livraison, recherche de compte et panier client en lecture seule | Non | Non | Oui |

## Statistiques produits (14 octobre 2026)

La rubrique `Ventes → Statistiques produits` classe les produits publiés et
visibles selon les **ouvertures de fiches** des 7 ou 30 derniers jours UTC.
Recherche par nom ou SKU, pages de 20, miniatures et lien vers la fiche publique
permettent de choisir les références à examiner pour une publicité. Les tuiles
résument les vues, les produits ayant reçu une vue et les produits éligibles ;
les totaux suivent le filtre de recherche. Le classement n'est ni un compteur
de personnes uniques, ni une mesure des ventes ou du rendement publicitaire.

`POST /api/v1/products/{id}/views` est public mais exige le jeton CSRF et une
fiche réellement visible ; les comptes `ADMIN`, `CATALOG_MANAGER` et
`ORDER_MANAGER` n'incrémentent pas le compteur. Seule la fiche Angular appelle
cette route après son chargement réussi, pas les cartes ni les recommandations.
`GET /api/v1/admin/catalog/product-views?days=7|30&q=&page=0&size=20`
requiert `CATALOG_MANAGER` ou `ADMIN` ; recherche et pagination sont validées
côté serveur (taille maximale 50) et les réponses sont non cachables. Flyway
V19 stocke seulement `(product_id, day, views)` ; une tâche quotidienne retire
les jours hors des 30 derniers jours. Les compteurs commencent à zéro lors de
la mise en service, sans reconstitution fictive des consultations passées.

Le module `content` stocke désormais un agrégat `Hero` (1 à 10 diapositives,
au moins une visible, ordre stable et version optimiste).
`GET /api/v1/content/hero` n'expose que les diapositives visibles ;
`GET/PUT /api/v1/admin/content/hero` donne l'éditeur complet aux gestionnaires de
catalogue. Les liens acceptés sont des routes internes prévues ; les trois
illustrations d'ambiance et trois bannières promotionnelles sont fournies avec
l'application. La bannière « Commande en ligne » dispose d'une composition
mobile spécifique. Les mentions de paiement en ligne, de retrait en agence et
de livraison rapide non vérifiées ont été retirées du visuel fourni. **Le
téléversement libre d'une nouvelle image n'est pas encore pris en charge** :
l'éditeur choisit parmi les ressources intégrées. Les photos
produit continuent de passer par `media` et son stockage objet privé.

`GET /api/v1/content/best-sellers` expose les trois identifiants dans l'ordre
de la vitrine, ou une liste vide si la sélection est absente ou si un produit
cesse d'être visible. `GET/PUT /api/v1/admin/content/best-sellers` permet au
gestionnaire de catalogue de rechercher, ordonner et enregistrer trois produits
publiés distincts, avec version optimiste et CSRF. Enregistrer une liste vide
masque la section. Le libellé est une sélection éditoriale, faute de classement
des ventes disponible ; aucun chiffre de ventes n'est affiché.

`GET /api/v1/content/professional-favorites` expose une sélection distincte
de quatre identifiants ordonnés, ou une liste vide si elle est incomplète ou
qu'un produit n'est plus publié. `GET/PUT /api/v1/admin/content/professional-favorites`
permet à CATALOG_MANAGER et ADMIN de choisir, ordonner ou masquer ces quatre
produits. L'écriture exige CSRF et la version courante ; seuls quatre produits
publiés distincts sont acceptés. Flyway V18 stocke uniquement les références et
leur ordre dans `content`. Prix, marques, photos et stock restent gérés par
leurs modules propriétaires. La boutique écarte la fiche consultée et les
articles épuisés, puis affiche trois produits disponibles.

La tâche « Réseaux sociaux » de l'administration gère les URL Facebook et
Instagram du bandeau. `GET /api/v1/content/social-links` est public ;
`GET/PUT /api/v1/admin/content/social-links` est réservé aux rôles
CATALOG_MANAGER et ADMIN. L'écriture exige CSRF, une version à jour et des URL
HTTPS sur les domaines attendus. Un champ vide masque l'icône concernée.
Facebook est initialisé à l'adresse fournie par l'exploitant ; Instagram reste
vide jusqu'à sa saisie.

`GET /api/v1/contact` fournit les coordonnées publiques, vides tant que
l'exploitant ne les renseigne pas. `PUT /api/v1/admin/contact` (ADMIN, CSRF,
version) les modifie. `POST /api/v1/contact/messages` reçoit un message
visiteur avec CSRF, champs bornés, champ leurre et limite de cinq messages par
heure et par IP de l'instance. `GET /api/v1/admin/contact/messages` est paginé
(`page`, `size` de 1 à 50, `status=NEW|RESOLVED`) et réservé à ORDER_MANAGER
ou ADMIN ; `POST .../{id}/resolve` marque un message traité une seule fois.
Le corps n'est jamais inséré comme HTML. Les messages sont conservés dans
PostgreSQL ; `CONTACT_MESSAGE_RETENTION_DAYS` active leur purge périodique,
`0` la désactive. L'exploitant doit fixer une durée avant l'ouverture.

Le support ADMIN recherche un compte par email exact via
`GET /api/v1/admin/accounts/lookup?email=…`, puis peut lire son panier client via
`GET /api/v1/admin/support/carts/{customerId}`. Cette dernière réponse est
limitée aux références et quantités du panier ; aucun prix ni disponibilité
n'est promis. Elle n'accepte aucune écriture. Les paniers visiteurs restent
dans le navigateur du visiteur et ne sont pas consultables par l'équipe.
L'inbox contact constitue un nouveau traitement de données personnelles :
les droits d'accès/rectification/export/suppression des messages demandent
encore une procédure de support vérifiant l'identité du demandeur ; ils ne
sont pas couverts par l'export automatique du compte.
