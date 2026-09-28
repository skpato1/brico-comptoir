# Checkout et commandes — contrat et invariants

Décisions avant implémentation, 28 septembre 2026. Le checkout accepte un
invité ou un compte `CUSTOMER`. Seul `CASH_ON_DELIVERY` existe ; aucun paiement
en ligne ni encaissement simulé. Le compte obligatoire de l'architecture
initiale est remplacé par ce choix explicite.

Les coordonnées de livraison sont validées à la commande : destinataire, téléphone tunisien à
huit chiffres (avec `+216` facultatif), rue, ville, code postal à quatre
chiffres, gouvernorat parmi les 24 codes tunisiens, pays `TN`. Il s'agit d'une
validation de forme et de zone, pas d'une vérification postale. Aucun tarif
commercial n'est inventé : `CHECKOUT_DELIVERY_FEE_TND` (forfait exact à trois
décimales) et `CHECKOUT_GOVERNORATES` définissent les zones servies. Sans
configuration, le checkout reste indisponible. Le profil test et l'exemple
local emploient des valeurs explicitement réservées aux tests/démonstrations.

Les coordonnées contiennent aussi `email` : facultatif pour un invité, normalisé
et validé côté serveur. Pour un compte connecté, le serveur prend l'adresse du
compte actif et ignore celle fournie par le client. Cet email entre dans
l'empreinte du récapitulatif et sert aux notifications, sans réécriture lors
des transitions. Sans email invité, le suivi reste dans la session navigateur.
La confirmation et chaque transition pertinente mettent leur email en outbox
dans la transaction de commande ; aucune communication SMTP ne s'y produit.

## Confirmation et concurrence

Les lignes comportent les versions de variante et de fiche affichées au
panier. La prévisualisation relit les offres publiées, déplie la composition
des packs et cumule les SKU communs. Toute version devenue différente exige
une actualisation du panier. Elle affiche lignes, composition, sous-total,
livraison, total TND et adresse ; aucune réservation à ce stade.

La confirmation porte l'empreinte du récapitulatif reçu. Le serveur reconstruit
les offres, versions des composants, prix, composition et frais ; toute
divergence retourne `409 OFFER_CHANGED` et demande un nouveau récapitulatif.
L'empreinte est aussi liée au compte/session qui a prévisualisé l'achat : une
session expirée ne peut pas rejouer silencieusement un achat client comme invité,
ni créer une seconde commande dans une nouvelle session avec un ancien récapitulatif.
Les montants client ne font jamais foi. Une modification catalogue committée
après l'instantané transactionnel concerne l'achat suivant, selon l'arbitrage
existant. Le stock est contrôlé sous verrous, indépendamment de l'estimation.

Placement en une transaction PostgreSQL `REPEATABLE READ` : clé unique par
propriétaire + UUID `Idempotency-Key`, empreinte canonique de la requête,
instantané d'offres, réservation agrégée via le port public inventory, commande
`CONFIRMED`, reçu idempotent. Même clé/contenu retourne la commande initiale,
même après changement catalogue ; autre contenu = `409 IDEMPOTENCY_CONFLICT`.
Une rupture ou erreur de persistance annule commande, réservation, mouvements
et clé. Les lignes/composition/prix de la commande sont immuables,
y compris pour le rôle SQL applicatif. Les mutations ne modifient que statut
et date de traitement, avec un journal append-only des transitions.

V11 sépare les coordonnées personnelles du snapshot commercial. Le propriétaire
peut les rectifier avant préparation dans la même zone, puis les retirer après
fin de commande ; elles passent alors en archive chiffrée selon la conservation
configurée. Les données financières restent immuables. Voir
[privacy-decisions.md](privacy-decisions.md). La réservation et la traçabilité
de stock ne sont pas modifiées par ces opérations.

Le stock verrouille les UUID de SKU dans son ordre stable existant. Une
collision d'écriture sous `REPEATABLE READ` ou un interblocage reprend la
transaction entière au maximum trois fois, jamais une partie des écritures.
Cela permet à deux placements simultanés de partager une clé sans créer deux
commandes, et empêche de réserver la dernière unité deux fois.

`CONFIRMED → PREPARING → SHIPPED → DELIVERED`, annulation depuis `CONFIRMED`
ou `PREPARING` pour l'administration ; le propriétaire peut annuler seulement
`CONFIRMED`. La commande est verrouillée avant réservation et stocks.
Annulation = libération ; expédition = consommation unique. Rejouer la même
transition est sans nouvel effet, transition incompatible = `409`. Une course
annulation/expédition produit un seul résultat valide. Pas de retour/restock
après expédition ni expiration automatique des réservations COD.

## Propriété et API `/api/v1`

Toutes les mutations exigent CSRF. Session invitée : identifiant aléatoire
serveur conservé dans la session `HttpOnly`, jamais fourni par le JSON. La
commande invitée est accessible seulement dans cette session. Son UUID seul
n'autorise aucune lecture. Après expiration/redémarrage, l'opérateur doit
la retrouver ; aucun lien public ou récupération par email n'est simulé.
Une connexion ultérieure n'attribue pas automatiquement les commandes invitées
au compte. Les commandes clients utilisent l'UUID authentifié. Les comptes
internes sans `CUSTOMER` ne peuvent pas acheter.

- `POST /checkout/preview` invité/client : `{items:[{kind,offerId,quantity,
  offerVersion,parentVersion}],address}` → récapitulatif et `quoteHash`.
- `POST /orders` invité/client + `Idempotency-Key: UUID` : même corps et
  `quoteHash` → `201` commande, y compris rejeu identique.
- `GET /orders/{id}`, `POST /orders/{id}/cancel` : propriétaire client/session
  invitée seulement ; autre propriétaire = `404`.
- `GET /orders?page=0&size=20` : historique du client `CUSTOMER`.
- `GET /admin/orders[/{id}]` : `ORDER_MANAGER` ou `ADMIN`, pagination et
  filtre `status` pour la liste.
- `POST /admin/orders/{id}/{prepare,ship,deliver,cancel}` : mêmes rôles.

Montants `{amount:"7.000",currency:"TND"}`, réponses `Cache-Control: no-store`.
Erreurs avec codes stables : `INVALID_CHECKOUT`, `DELIVERY_ZONE_UNAVAILABLE`,
`CHECKOUT_UNAVAILABLE`, `OFFER_CHANGED`, `STOCK_UNAVAILABLE`,
`IDEMPOTENCY_CONFLICT`, `INVALID_TRANSITION`, `ORDER_NOT_FOUND`.
Angular présente le récapitulatif avant le placement, conserve en mémoire la
même clé et le même corps lors d'un nouvel essai après réponse incertaine,
sans enregistrer l'adresse dans le navigateur. Après succès il retire
uniquement les lignes achetées si le panier n'a pas changé entre-temps ; un
panier client changé dans un autre onglet reste conservé.
