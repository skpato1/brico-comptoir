# Inventaire — invariants et concurrence

Décision prise avant l'implémentation du noyau, le 27 septembre 2026.

## Modèle et invariants

Un seul dépôt, des quantités entières dans l'unité de vente du SKU. La variante
du catalogue identifie le SKU par son UUID ; le code SKU est une étiquette
modifiable. `stock physique` (`on_hand`) = unités effectivement en dépôt,
réservées comprises. `stock réservé` (`reserved`) = unités affectées aux
réservations `ACTIVE`, encore physiquement présentes. `stock disponible` =
`on_hand - reserved`. Une variante sans ligne de stock a les trois soldes à zéro.

Pour tout SKU : `0 <= reserved <= on_hand`. Une demande réserve une quantité
strictement positive par variante ; les doublons d'une demande sont agrégés
avec détection de dépassement. Une réservation porte un UUID fourni par le
futur module de commande, une liste de lignes immuable et un état global
`ACTIVE`, `RELEASED` ou `CONSUMED`. Aucun état terminal ne redevient actif.
Rejouer une libération ou une conversion déjà effectuée ne modifie ni soldes ni
journal ; rejouer une réservation encore `ACTIVE` avec les mêmes lignes est
également sans effet. Réutiliser un UUID avec d'autres lignes, relancer une
réservation terminée ou demander la transition terminale opposée est un
conflit. Une libération retire seulement le réservé ; une
conversion retire à la fois le physique et le réservé. Un ajustement signé
change seulement le physique, avec motif obligatoire et `operationId` unique ;
un rejeu identique est sans effet, un rejeu divergent est un conflit. Les
mouvements sont ajoutés dans la même transaction que chaque changement de
solde. Les montants monétaires restent dans `catalog`, jamais dans l'inventaire.

## Séquence transactionnelle

PostgreSQL `READ COMMITTED`, transactions Spring `REQUIRED` : l'appel du futur
module `sales` et la réservation devront partager la même transaction. Pour
une nouvelle réservation, insérer la ligne d'en-tête par
`INSERT ... ON CONFLICT DO NOTHING`, puis la verrouiller par `FOR UPDATE`.
Pour libérer ou convertir, verrouiller d'abord cet en-tête. Acquérir ensuite
chaque verrou de stock par un **SELECT séparé** `FOR UPDATE`, dans l'ordre
croissant de l'UUID de variante ; une requête unique `ORDER BY` ne sert pas de
garantie d'ordre d'acquisition. Une ligne de stock absente vaut zéro et interdit
la réservation. Un ajustement crée d'abord sa ligne à zéro par insertion
concurrente sûre, puis la verrouille. Vérifier tous les soldes et l'état avant
de committer les écritures ; toute exception annule l'ensemble, y compris un
lot multi-SKU. Aucun appel réseau sous verrou. L'unicité des identifiants et
les contraintes `CHECK` en base forment une seconde protection. Deux demandes
concurrentes sur la dernière unité se sérialisent : la seconde relit le solde
committé et échoue. Voir les [verrous de lignes PostgreSQL](https://www.postgresql.org/docs/current/explicit-locking.html)
et l'[isolation Read Committed](https://www.postgresql.org/docs/current/transaction-iso.html).

La disponibilité publique est indicative : le futur achat devra réserver dans
sa propre transaction et accepter une rupture entre affichage et validation.
Le port `CatalogVariantQueries` valide les références sans accès aux tables du
catalogue depuis `inventory`. Les SKU brouillon peuvent recevoir du stock mais
leur disponibilité est invisible au public. Les réservations internes exigent
une variante et un produit publiés au moment de l'appel ; le futur module
`sales` fixera en plus les offres et prix dans la commande.

## Contrats exposés

- `GET /api/v1/availability/{variantId}` : public, uniquement si la variante
  et son produit sont publiés ; réponse `{variantId, available}`, 404 sinon.
- `GET /api/v1/admin/stock/{variantId}` : `ADMIN`, solde physique, réservé et
  disponible, même pour un brouillon existant.
- `POST /api/v1/admin/stock/adjustments` : `ADMIN` + CSRF ; corps
  `{operationId, variantId, delta, reason}` avec entier non nul. Le sujet de
  session, et jamais le corps JSON, fournit l'acteur d'audit.
- Les ports Java `reserve(reservationId, lines)`, `release(reservationId)` et
  `consume(reservationId)` ne sont pas des routes HTTP. Le module `sales` sera
  seul responsable de l'autorisation des commandes et de leurs transitions.

Les erreurs d'entrée renvoient 400, les références invisibles/absentes 404, les
soldes insuffisants ou transitions incompatibles 409 ; l'accès suit la matrice
Spring Security (401 anonyme, 403 rôle ou CSRF insuffisant). Aucune commande ou
expédition n'est prétendue opérationnelle à cette étape.
