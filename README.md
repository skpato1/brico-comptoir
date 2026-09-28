# BricoComptoir

Boutique de quincaillerie tunisienne : Java 21, Spring Boot, Spring Security,
Angular, PostgreSQL et Flyway. Monolithe modulaire hexagonal ; les comptes,
sessions, récupération de mot de passe, catalogue de produits, photos, import CSV,
inventaire par SKU, packs, panier et commandes avec paiement à la livraison sont en place.

## Fonctionnalités livrées et reprise

- Parcours public mobile : accueil, recherche/filtrage de produits, packs et
  variantes, fiches avec composition, panier invité/client et reprise après
  connexion, checkout tunisien, confirmation et suivi de ses commandes.
- Catalogue administrable : catégories hiérarchiques, marques, SKU, prix TND
  précis, brouillons/publication, photos responsives et import CSV avec aperçu.
- Packs sans stock indépendant ; demande SKU cumulée et réservation atomique,
  placement idempotent, annulation et conversion unique à l'expédition.
- Administration selon rôles : catalogue/packs/accueil, commandes/SSE, stocks,
  frais de livraison et comptes ; autorisations côté backend.
- Emails transactionnels en outbox, récupération, consentement marketing
  distinct, consultation/export/rectification et retrait des données usuelles.

Limites : paiement à la livraison seulement, forfait de livraison unique, liste
publique de packs non paginée, sessions/limiteurs sur une instance et pas de MFA.
SMTP peut remettre un doublon après accusé perdu ; des objets privés orphelins
peuvent demander réconciliation. Les choix commerciaux, juridiques et les
prestataires de production restent à valider. Les résultats actuels figurent
dans [progress.md](docs/progress.md), sans assimilation à une ouverture commerciale.

Structure : `backend/src/main/java/tn/bricocomptoir/` contient les neuf modules
hexagonaux et `bootstrap/`, `backend/src/main/resources/db/migration/` les
douze migrations, `frontend/src/app/{core,features,shared}/` Angular, `e2e/`
les parcours réels, `infra/` l'infrastructure locale et les exemples, `scripts/`
les commandes de vérification. La persistance métier est JDBC/PostgreSQL.

Pour reprendre : lire [AGENTS.md](AGENTS.md),
[architecture.md](docs/architecture.md), [security.md](docs/security.md) et
[deployment.md](docs/deployment.md). Aucun compte/mot de passe universel n'est
livré. Le premier ADMIN local est créé par la commande ci-dessous ; les autres
comptes internes sont créés par un ADMIN et les clients par inscription.

Lire [l'architecture](docs/architecture.md), la [roadmap](docs/roadmap.md), les
[règles de contribution](AGENTS.md) et les [vérifications réalisées](docs/progress.md).
Les [permissions et routes identity](docs/identity.md) et la
[matrice de sécurité](docs/security.md) décrivent les accès effectifs.
Le [contrat du catalogue](docs/catalog.md) détaille la lecture et l'administration.
Voir aussi les [invariants de stock](docs/inventory.md), les [offres de packs](docs/packs.md)
la [reprise du panier](docs/cart.md), le [checkout et les commandes](docs/checkout.md)
et l'[interface publique Angular](docs/storefront.md).

La boutique locale s'ouvre sur l'accueil : `/solutions` et `/packs` pour les
besoins, `/catalogue` pour les produits et la recherche, puis `/panier` et
`/commande`. `/confirmation/:id` relit la commande autorisée par la session.
Les liens des fiches et les filtres du catalogue peuvent être partagés ;
les coordonnées et commandes restent privées. Le compte et les accès de
gestion sont disponibles depuis `/compte`.

## Démarrage complet avec Docker

Prérequis : Docker Engine/Desktop avec conteneurs Linux et Docker Compose **2.24.4** ou
ultérieur, Node.js **24.18.0**. Java n'est pas nécessaire sur l'hôte dans ce mode.
Les premiers builds téléchargent les dépendances et compilent MinIO ; prévoir
plusieurs minutes, suffisamment de mémoire pour Docker et une marge d'espace
disque (au moins 10 Go recommandés pour reconstruire et tester confortablement).

Depuis la racine, sous PowerShell, Bash ou un terminal équivalent :

```sh
node scripts/init-local-env.mjs
docker compose config --quiet
docker compose up --build --detach --wait --wait-timeout 180
docker compose ps
node scripts/check-local.mjs
```

Le script génère `.env` avec des secrets aléatoires ; il ne remplace jamais un
fichier existant et n'affiche pas les secrets. `.env` est ignoré par Git.
`docker compose config --quiet` valide sans afficher les valeurs sensibles.
Les services écoutent uniquement sur l'interface locale de l'hôte.

Les cinq services de la boutique utilisent `restart: unless-stopped` pour
reprendre après un redémarrage Docker. Un arrêt explicite reste respecté. Pour
relancer des conteneurs déjà créés sans reconstruire les images ni recréer les
volumes : `docker compose start --wait --wait-timeout 180`, puis
`node scripts/check-local.mjs`. Conserver suffisamment d'espace libre sur le
disque qui héberge Docker ; une saturation peut bloquer le moteur et les ports.

| Service | Adresse par défaut | Usage |
| --- | --- | --- |
| Angular | http://localhost:4200 | Santé, identité, catalogue, packs et panier |
| API | http://localhost:8080/api/v1/health | Santé réelle, dépend de PostgreSQL |
| PostgreSQL | localhost:5432 | Base `bricocomptoir`, comptes dans `.env` |
| MinIO S3 | http://localhost:9000 | Photos privées du catalogue, bucket créé au premier téléversement |
| Console MinIO | http://localhost:9001 | Connexion avec les valeurs `MINIO_ROOT_*` de `.env` |
| Mailpit | http://localhost:8025 | Boîte locale ; SMTP sur localhost:1025 |

Les ports sont configurables dans `.env`. Mailpit reçoit les emails de
récupération du module `identity` ; MinIO conserve les photos, tandis que
PostgreSQL conserve uniquement leurs métadonnées. Depuis les conteneurs, leurs
adresses seront `http://minio:9000` et `mailpit:1025`.

La page Angular appelle `/api/v1/health` via Nginx sur la même origine. Elle
affiche une indisponibilité en cas de réponse incorrecte, d'erreur HTTP/réseau ou
de délai dépassé après cinq secondes. Le catalogue et les packs publiés sont
publics ; leurs écritures exigent `CATALOG_MANAGER` ou `ADMIN`. Les autres routes API restent
fermées par défaut, sauf les routes `identity` documentées. Aucun compte de test ni mot de
passe Spring généré n'est disponible. L'inscription crée seulement un client.

## Premier administrateur local et comptes

Le premier administrateur est créé une seule fois par une commande explicite,
avec un email et un secret choisis au moment de l'exécution. Après le démarrage
Compose, sous PowerShell :

```powershell
$env:BRICO_BOOTSTRAP_ADMIN_EMAIL = Read-Host 'Email du premier administrateur'
$bricoSecurePassword = Read-Host 'Mot de passe (12 caractères minimum)' -AsSecureString
$env:BRICO_BOOTSTRAP_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $bricoSecurePassword).Password
try {
  docker compose run --rm --no-deps -e BRICO_BOOTSTRAP_ADMIN_EMAIL -e BRICO_BOOTSTRAP_ADMIN_PASSWORD backend --brico.bootstrap-admin=true --server.port=0
} finally {
  Remove-Item Env:BRICO_BOOTSTRAP_ADMIN_EMAIL, Env:BRICO_BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
}
```

Cette commande est limitée au profil `local` et refuse de s'exécuter si un
administrateur actif ou un compte de même email existe déjà. Elle n'inscrit
aucun identifiant dans le dépôt. Les comptes internes supplémentaires se créent
avec une session `ADMIN` via l'API ; `CATALOG_MANAGER` et `ORDER_MANAGER` ne
donnent aucun droit d'administration des comptes. La matrice est dans
[identity.md](docs/identity.md).

La page Angular permet l'inscription, la connexion, la déconnexion et la
récupération du mot de passe. Mailpit reçoit le lien local sur
http://localhost:8025. Le lien expire au bout de 30 minutes et ne fonctionne
qu'une fois. La page demande automatiquement un jeton CSRF avant chaque
mutation. La session, et non le navigateur, porte les droits effectifs.

Les boutons « Ajouter au panier » acceptent les variantes de produits et de
packs publiées. Le visiteur garde ses références et quantités dans ce
navigateur ; à la connexion, elles sont fusionnées une seule fois dans le
panier du compte. Une erreur de reprise conserve les lignes locales et affiche
un bouton de nouvelle tentative. Le sous-total est indicatif, hors livraison,
et ne réserve aucun stock. Les contrats et limites sont dans [cart.md](docs/cart.md).

Le checkout collecte une adresse tunisienne et affiche le récapitulatif serveur
avant la confirmation, avec paiement à la livraison uniquement. Le forfait
`CHECKOUT_DELIVERY_FEE_TND` et la liste de codes `CHECKOUT_GOVERNORATES` sont
configurables. Compose et le profil local utilisent **7.000 TND, TUNIS et SFAX
uniquement comme démonstration**, à faire valider avant ouverture. En production,
ces variables restent vides dans l'exemple ; le checkout répond indisponible
tant qu'elles ne sont pas renseignées. Le code postal et le téléphone sont
validés sur leur forme, sans service de vérification postale.

La commande réservée est consultable par son propriétaire ; un invité doit
conserver sa session navigateur et sa référence. Une connexion ne rattache pas
automatiquement ses commandes invitées au compte. Les comptes `ORDER_MANAGER`
ou `ADMIN` disposent de la liste paginée et des actions préparer, annuler,
expédier et marquer livrée. L'expédition consomme le stock une seule fois.
Voir [checkout.md](docs/checkout.md) pour les transitions et limites.

## Catalogue local

La page Angular lit les catégories, marques et produits publiés. Après connexion
avec un compte `CATALOG_MANAGER` ou `ADMIN`, « Gérer le catalogue » permet de
créer et modifier catégories, marques, produits et variantes/SKU. Une fiche
publiée sans variante publiée reste invisible au public. Le backend contrôle les
rôles, les versions et la publication ; les prix sont des décimaux TND précis.
La gestion des photos permet 1 à 4 JPEG/PNG par envoi, jusqu'à 12 par produit,
avec ordre et image principale. Les cartes et fiches chargent des rendus
redimensionnés ; le bucket et les originaux restent privés. L'import CSV propose
un aperçu des erreurs avant application. Son format exact et ses limites sont
dans [le contrat du catalogue](docs/catalog.md).
Voir [le contrat API et les arbitrages](docs/catalog.md).

La base démarre sans produits commerciaux préchargés. Pour afficher quelques
**données entièrement fictives** dans la base locale Compose, exécuter sous
PowerShell depuis la racine :

```powershell
Get-Content -Raw -Encoding UTF8 infra/demo/catalog-demo.sql | docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d bricocomptoir
Get-Content -Raw -Encoding UTF8 infra/demo/packs-demo.sql | docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U postgres -d bricocomptoir
```

Les cinq packs du second script restent en brouillon ; tous ces exemples sont
marqués `DÉMO`/`demo: true`. Ces fichiers ne sont pas des migrations et ne
doivent jamais alimenter un catalogue réel.

Commandes d'exploitation locale :

```sh
docker compose logs --tail=100 backend
docker compose logs --tail=100 postgres
docker compose stop
docker compose start --wait
docker compose down
```

`stop` et `down` conservent les volumes de PostgreSQL, MinIO et Mailpit.
Changer les mots de passe dans `.env` ne modifie pas les rôles d'un volume
PostgreSQL déjà initialisé : effectuer une rotation SQL correspondante, ou
recréer explicitement une base de développement dont les données sont jetables.
Ne pas supprimer de volume contenant des données à conserver.

## Développement avec rechargement Angular

Prérequis supplémentaires : un JDK **21** pour lancer Spring hors Docker.
Maven est fourni par les wrappers `backend/mvnw` et `backend/mvnw.cmd`.

```sh
node scripts/init-local-env.mjs
docker compose up --build --detach --wait postgres minio mailpit
node scripts/run-backend.mjs
```

Le lanceur lit `.env`, sélectionne le profil `local` et adapte l'URL PostgreSQL
aux ports locaux. Dans un second terminal :

```sh
cd frontend
npm ci
npm start
```

Le proxy Angular utilise `http://localhost:8080` par défaut. Si le port API a
changé, définir `API_PROXY_TARGET` avant `npm start` (PowerShell :
`$env:API_PROXY_TARGET='http://localhost:8081'` ; Bash :
`API_PROXY_TARGET=http://localhost:8081 npm start`). Ne pas lancer les conteneurs
`backend`/`frontend` sur les mêmes ports que les serveurs natifs ; les arrêter
avec `docker compose stop backend frontend` si nécessaire.
Pour recevoir les emails en développement natif, Mailpit reste démarré sur
`localhost:1025` ; `application-local.yml` pointe vers ce port par défaut.

## Builds et tests

Backend natif, depuis `backend/` :

```sh
./mvnw -B verify
```

Sous PowerShell : `./mvnw.cmd -B verify`. La commande compile, exécute les
contrôles ArchUnit, crée le JAR et lance les tests d'intégration. **Docker doit
être disponible** : Testcontainers crée un PostgreSQL indépendant, avec secrets
aléatoires, puis le détruit. Aucun `.env` ni service Compose n'est requis pour
les tests natifs. Ils ne ciblent jamais la base de développement.

Sans JDK local, depuis la racine :

```sh
node scripts/init-local-env.mjs
docker compose --profile tools run --rm backend-test
```

Pour vérifier aussi les opérations objet réelles contre MinIO local, démarrer
`minio` puis lancer le test dédié (il crée et supprime un objet de test) :

```sh
docker compose up --detach --wait minio
BRICO_LOCAL_MINIO_TEST=true docker compose --profile tools run --rm backend-test -B -ntp verify
```

Sous PowerShell, remplacer le préfixe d'environnement par
`$env:BRICO_LOCAL_MINIO_TEST='true'` puis enlever la variable après le test.

Ce conteneur de test reçoit le socket Docker pour lancer Testcontainers ; il
n'appartient pas aux services applicatifs. `host.docker.internal` est configuré
pour joindre les ports de test depuis Docker Desktop ou Docker Engine local.

Frontend, depuis `frontend/` :

```sh
npm ci
npm run test:ci
npm run build
```

Vitest vérifie les services et composants de santé, identité, catalogue, packs,
panier, checkout, administration, confidentialité et notifications. Le build
optimisé est dans `frontend/dist/frontend/browser`.
Le build Docker utilise le même lockfile ; les images applicatives s'exécutent
avec un utilisateur non privilégié.

La [CI GitHub Actions](.github/workflows/ci.yml) compile et teste les deux
applications à chaque push et pull request. Le job backend lance ArchUnit,
PostgreSQL Testcontainers et les deux tests MinIO réels. Le job frontend exécute
Vitest et le build optimisé. Le job navigateur construit la boutique Compose,
vérifie les achats dans Chromium et restaure une sauvegarde dans un second projet.
Les rapports et traces d’échec sont gardés sept jours et contiennent uniquement
des données fictives E2E. Ne jamais lancer cette suite sur une boutique commerciale.
Les actions sont figées par SHA des versions officielles vérifiées :
[checkout](https://github.com/actions/checkout), [setup-node](https://github.com/actions/setup-node),
[setup-java](https://github.com/actions/setup-java) et [upload-artifact](https://github.com/actions/upload-artifact).
Le jeton de checkout n’est pas conservé dans Git ; le workflow n’accorde que la lecture du dépôt.
Les builds d'images ne remplacent pas les tests : `Dockerfile` package le JAR
avec `-DskipTests`, alors que `verify` en CI et dans `backend-test` les exécute.

## Configuration et migrations

| Environnement | Configuration | Secrets |
| --- | --- | --- |
| Local | `application-local.yml`, `.env`, `compose.yaml` | Générés dans `.env`, jamais versionnés |
| Test | Profil `test`, propriétés Testcontainers | Générés au lancement ; base isolée |
| Production future | `application-prod.yml`, variables du déploiement | Injectés depuis un gestionnaire de secrets ; voir `.env.production.example` |

Aucun profil local n'est activé implicitement. Hors local/tests, `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `DB_MIGRATION_USERNAME`, `DB_MIGRATION_PASSWORD`,
`MEDIA_ENDPOINT`, `MEDIA_ACCESS_KEY` et `MEDIA_SECRET_KEY`
sont obligatoires. Les cookies sont sécurisés par défaut, avec exception pour
le HTTP local. Le frontend ne contient aucun secret : il garde l'URL relative
`/api/v1` dans tous les environnements, avec un reverse proxy en production.
Le Compose fourni est réservé au développement local.
Pour la récupération en production, fournir aussi `SMTP_HOST`, `SMTP_PORT`,
`MAIL_FROM`, `APP_PUBLIC_URL`, `MAIL_OUTBOX_KEY` (Base64, 32 octets) et, selon le fournisseur, `SMTP_USERNAME`,
`SMTP_PASSWORD`, `SMTP_AUTH`, `SMTP_STARTTLS` et `SMTP_STARTTLS_REQUIRED`, puis tester la délivrabilité de l'email avant
ouverture. Le lien utilise un fragment URL, absent des requêtes au serveur.

Flyway possède le schéma applicatif `bricocomptoir`. La migration V1 fixe ses
permissions, sans créer de table métier fictive. Le rôle `brico_migrator` crée
les objets ; `brico_app` n'a que l'usage du schéma et les droits DML sur les
tables. Le superutilisateur PostgreSQL ne sert qu'à l'initialisation
locale. Flyway possède le schéma ; les adaptateurs sont JDBC. Le starter JPA
conserve Hibernate en validation, sans entité métier ni génération de schéma.

Ajouter une nouvelle migration après V12 ; ne jamais modifier une migration
déjà appliquée. Les tests vérifient l'application des douze migrations sur
une base vide, leur validation et l'absence de rejeu.

La santé n'expose ni secrets, ni détails des composants :

- `/api/v1/health` : état global, incluant PostgreSQL ; `200` ou `503`.
- `/api/v1/health/readiness` : API prête et base disponible.
- `/api/v1/health/liveness` : vie du processus, sans dépendance PostgreSQL.

Le contrat implémenté est dans [OpenAPI](docs/openapi.yaml). L'authentification
client et les contrats métier sont détaillés dans les documents des modules.

L'[administration](docs/administration.md) est accessible sur `/gestion` après
connexion avec un compte interne. Catalogue, photos, référentiels, packs,
stocks, commandes, livraison et accueil s'affichent selon les rôles. Les frais
enregistrés dans l'administration prennent la priorité sur l'environnement ;
aucun tarif de production n'est fourni par la migration.

Les [notifications transactionnelles](docs/notifications.md) utilisent une
outbox PostgreSQL et Mailpit en local. Relancer `node scripts/init-local-env.mjs`
sur un environnement existant ajoute la clé d'outbox manquante en conservant les
autres secrets, puis `docker compose up --build --detach --wait backend frontend`.
Le worker reprend les envois après panne SMTP, avec cinq essais maximum.
Conserver la clé d'outbox avec les sauvegardes ; ne pas la remplacer pendant des
envois en attente. Un SMTP à réponse ambiguë peut remettre un même email.

Les gestionnaires de commandes et administrateurs reçoivent les nouvelles
commandes par SSE dans `/gestion`. Une reconnexion reprend après le curseur
conservé dans la session navigateur. La liste de commandes reste consultable.
`ADMIN` peut consulter `/api/v1/admin/mail-outbox?page=0&size=20` et relancer un
échec terminal avec `POST /api/v1/admin/mail-outbox/{id}/retry` (session et CSRF).

La page `/mes-donnees` propose un choix marketing indépendant, la rectification
vérifiée de l’e-mail, l’export JSON et le retrait des données usuelles.
Voir [les décisions de confidentialité](docs/privacy-decisions.md), les limites
des archives et les vérifications Tunisie/UE. Relancer
`node scripts/init-local-env.mjs` ajoute la clé locale `DATA_ARCHIVE_KEY` sans
modifier les secrets existants, puis reconstruire backend et frontend.

La conservation générale est désactivée tant qu’elle n’est pas décidée.
Renseigner `PRIVACY_LEGAL_DAYS` (durée justifiée et point de départ à valider),
les paramètres `PRIVACY_*_DAYS` puis `PRIVACY_RETENTION_ENABLED=true` dans
l’environnement, et recréer le backend. Une valeur légale `0` bloque le retrait
des coordonnées des commandes ; la fermeture d’un compte sans commande reste
possible. Pour un essai local, une durée choisie constitue uniquement un
paramètre de test. Les archives ne doivent pas être purgées pour débloquer un
essai. Garder la clé d’archive avec les sauvegardes et limiter les accès.

## Vérification de bout en bout reproductible

La suite [Playwright](https://playwright.dev/docs/ci) a son propre lockfile dans
`e2e/`. Elle utilise Angular/Nginx, Spring, PostgreSQL, MinIO, Mailpit et SSE réels.
Elle ne simule aucune réponse métier. Depuis la racine :

```sh
npm ci --prefix e2e
cd e2e
npx playwright install chromium
cd ..
node scripts/e2e.mjs up
node scripts/e2e.mjs test
node scripts/check-restore.mjs
node scripts/e2e.mjs down
```

Sous Linux neuf, installer les dépendances système avec
`npx playwright install --with-deps chromium`. Un navigateur déjà installé peut
être choisi par `BRICO_E2E_BROWSER_PATH` (chemin absolu de son exécutable).
`BRICO_E2E_TMPDIR` permet de placer les profils temporaires du navigateur sur
un disque disposant d’espace ; les rapports restent dans le dépôt.
Le script crée `.local/e2e.env` et un administrateur local à secret aléatoire,
puis les volumes **`bricocomptoir-e2e_*`** ; il refuse d’adopter des ressources
existantes sans son fichier d’environnement. La boutique E2E est sur
http://localhost:14200, l’API sur 18080, Mailpit sur 18025, MinIO sur 19000/19001
et PostgreSQL sur 15432. Le projet de restauration utilise 14300, 18180, 18125,
19100/19101 et 15532. Réserver ces ports. L’environnement habituel sur 4200 et
ses volumes sont distincts. `down` conserve les volumes E2E pour inspection.

Chaque test crée ses propres articles et comptes `DÉMO E2E`/`example.invalid`
via les API administratives ; les images sont des motifs synthétiques. Les
achats mobiles couvrent un invité et un client connecté avec reprise de panier,
les deux et trois unités réservées d’un composant commun, le prix final,
la confirmation relue, l’administration, l’annulation et l’expédition. Les
autres tests vérifient les images responsives sans originaux publics, le SMTP
interrompu puis repris, les notifications après interruption et les refus RBAC.
Le lanceur redémarre uniquement le backend E2E entre exécutions pour renouveler
son budget de connexion en mémoire ; la limite réelle reste inchangée.
Ne pas lancer deux suites simultanément : le test SMTP suspend uniquement Mailpit
E2E (`pause`/`unpause`) pour provoquer un délai SMTP dépassé, puis vérifier la reprise.

Rapport : `e2e/playwright-report/index.html`, XML dans `e2e/test-results/junit.xml`.
Les captures/traces d’échec peuvent inclure des cookies de test ; ces fichiers
et `.local/` restent ignorés par Git. Pour ouvrir le rapport :
`cd e2e` puis `npx playwright show-report`. Si Docker ne répond plus mais que
la boutique E2E reste disponible, une relance partielle est possible depuis
`e2e/` avec `BRICO_E2E_SKIP_DOCKER_CONTROL=true npx playwright test` (PowerShell :
définir la variable puis `npx playwright test`). Le test de coupure SMTP est alors
explicitement ignoré ; cette option n’est jamais définie en CI et ne valide
ni les commandes d’exploitation ni une restauration.

## Sauvegarde et restauration

Sauvegarder ensemble **PostgreSQL, les objets MinIO, la version des applications
et des migrations**, et garder **`MAIL_OUTBOX_KEY`/`DATA_ARCHIVE_KEY`** dans un
coffre séparé et restaurable. Sans ces clés, les contenus en attente et archives
sont illisibles. Un `pg_dump` n’inclut pas les rôles du cluster ; les comptes
migrateur/application et leurs propriétaires doivent exister avant restauration.
Le dump personnalisé se restaure avec `pg_restore`, comme décrit dans la
[documentation PostgreSQL](https://www.postgresql.org/docs/18/backup-dump.html).

Le test `node scripts/check-restore.mjs` réalise la procédure sur les données
E2E exclusivement : arrêt des écritures, dump `pg_dump -Fc`, copie binaire par
`docker compose cp` (sans pipeline texte PowerShell), archive à froid du volume
MinIO complet (copies binaires Docker, sans montage du dossier hôte), restauration
dans des volumes neufs et initialisation explicite des rôles, redémarrage avec les mêmes
images/clés. Il vérifie le nombre de commandes, la lecture authentifiée d’un
instantané, la validation Flyway et le SHA-256 identique d’un rendu JPEG. Les
contrôles HTTP du clone passent par Nginx sur son réseau Docker, indépendamment
du relais de ports Windows ; les achats navigateur vérifient l’accès depuis l’hôte.
Il refuse un projet de restauration préexistant, le détruit après vérification
et redémarre l’environnement E2E d’origine même en cas d’échec. Les sauvegardes
de démonstration restent dans `.local/e2e-backup/` pour inspection.

Pour une sauvegarde manuelle de la base locale par défaut, arrêter les écritures
et la tâche d’outbox, puis utiliser :

```sh
docker compose stop frontend backend
docker compose exec -T postgres pg_dump -U postgres -d bricocomptoir -Fc -f /tmp/brico.dump
docker compose cp postgres:/tmp/brico.dump .local/brico.dump
```

Créer `.local/` avant la copie si absent. Ne pas copier les fichiers du volume
PostgreSQL en activité. Pour MinIO local, arrêter `minio`, archiver intégralement
le volume identifié par `docker volume inspect bricocomptoir_minio-data`, y compris
sa métadonnée interne, avec un conteneur auxiliaire monté en lecture seule ;
les commandes exactes automatisées sont dans `scripts/check-restore.mjs`. Restaurer
l’archive dans un **nouveau volume vide**, en préservant les propriétaires, avant
de démarrer MinIO. La base se restaure dans une **nouvelle base vide** avec les
rôles déjà créés :

```sh
# Nouveau projet et environnement avec des ports distincts.
# Démarrer uniquement postgres/minio ; le backend reste arrêté.
# Copier le dump vers postgres:/tmp/brico.dump dans CE projet.
docker compose -p bricocomptoir-restauration --env-file .local/restauration.env exec -T postgres pg_restore -U postgres -d bricocomptoir --exit-on-error --single-transaction /tmp/brico.dump
docker compose -p bricocomptoir-restauration --env-file .local/restauration.env exec -T postgres psql -U postgres -d bricocomptoir -c ANALYZE
```

Vérifier explicitement le projet et la cible avant ces commandes ; ne jamais
écraser la base source. Redémarrer backend/frontend et contrôler santé, droits,
commandes, stock, images et outbox. Mailpit est une boîte de test, pas l’archive
de production des emails. En production, le mécanisme de snapshot/réplication
S3 dépend du prestataire : cette archive de volume MinIO local ne constitue pas
une procédure universelle de sauvegarde S3. Chiffrer les sauvegardes, restreindre
les accès, conserver une copie hors hôte, fixer fréquence/rétention/RPO/RTO et
répéter une restauration complète. Ces services externes restent à configurer.

## Configuration requise avant production

Le Compose livré utilise le profil local et des tarifs de démonstration.
Il n’est pas un déploiement de production. Renseigner au minimum :

| Paramètres | Décision requise |
| --- | --- |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_MIGRATION_*` | PostgreSQL protégé, TLS vérifié, rôles séparés et sauvegardes restaurées |
| `MEDIA_ENDPOINT`, `MEDIA_ACCESS_KEY`, `MEDIA_SECRET_KEY`, `MEDIA_BUCKET` | Stockage S3 privé, compte restreint au bucket, sauvegarde et accès HTTPS |
| `SMTP_*`, `MAIL_FROM`, `APP_PUBLIC_URL` | Fournisseur réel, TLS/authentification, domaine et délivrabilité vérifiés |
| `MAIL_OUTBOX_KEY`, `DATA_ARCHIVE_KEY` | Deux clés Base64 de 32 octets, coffre, sauvegarde et procédure de rotation |
| `CHECKOUT_DELIVERY_FEE_TND`, `CHECKOUT_GOVERNORATES` | Tarif/zones validés ; réglages sauvegardés en administration prioritaires |
| `PRIVACY_LEGAL_DAYS`, `PRIVACY_*_DAYS`, `PRIVACY_RETENTION_ENABLED` | Conservation décidée ; `0`/désactivée ne vaut pas validation juridique |
| `TRUSTED_PROXY_PATTERN` | Expression régulière Java des seules adresses des passerelles autorisées ; backend inaccessible directement aux clients |

Déployer sous `prod`, HTTPS et même origine pour frontend/API ; vérifier les
cookies `Secure`/`HttpOnly`/`SameSite`, CSRF et le proxy SSE sans tampon. Ne pas
exposer PostgreSQL, la console MinIO ni SMTP publiquement. Créer les comptes
administratifs par une procédure d’exploitation contrôlée : la commande de bootstrap
local n’est pas activable sous `prod`. Valider les vrais produits/photos,
compositions, prix, règles fiscales, zones et procédures de livraison.

Le proxy écrase `X-Forwarded-For` avec l'adresse de son client direct ; Spring
ne l'utilise que si le pair appartient à `TRUSTED_PROXY_PATTERN`. Renseigner une
liste précise, jamais `.*`. Si un CDN ou un autre proxy précède Nginx, configurer
ses adresses de confiance et la restitution de l'adresse client à cette couche,
puis vérifier que deux clients disposent de budgets de connexion indépendants
et qu'un en-tête forgé ne permet pas de les contourner. Adapter également la
transmission du protocole HTTPS à cette chaîne de confiance. Le limiteur actuel
est en mémoire : prévoir une protection partagée si plusieurs instances sont
déployées. La limite Nginx de 26 Mio concerne uniquement le téléversement des
photos ; chaque image reste limitée à 6 Mio, quatre images par requête.

L'[audit de lancement](docs/launch-audit.md) conserve les défauts corrigés,
les difficultés historiques et leur vérification finale. Les tests locaux ne
remplacent pas la validation de la chaîne HTTPS et des services de production.

Appliquer les nouvelles migrations avec le rôle dédié, tester la mise à niveau
sur une copie et sauvegarder avant déploiement ; ne pas modifier des migrations
appliquées ni utiliser `Flyway clean` sur la boutique. Le démarrage valide leurs
checksums. Prévoir une restauration testée avant une évolution incompatible.
La santé couvre PostgreSQL ; surveiller séparément les objets, la délivrabilité
SMTP, les files en échec, l’espace disque et les sauvegardes. Le code masque les
logs applicatifs sensibles ; configurer aussi ceux du proxy et des prestataires.
Les décisions juridiques, notice/contact et formalités sont suivies dans
`docs/privacy-decisions.md`. Aucun statut de conformité automatique n’est annoncé.

## Dépannage courant

| Symptôme | Vérification et action |
| --- | --- |
| `localhost:4200` ne répond pas | `docker version`, `docker compose ps`, puis `docker compose start --wait --wait-timeout 180` et `node scripts/check-local.mjs`. Si Docker lui-même expire, vérifier l'espace disque et redémarrer Desktop en tenant compte des autres conteneurs. Ne pas réinitialiser les volumes. |
| Port déjà utilisé | Choisir un autre `*_PORT` dans `.env`, puis `docker compose up -d --wait`. Recréer les conteneurs conserve les volumes. Le proxy Angular natif vise le port API indiqué dans `frontend/proxy.conf.json`. |
| API absente ou démarrage refusé | Vérifier PostgreSQL sain, paramètres obligatoires et migrations. `docker compose logs --tail=100 backend postgres` ; les logs applicatifs masquent les détails sensibles. Ne pas désactiver Flyway ni supprimer les données pour contourner une erreur. |
| Connexion ou écriture refusée | Vérifier le rôle et la propriété de la ressource. Renouveler le CSRF après connexion/déconnexion ; `401` exige une reconnexion, `403` indique permission ou CSRF. Un compte interne sans CUSTOMER n'achète pas. |
| Aucun produit ou pack visible | Base initialement vide ; vérifier publication des fiches, variantes et composants. Les packs DÉMO sont volontairement brouillons. Charger les exemples est une action explicite, jamais une migration de production. |
| Photo rejetée | JPEG/PNG réels, 6 Mio maximum par fichier, dimensions 320–6000, 24 mégapixels maximum, quatre par requête. Vérifier MinIO et la configuration de passerelle pour les lots. |
| Email absent | Vérifier Mailpit, la file `/admin/mail-outbox`, les paramètres SMTP et la tâche de distribution. La commande validée reste persistée ; ADMIN peut relancer un échec. Aucun email réel n'est livré par Mailpit. |
| Tests Testcontainers bloqués | Docker Linux doit répondre, socket accessible au runner, mémoire/espace disponibles. Ne pas remplacer PostgreSQL par H2 ni annoncer ces tests réussis. Sous Windows utiliser le runner Compose documenté. |

Les exemples commerciaux portent `DÉMO` et `demo=true`. Les images des tests
sont des motifs synthétiques sans produit réel ; aucune photographie avant/après
ni preuve de résultat n'est fournie. Avant publication commerciale, vérifier les
photos, leurs droits, les caractéristiques et retirer/remplacer les exemples.

Avant chaque publication, sans afficher les secrets :

```sh
node --test scripts/release-checks.test.mjs
node scripts/check-repository.mjs
node scripts/check-release.mjs
git diff --check
git diff --cached --check
```

Le contrôle de publication examine les fichiers non ignorés, les liens locaux
et certains motifs de secrets, y compris les secrets locaux connus et
l'historique. Il complète une relecture du diff ; ce n'est pas une certification
automatique de l'absence de toute donnée sensible. Les URLs externes peuvent
nécessiter une vérification réseau séparée.
