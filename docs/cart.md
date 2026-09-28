# Panier — références, reprise et estimation

Décisions prises avant l'implémentation, le 27 septembre 2026.

Le panier accepte deux sortes de lignes : `PRODUCT` désigne l'UUID d'une
variante/SKU du catalogue, `PACK` l'UUID d'une variante de pack. La clé est le
couple `(kind, offerId)` ; chaque quantité est entière entre 1 et 999, avec
100 lignes distinctes au maximum. Les doublons sont additionnés puis la limite
est contrôlée. Un pack reste une ligne commerciale unique, mais sa composition
est dépliée pour compter la demande de stock : pour chaque ligne, la quantité
du panier multiplie celle de chaque SKU ; les besoins de toutes les lignes
s'additionnent par UUID de SKU, avec détection des dépassements entiers. Aucune
réservation de stock n'a lieu dans le panier.

Le visiteur conserve uniquement `kind`, `offerId`, `quantity` et un UUID de
fusion dans `localStorage`, par appareil et navigateur. Il n'y stocke ni prix,
adresse, nom de produit, statut ni secret. Le client connecté possède un panier
par compte dans PostgreSQL (migration V7), lu et modifié uniquement à partir de
son identité serveur. Ce choix complète la décision initiale « panier navigateur »
pour répondre au besoin de persistance après connexion, sans synchroniser les
paniers de visiteurs anonymes entre appareils.

Après une connexion, y compris une session restaurée au rechargement, Angular
appelle `POST /cart/merge` avec le panier visiteur. Le serveur verrouille le
panier client, additionne les lignes communes dans une transaction et mémorise
le couple `(customerId, mergeId)` avec l'empreinte des lignes. Un rejeu identique
n'ajoute rien ; le même identifiant avec un autre contenu est refusé. Le client
efface `localStorage` seulement après la réponse réussie. En cas d'erreur, il
conserve les lignes et propose de réessayer. Une modification locale régénère
l'identifiant de fusion. Déconnexion : le panier du compte reste en base ; la
session redevenue visiteur retrouve seulement son panier local non fusionné.

Le serveur résout les offres publiées via les ports publics de `catalog` et
`packs`, puis lit la disponibilité par SKU via le port public d'`inventory`.
Une offre devenue invisible reste dans la liste mais est marquée indisponible.
L'estimation de sous-total est en TND à trois décimales et devient `null` si
une offre ne peut plus être résolue. Les ruptures portent sur la demande
**cumulée** par SKU. Ni frais ni total de livraison ne sont calculés à ce
stade : adresse, zone et règle commerciale sont traitées au checkout décrit
dans [checkout.md](checkout.md). Prix, composition, publication et stock y sont recalculés
et revalidés dans la transaction de commande ; aucune valeur du panier ou de
son estimation ne sera acceptée comme montant ou disponibilité contractuels.

## API `/api/v1`

- `POST /cart/estimate` public + CSRF : `{items:[{kind,offerId,quantity}]}`
  vers `{items,subtotalEstimate,shortages}`. Aucune écriture.
- `GET /cart` client `CUSTOMER` : même vue, avec `version` du panier persisté.
- `PUT /cart` client `CUSTOMER` + CSRF : `{version,items}` remplace les lignes
  après contrôle de version ; `409` si un autre onglet a modifié le panier.
- `POST /cart/merge` client `CUSTOMER` + CSRF : `{mergeId,items}` réalise la
  fusion idempotente et renvoie le panier actualisé.

Les réponses privées et les estimations portent `Cache-Control: no-store`.
Chaque ligne retournée contient ses références/quantité, un `label` et les
montants `unitPrice` / `lineEstimate`, plus `offerVersion` et `parentVersion`
(fiche produit ou pack). Ces champs sont `null` si l'offre n'est plus visible.
Les montants utilisent `{amount:"2.375",currency:"TND"}` ; ils ne sont jamais
des nombres flottants. Les ruptures ont la forme `{skuId,required,available}`.
Les UUID d'un autre compte ne sont jamais acceptés comme paramètre. Les rôles
internes n'ont pas de panier client. Les erreurs de validation valent `400`,
les conflits `409` et les refus d'accès `401/403`. Les gardes Angular n'ont
aucun rôle de sécurité.
