# Emails et événements gestionnaire

La confidentialité complète cette étape : V12 associe les emails de commande à
un scope opaque, annule leurs payloads en attente au retrait des données et purge
l’outbox selon la conservation configurée. La vérification d’une rectification
d’e-mail utilise le même port et les mêmes reprises, avec jeton expirant en
30 minutes. Aucun choix marketing n’est déduit des emails transactionnels.
Voir [privacy-decisions.md](privacy-decisions.md).

Décisions avant implémentation : un module `notifications`, appelé uniquement
par ses ports publics depuis les adaptateurs de `sales` et `identity`. Aucun
SMTP pendant une transaction métier. Commande, réservation, email en outbox et
événement gestionnaire sont validés ou annulés ensemble dans PostgreSQL.

La confirmation et les changements PREPARING, SHIPPED, DELIVERED, CANCELLED
utilisent l'email du compte actif pour un achat connecté, ou l'email facultatif
saisi au checkout invité. Sans email invité, la commande fonctionne et sa
confirmation reste consultable dans la session navigateur. Aucun email n'est
envoyé à une adresse choisie par un gestionnaire lors d'une transition.

L'outbox a une clé unique par commande/statut, un payload chiffré AES-256-GCM
(destinataire, texte et lien de récupération), une échéance et un état.
`MAIL_OUTBOX_KEY` est une clé Base64 de 32 octets injectée hors Git ; sa perte
rend les messages en attente illisibles. Le token de récupération reste haché
dans identity, à usage unique et valable 30 minutes. Les anciennes demandes
en attente sont annulées ; les messages expirés ne partent pas.

Un worker réclame une ligne avec `FOR UPDATE SKIP LOCKED`, valide une lease
de deux minutes, ferme cette transaction puis appelle le port `EmailProvider`.
Succès ou échec sont enregistrés dans une autre transaction avec comparaison
du token de lease. Cinq essais maximum, reprises après 15, 30, 60 puis 120
secondes ; lease abandonnée récupérable après redémarrage. L'administrateur
peut relancer un échec terminal non expiré via une API CSRF protégée.
Les erreurs journalisées sont des codes, sans adresse ni lien secret.

La clé unique et les verrous empêchent les doubles soumissions métier et les
workers concurrents. Le `Message-ID` SMTP reste identique à chaque reprise.
SMTP ne garantit pas exactement un envoi : si le fournisseur accepte un message
puis que la réponse est perdue, une reprise peut le remettre. Ce risque est
documenté ; un futur fournisseur avec clé d'idempotence peut remplacer SMTP.

`GET /api/v1/admin/order-events/stream` est un flux SSE même origine, session
requise, rôles `ORDER_MANAGER` ou `ADMIN`. Il ne contient que l'identifiant de
commande, la date et le type `ORDER_CREATED`. Une lecture JSON bornée
`GET /api/v1/admin/order-events?after=…&size=…` permet aussi de retrouver les
événements. `Last-Event-ID` (reconnexion native) ou `after` (nouvelle connexion)
reprend après le dernier événement reçu. Sans curseur : événements futurs.

Les curseurs sont attribués sous verrou d'une ligne singleton dans la transaction
productrice : aucune transaction validée tardivement ne peut être sautée par
un curseur plus grand déjà visible. Les événements sont conservés sans purge
automatique au lancement. Le flux lit des lots de 100, envoie des heartbeats,
expire après une minute et revérifie session, compte actif, version et rôle
pendant son ouverture. Nginx désactive son buffering pour cette route.
Le transport utilise [SseEmitter de Spring MVC](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/servlet/mvc/method/annotation/SseEmitter.html).
Angular garde le curseur par compte dans la session du navigateur, ignore
les replays déjà vus et reconnecte avec délai borné. La liste de commandes
reste disponible indépendamment du flux.

Une récupération a toujours une limite absolue de 30 minutes, même si SMTP
reste indisponible. Une annulation d'email empêche un envoi encore en attente ;
elle ne peut pas rappeler un message déjà remis au fournisseur. Les anciens
liens remis restent inutilisables après remplacement/consommation du jeton.
Le journal SSE et les métadonnées d'outbox restent conservés au lancement ;
définir une rétention et une supervision des états FAILED avant exploitation.

Administration de l'outbox : `GET /api/v1/admin/mail-outbox?page=0&size=20`
(métadonnées sans destinataire/texte), `POST /api/v1/admin/mail-outbox/{id}/retry`
(`ADMIN` seulement, CSRF, 409 si état/échéance incompatible).
