# Packs — composition, offre et disponibilité

Décisions prises avant l'implémentation, le 27 septembre 2026.

Le module `packs` possède ses fiches, ses variantes commerciales et leurs
compositions. Il appelle uniquement les ports publics de `catalog` pour
identifier/valider les SKU et d'`inventory` pour lire le disponible. Ni
`catalog` ni `inventory` ne dépendent de `packs`. Ce module distinct évite
d'introduire une dépendance `catalog -> inventory` dans le monolithe.

Un pack est une fiche avec code, nom, slogan, guide textuel, état
`DRAFT`/`PUBLISHED`, marqueur `demo` et version. Chaque variante du pack a son
propre code, libellé, prix final en TND à trois décimales, état et version.
Chaque variante comporte au moins un SKU du catalogue et une quantité entière
strictement positive ; il n'y a ni pack imbriqué, ni composant facultatif, ni
substitution implicite. Un même SKU soumis plusieurs fois dans une composition
est agrégé avant persistance avec détection de dépassement ; sa clé est unique
en base par variante de pack. Deux variantes (« Sans outils » et « Tout
compris », par exemple) peuvent avoir des compositions différentes. Le prix
du pack n'est pas calculé à partir des prix des composants.

Une variante de pack ne peut être publiée si un composant n'est pas publié au
catalogue ; une fiche pack ne peut être publiée sans variante publiée. Si un
composant est ensuite dépublié, la variante de pack devient immédiatement
invisible au public. La fiche n'est publique que si elle est publiée et
possède encore une variante visible. Le brouillon reste accessible aux
gestionnaires. Aucune suppression physique n'est nécessaire dans ce premier
périmètre : retour en brouillon et versions optimistes.

La disponibilité indicative d'une variante de pack est
`min(floor(disponible(SKU) / quantitéRequise(SKU)))` après agrégation des
occurrences. Aucun solde de pack n'est stocké. Deux packs ou variantes qui
partagent un SKU affichent chacun une disponibilité *individuelle* : ces
nombres ne s'additionnent pas. Pour plusieurs exemplaires ou un panier mixte,
le port d'offre fournit la composition immuable et le futur module `sales`
agrégera toutes les demandes SKU avant d'appeler la réservation atomique du
module `inventory`. L'affichage peut devenir périmé ; il ne promet pas la
disponibilité au moment de la commande.

Une lecture d'offre fournit `packVersion`, `variantVersion`, prix, devise et
composition. Une modification de fiche incrémente `packVersion`, une
modification de prix, état ou composition incrémente `variantVersion`.
La lecture d'offre seule utilise un instantané PostgreSQL `REPEATABLE READ` ;
si elle rejoint une transaction métier englobante, l'isolation de celle-ci
prévaut. L'instantané est une valeur copiée pour le futur module `sales`, qui devra
revalider l'offre et la publication des composants dans sa transaction avant
de réserver. Aucune commande n'est ajoutée par cette étape.

## HTTP

- `GET /api/v1/packs` et `GET /api/v1/packs/{id}` : lecture publique des seules
  fiches/variantes visibles, avec disponibilité indicative et prix TND chaîne.
  Le champ `componentNames` associe les UUID des composants aux noms, variantes
  et références lisibles, résolus via le contrat public du catalogue. Il permet
  à la vitrine d'expliquer le contenu sans exposer les fiches non publiées.
- `GET /api/v1/admin/packs` et `GET /api/v1/admin/packs/{id}` : lecture des
  brouillons et compositions, `CATALOG_MANAGER` ou `ADMIN`.
- `POST /api/v1/admin/packs`, `PUT /api/v1/admin/packs/{id}` : créer/modifier
  une fiche. `PUT` exige la version attendue.
- `POST /api/v1/admin/packs/{id}/variants`,
  `PUT /api/v1/admin/packs/{id}/variants/{variantId}` : créer/modifier une
  variante avec composition complète. `PUT` exige la version attendue.

Toutes les écritures exigent session autorisée et CSRF. Les données invalides
renvoient 400, les références absentes/invisibles 404 et les versions ou
contraintes concurrentes 409. Les cinq brouillons ont été définis comme
exemples fictifs après clarification avec l'utilisateur ; leurs noms,
compositions et prix sont dans [packs-demo.sql](../infra/demo/packs-demo.sql)
et récapitulés dans la [roadmap](roadmap.md). Le chargement est volontaire,
après le jeu catalogue fictif. Aucune donnée de démonstration n'est injectée
dans une migration de production. Une fiche `demo=true` ne peut pas être
publiée par la route d'administration ordinaire ; une offre réelle devra être
créée avec des données commerciales validées.
