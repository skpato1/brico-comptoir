# Sécurité effective de BricoComptoir

État du code au 28 septembre 2026. Les résultats de vérification sont dans
[progress.md](progress.md) ; ce document décrit des protections techniques et
leurs limites, sans certification de conformité.

## Authentification et navigateur

Spring Security utilise une session serveur, sans JWT dans le stockage local.
Les mots de passe de 12 à 128 caractères sont hachés avec Argon2id. Le cookie
de session est `HttpOnly`, `SameSite=Lax`, `Secure` hors profils local/test,
sans domaine ; son identifiant change à la connexion. Déconnexion par POST,
expiration après 30 minutes d'inactivité. Le filtre relit état actif et version
du compte : retrait de rôle, désactivation ou nouveau mot de passe révoquent
les sessions précédentes.

Les mutations exigent un CSRF, y compris connexion, inscription, checkout
invité et récupération. Angular demande `GET /api/v1/auth/csrf`, lit
`XSRF-TOKEN` et renvoie `X-XSRF-TOKEN`. `spa()` est appliqué avant le dépôt
personnalisé pour conserver les attributs du cookie derrière la terminaison TLS.
Le cookie CSRF est volontairement lisible par Angular, contrairement à celui
de session. Même origine `/api/v1` via Nginx ; pas de CORS permissif.

Le reverse proxy remplace les en-têtes fournis par le client. En production,
`TRUSTED_PROXY_PATTERN` identifie uniquement ses pairs réels et le port backend
reste privé. HTTPS est requis. La limitation des essais de connexion,
inscription et récupération utilise l'adresse reconnue par cette chaîne.
Les sessions et compteurs sont en mémoire d'une instance : un redémarrage
déconnecte les utilisateurs ; plusieurs instances nécessitent un stockage partagé.

Récupération : réponse générique `202`, jeton aléatoire 256 bits, empreinte
SHA-256 en base, expiration 30 minutes, une utilisation. Le lien contient un
fragment URL et son contenu d'outbox est chiffré. Aucun mot de passe ou jeton
de récupération n'est renvoyé par l'API.

## Matrice RBAC et propriété

Les rôles internes sont cumulables. `ADMIN` n'implique pas `CUSTOMER` ; un
administrateur peut attribuer ce rôle explicitement après création. Une
inscription publique réussie crée uniquement `CUSTOMER` et rejette un champ
de rôle ajouté au corps.
La création initiale d'un compte interne refuse `CUSTOMER`. Aucun endpoint
public ne crée un administrateur.

| Opération | Visiteur | CUSTOMER | CATALOG_MANAGER | ORDER_MANAGER | ADMIN |
| --- | --- | --- | --- | --- | --- |
| Catalogue, packs publiés, rendus photo, disponibilité | Oui | Oui | Oui | Oui | Oui |
| Estimation du panier | Oui | Oui | Oui | Oui | Oui |
| Panier persisté/reprise | Non | Propre compte | Seulement avec CUSTOMER | Seulement avec CUSTOMER | Seulement avec CUSTOMER |
| Checkout et suivi client | Session invitée propriétaire | Propre compte | Seulement avec CUSTOMER | Seulement avec CUSTOMER | Seulement avec CUSTOMER |
| Produits, photos, CSV, catégories, marques, packs, accueil | Non | Non | Oui | Non | Oui |
| Commandes, coordonnées utiles, transitions et flux SSE | Non | Non | Non | Oui | Oui |
| Stocks et réglages de livraison | Non | Non | Non | Non | Oui |
| Comptes internes, rôles, désactivation | Non | Non | Non | Non | Oui |
| Profil, export, consentement et retrait usuels | Propre session invitée, opérations dédiées | Propre compte | Propre compte | Propre compte | Propre compte |
| Archives, gels de conservation, reprise d'outbox | Non | Non | Non | Non | Oui |

Routes inconnues fermées par défaut. Les services vérifient propriété,
permissions et transitions avec un acteur issu de la session, jamais du corps
JSON. Un UUID ou un email ne prouve aucune propriété. Une commande invitée
reste liée à sa session, même après connexion ; elle n'est pas rattachée
automatiquement à un compte. Les gardes et boutons Angular aident la navigation.

Les modifications de comptes sont sérialisées par verrou PostgreSQL ; le rôle
de l'acteur est revérifié après l'attente. Le dernier administrateur actif ne
peut pas être désactivé, rétrogradé ou fermé. Le bootstrap livré est réservé
au développement et exige des identifiants saisis à l'exécution. La procédure
du premier administrateur de production reste à valider par l'exploitant.
Voir les [contrats identité](identity.md) et [administration](administration.md).

## Stock, idempotence et fichiers

Commande, réservations agrégées et outbox sont committées ensemble. Les SKU
partagés entre packs et produits sont cumulés ; verrous dans un ordre stable,
contraintes `0 <= réservé <= physique`, clé d'idempotence par propriétaire,
transitions et conversion unique. La prévisualisation ne réserve rien.
Voir [checkout.md](checkout.md) et [inventory.md](inventory.md).

Images réservées au catalogue autorisé : signature/décodage JPEG ou PNG,
correspondance avec le type déclaré, 6 Mio par fichier, 1 à 4 fichiers par lot,
12 par produit, 320 à 6000 pixels et 24 MP au maximum. Rendus JPEG réencodés,
dimensions réduites, ordre et image principale en PostgreSQL ; originaux et
bucket privés. Les listes utilisent les dérivés. La route Nginx d'import photo
autorise 26 Mio, sans augmenter les autres routes. Le CSV est analysé côté
serveur, validé avant application et traité transactionnellement.

Pour Supabase Storage, le bucket doit rester privé et les clés S3 doivent
rester dans Render. Ces clés contournent les RLS et couvrent tous les buckets
du projet : prévoir un projet dédié, sans clé dans Angular ni politique publique.
Les API BricoComptoir continuent à vérifier rôle, propriété et publication des
produits/packs. Voir [supabase-storage.md](supabase-storage.md).

## Données personnelles et secrets

Consentement marketing explicite, distinct de la commande, jamais précoché.
Exports et rectifications exigent le propriétaire ; opérations sensibles avec
réauthentification. Les coordonnées archivées sont chiffrées et accessibles
uniquement à ADMIN dans un dossier audité. Les données commerciales et
références conservées restent pseudonymisées, sans promesse d'anonymat absolu.
[privacy-decisions.md](privacy-decisions.md) précise collecte, durées et droits.

Logback supprime messages libres, arguments et piles d'exception. Nginx ne
conserve ni accès bruts ni erreurs contenant des URI. Réponses privées
`no-store`, erreurs JSON/persistance génériques. Les logs de l'hébergeur, du
SMTP et de la base nécessitent leur propre politique.

Les exemples d'environnement laissent mots de passe et clés vides. Le lanceur
génère les secrets locaux dans `.env`, ignoré. En production : gestionnaire de
secrets, comptes SQL séparés, accès S3 restreint, clés Base64 de 32 octets
`MAIL_OUTBOX_KEY` et `DATA_ARCHIVE_KEY` sauvegardées séparément. Pas de clé
dans Angular, dans une image de déploiement ou dans Git. Les rapports, backups,
exports, `.local/`, fichiers de clés et artefacts sont exclus. Exécuter
`node scripts/check-release.mjs` avant publication : contrôle des chemins,
liens locaux et détection ciblée des valeurs secrètes connues et signatures
de credentials, y compris l'historique ; ce contrôle complète la revue humaine.

## Limites connues avant ouverture

- Aucun MFA, partage de session/limiteur entre instances, chiffrement complet
  des volumes ou rotation automatique des clés n'est livré.
- SMTP à accusé ambigu peut produire une seconde remise malgré déduplication
  et Message-ID stable ; une commande validée reste persistée lors d'une panne.
- PostgreSQL et S3 ne forment pas une transaction distribuée : un rollback
  après écriture objet peut laisser un objet privé orphelin ; prévoir réconciliation.
- Les logs volontairement réduits imposent une supervision par codes, santé et
  état d'outbox ; l'exploitant doit définir alertes, réponse aux incidents et restauration.
- TLS, hébergement, prestataires, notice/durées légales et procédure de création
  du premier ADMIN doivent être décidés avant ouverture. Voir [deployment.md](deployment.md).
