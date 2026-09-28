# Déploiement et exploitation

État au 28 septembre 2026. Le Compose fourni est un environnement **local**,
avec Mailpit et des frais fictifs ; il n'est pas une configuration de production.
La [sécurité](security.md), les [décisions de confidentialité](privacy-decisions.md)
et le [suivi des vérifications](progress.md) complètent cette procédure.

## Prérequis et artefacts

Docker Linux et Compose 2.24.4+, Node 24.18.0. Java 21 est requis uniquement
pour lancer Maven/backend hors conteneur ; Maven 3.9.16 est fourni par wrapper.
Prévoir de l'espace pour les images, caches, volumes et sauvegardes, avec alerte
avant saturation. Une dizaine de Go libres est une marge pratique pour les
builds de ce dépôt, pas une capacité de production garantie.

`backend/Dockerfile` produit un JAR puis une image JRE non privilégiée ;
`frontend/Dockerfile` compile Angular et sert les fichiers avec Nginx non
privilégié. Les tests sont exécutés séparément par `verify` et la CI, car la
construction du JAR dans l'image saute les tests. Déployer des images validées
identifiées par commit/digest et garder la version des migrations correspondante.

## Angular sur Vercel, API Spring Boot hébergée séparément

Le dépôt est un monorepo : Angular se trouve dans `frontend/`, tandis que la
racine n'est pas une application Node. Le [vercel.json](../vercel.json) fourni
installe son lockfile et appelle [build-vercel.mjs](../scripts/build-vercel.mjs).
Ce build compile Angular en production puis génère `.vercel/output/` selon la
[Build Output API v3](https://vercel.com/docs/build-output-api), avec seulement
les fichiers navigateur. La configuration générée rétablit les URL Angular
directes (`/catalogue`, `/packs/{id}`, `/gestion`, etc.) sans transformer une
erreur API ou un fichier JavaScript absent en réponse HTML réussie.

Dans **le projet Vercel BricoComptoir**, importer `skpato1/brico-comptoir` et
sélectionner la branche `main`. Régler **Root Directory sur la racine du dépôt**
(champ vide), **Framework Preset : Other**, **Node.js : 24.x**. Les commandes
d'installation/build viennent du fichier ; supprimer les anciens overrides et
laisser **Output Directory sans override** : Vercel consomme `.vercel/output`.
Ne pas choisir `frontend/` comme Root Directory avec cette configuration.
Le projet et son domaine doivent appartenir à l'équipe Vercel connectée.

Ajouter `BRICO_API_ORIGIN` dans les environnements Vercel appropriés, puis
redéployer : une origine DNS publique HTTPS, par exemple `https://api.example.com`
(exemple fictif), sans `/api`, paramètres ni identifiants. Cette variable ne
contient aucun secret. Le build refuse localhost, adresses IP, HTTP et URL avec
identifiants. Ne pas mettre les secrets PostgreSQL/S3/SMTP dans le frontend.
Pour vérifier le même artefact localement depuis la racine :

```sh
npm ci --prefix frontend
node --test scripts/vercel-output.test.mjs
# Injecter BRICO_API_ORIGIN dans l'environnement, sans secret.
node scripts/build-vercel.mjs
```

Le navigateur continue d'utiliser `/api/v1/...`. La
[réécriture externe](https://vercel.com/docs/routing/rewrites) vers Spring
conserve ce préfixe et la même origine publique pour cookies/session et CSRF ;
aucune désactivation de CSRF ni ouverture générale de CORS n'est ajoutée.
Les réponses API sont exclues des caches navigateur/CDN. Sans
`BRICO_API_ORIGIN`, le frontend peut être déployé mais toutes les routes API
retournent **503 `API_NOT_CONFIGURED`** ; l'interface annonce son indisponibilité.
Ce mode ne constitue pas une boutique opérationnelle.

**Une API sur localhost n'est pas accessible depuis Vercel.** Héberger le
backend Java 21 comme service durable avec `backend/Dockerfile`, sous `prod`,
PostgreSQL, S3 privé et SMTP configurés comme ci-dessous. Renseigner
`APP_PUBLIC_URL` avec l'URL HTTPS de la boutique. Les sessions et limiteurs
actuels sont en mémoire ; conserver une seule instance applicative et le
worker d'outbox actif jusqu'à une évolution explicitement testée. La perte de
session lors d'un redémarrage impose une reconnexion, sans perte des commandes.
Les runtimes
[OCI Vercel en bêta](https://vercel.com/docs/functions/container-images) existent,
mais leur mise en veille/scaling n'est pas la procédure validée pour ces
sessions et tâches planifiées. Docker Compose ne provisionne pas ces services
sur Vercel ; aucun tunnel public du développement local n'est fourni.

Avant ouverture, vérifier sur le domaine réel : santé PostgreSQL, connexion et
déconnexion, cookies `Secure`/`HttpOnly`/`SameSite`, refus d'écriture sans CSRF,
commande et idempotence, lot de quatre photos de 6 Mio, images, SSE et reconnexion,
envoi SMTP/outbox. Vérifier les limites de taille/durée du proxy du prestataire
et sa chaîne de confiance : l'origin API doit être protégé, les en-têtes client
réécrits et les pairs ingress précisément approuvés. Ne pas faire confiance à
tout Internet pour `TRUSTED_PROXY_PATTERN`. Ces contrôles HTTPS ne sont pas
remplacés par les tests du routage généré ou les tests locaux Docker.

Un `NOT_FOUND` Vercel sur `/` indique qu'aucun frontend valide n'est servi :
vérifier projet/domaine/équipe, Root Directory, commandes et deployment Ready.
Un 503 `API_NOT_CONFIGURED` demande de renseigner l'origine puis redéployer.
Un 502/504 avec origine configurée demande de vérifier le backend et le réseau,
sans remplacer l'API réelle par des données de démonstration.

## Variables à injecter

[.env.example](../.env.example) décrit le local ;
[.env.production.example](../.env.production.example) est un inventaire sans
secrets. Ne pas copier les valeurs locales comme identifiants commerciaux.
Les secrets de production viennent du gestionnaire de secrets du prestataire,
jamais d'un fichier committé, d'un argument visible ou d'Angular.

| Variables | Usage et décision |
| --- | --- |
| `BRICO_API_ORIGIN` | Vercel uniquement : origine HTTPS publique du backend ; absente = API indisponible, pas de secrets |
| `SPRING_PROFILES_ACTIVE=prod`, `SERVER_PORT` | Profil sécurisé et port backend privé |
| `DB_URL` | JDBC PostgreSQL, base/région choisies ; TLS vérifié (`sslmode=verify-full`) et certificat de confiance |
| `DB_USERNAME`, `DB_PASSWORD` | Rôle applicatif DML, sans superutilisateur ni droit de migration |
| `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD` | Propriétaire du schéma et des migrations, secret séparé |
| `MEDIA_ENDPOINT`, `MEDIA_ACCESS_KEY`, `MEDIA_SECRET_KEY`, `MEDIA_BUCKET` | Stockage S3 compatible privé, compte restreint au bucket |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | Fournisseur SMTP choisi, accès et délivrabilité vérifiés |
| `SMTP_AUTH`, `SMTP_STARTTLS`, `SMTP_STARTTLS_REQUIRED` | Activer authentification et TLS selon le fournisseur ; pas de Mailpit commercial |
| `MAIL_FROM`, `APP_PUBLIC_URL` | Expéditeur validé, URL publique HTTPS utilisée dans les liens |
| `MAIL_OUTBOX_KEY`, `DATA_ARCHIVE_KEY` | Deux clés indépendantes Base64 de 32 octets, à sauvegarder et préserver lors de restauration |
| `MAIL_WORKER_ENABLED`, `MAIL_POLL_MS` | Worker d'outbox activé par défaut, cadence 2000 ms ; arrêter pour une sauvegarde cohérente |
| `TRUSTED_PROXY_PATTERN` | Expression Java des seuls pairs ingress réellement approuvés ; jamais `.*` ni plage publique |
| `CHECKOUT_DELIVERY_FEE_TND`, `CHECKOUT_GOVERNORATES` | Forfait exact TND à trois décimales et codes des gouvernorats desservis ; vides en production tant que non validés |
| `PRIVACY_CONTACT_DAYS`, `PRIVACY_CART_DAYS`, `PRIVACY_MAIL_DAYS`, `PRIVACY_AUDIT_DAYS` | Durées techniques configurables, à justifier |
| `PRIVACY_LEGAL_DAYS`, `PRIVACY_RETENTION_ENABLED` | Durée des coordonnées archivées et activation de conservation ; `0` bloque l'archivage des coordonnées de commande, activation refusée sans durée positive |

Les paramètres `POSTGRES_*`, `MINIO_ROOT_*` et les ports hôte du Compose
initialisent uniquement l'environnement local. Les réglages de livraison
enregistrés via ADMIN en base prennent priorité sur les variables ; vérifier
les données persistées après une restauration.

## PostgreSQL et Flyway

PostgreSQL 18.6, un schéma `bricocomptoir`, rôles migrateur/application distincts.
Le script local [10-create-roles.sh](../infra/postgres/10-create-roles.sh) les
crée seulement à l'initialisation d'un volume neuf. Changer `.env` ne fait pas
tourner les secrets d'une base existante ; coordonner rotation SQL et déploiement.

Douze migrations versionnées V1–V12 : permissions, identité, catalogue,
médias, inventaire, packs, panier, commandes, réglages, outbox, confidentialité.
Flyway les applique au démarrage et valide les checksums ; `clean` est désactivé.
Les adaptateurs métier utilisent JDBC. La configuration Hibernate conserve
`ddl-auto: validate`, sans génération de schéma ; aucune entité JPA métier n'est
actuellement définie. Une nouvelle évolution s'ajoute après V12 ; ne pas réécrire
une migration déjà appliquée et ne pas faire de `repair` pour masquer un écart.

Avant mise à niveau : sauvegarde restaurable, revue SQL, migration testée sur
base vide et sur une copie du schéma existant. Le retour à une ancienne image
ne restaure pas une ancienne base ; définir la compatibilité ou une restauration
contrôlée. Une seule instance applique les migrations lors de cette procédure.

## Stockage d'images et email

Le stockage objet est derrière `ObjectStorage` ; l'adaptateur livré est MinIO/S3.
Les binaires restent dans le bucket privé, les métadonnées dans PostgreSQL.
L'API sert les JPEG carte/fiche ; les originaux n'ont pas de route publique.
Créer/autoriser le bucket avec les droits minimum pour lecture, écriture et
suppression ; tester téléversement, rendu et indisponibilité du fournisseur.
Sauvegarder ensemble objets et métadonnées ; définir revue des objets orphelins,
quotas, rétention et droits sur les images.

`EmailProvider` est implémenté par SMTP. Confirmation et changements de statut,
récupération et rectification sont inscrits dans une outbox chiffrée au commit.
Le worker possède un bail de deux minutes, cinq essais avec temporisation,
puis état d'échec consultable/rejouable par ADMIN. Une panne SMTP ne supprime
pas la commande. Prévoir SPF/DKIM/DMARC, alertes sur échecs et délai d'envoi,
destinataires de test puis essai de délivrabilité réel. SMTP ne garantit pas
une remise unique après perte de son accusé.

## HTTPS, réseau et premier administrateur

Servir Angular et `/api/v1` sous la même origine HTTPS. Seul l'ingress est public ;
backend, PostgreSQL et stockage objet sont privés. L'ingress fournit les en-têtes
de transfert validés ; `TRUSTED_PROXY_PATTERN` doit correspondre à son adresse
effective. Valider la chaîne si plusieurs proxies sont utilisés : le Nginx local
fourni est HTTP et n'est pas la recette TLS d'un hébergeur.

Conserver HTTP/1.1 et désactiver tampon/cache du SSE, avec délai de lecture
supérieur aux heartbeats ; garder les contrôles de session/RBAC. Autoriser
26 Mio uniquement sur la route photo, en maintenant les bornes serveur et les
petites limites sur les autres routes. Ne pas exposer consoles MinIO, Mailpit,
socket Docker, base ou endpoints techniques supplémentaires.

Vérifier cookies de session et CSRF `Secure`/`SameSite`, rotation/révocation,
rejet CSRF et en-têtes forgés, propriétaire des commandes/export et périmètre
des trois rôles internes. Le bootstrap `LocalAdminBootstrap` est volontairement
désactivé en `prod` : convenir d'une procédure de provisionnement du premier
ADMIN, avec secret unique et accès opérateur contrôlé, avant ouverture. Ne pas
activer le profil local sur une production pour contourner cette restriction.

## Sauvegarde et restauration

Sauvegarder PostgreSQL, objets, version/digests applicatifs et migrations dans
une même fenêtre sans écritures. Les clés d'outbox et d'archives sont gardées
dans un coffre séparé mais restaurable. Un dump ne contient pas les rôles du
cluster ; reconstruire leurs propriétaires/droits avant import. Chiffrer les
sauvegardes, limiter leurs accès et conserver une copie hors hôte. Définir
fréquence, durée, RPO/RTO, contrôles d'intégrité et répétition de restauration.

Exemple **local seulement**, après création de `.local/` :

```sh
docker compose stop frontend backend
docker compose exec -T postgres pg_dump -U postgres -d bricocomptoir -Fc -f /tmp/brico.dump
docker compose cp postgres:/tmp/brico.dump .local/brico.dump
docker compose stop minio
```

Ne pas faire transiter un dump binaire par un pipeline texte PowerShell. Archiver
à froid le volume MinIO complet, métadonnées internes incluses, avec un auxiliaire
monté en lecture seule. L'automatisation concrète est dans
[check-restore.mjs](../scripts/check-restore.mjs), exclusivement destinée au jeu E2E.
Ne pas copier le volume PostgreSQL actif.

Restaurer dans **une base et un volume objet neufs**, projet/ports distincts ;
le backend cible reste arrêté. Après création des rôles et copie du dump dans
le conteneur cible :

```sh
docker compose -p bricocomptoir-restauration --env-file .local/restauration.env exec -T postgres pg_restore -U postgres -d bricocomptoir --exit-on-error --single-transaction /tmp/brico.dump
docker compose -p bricocomptoir-restauration --env-file .local/restauration.env exec -T postgres psql -U postgres -d bricocomptoir -c ANALYZE
```

Vérifier explicitement la cible avant import. Restaurer MinIO à froid dans le
volume neuf avec ses propriétaires, injecter les mêmes clés, puis démarrer les
images correspondant à la sauvegarde. Vérifier Flyway, santé, comptes/droits,
instantanés de commandes, stock, hash des images, déchiffrement et état d'outbox.
Rejouer les retraits intervenus depuis la sauvegarde avant remise en service.
L'archive de volume local ne remplace pas une procédure de sauvegarde S3 du
prestataire. Mailpit n'est pas une archive commerciale des emails.

Essai reproductible sur les données fictives isolées :

```sh
node scripts/e2e.mjs up
node scripts/e2e.mjs test
node scripts/check-restore.mjs
node scripts/e2e.mjs down
```

Les volumes E2E sont conservés pour inspection. Ne pas définir
`BRICO_E2E_SKIP_DOCKER_CONTROL` pour une validation complète : cette option
ignore explicitement le scénario de panne SMTP.

## Conditions d'ouverture

- Builds, tests unitaires, PostgreSQL/MinIO, parcours réels et restauration
  passent sur les sources/digests à déployer ; lire les limites de [progress.md](progress.md).
- Catalogue, compositions, prix, photos, frais/zones, fiscalité et processus de
  livraison validés ; aucun script `infra/demo/` utilisé comme catalogue réel.
- Domaine/HTTPS, secrets, rôles SQL, stockage privé, SMTP et premier ADMIN
  provisionnés ; essais de permissions et d'idempotence effectués.
- Prestataires, régions/transferts, notice, durée légale, gestion des droits et
  sauvegardes validés avec les responsables compétents.
- Supervision du disque, disponibilité, outbox et conservation, exploitation des
  sauvegardes et réponse aux incidents définies. Aucun de ces choix externes
  n'est automatiquement configuré par le dépôt.
