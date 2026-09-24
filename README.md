# BricoComptoir

Socle d'une quincaillerie tunisienne : Java 21, Spring Boot, Spring Security,
Angular, PostgreSQL et Flyway. Monolithe modulaire hexagonal ; les fonctionnalités
catalogue, comptes, packs et commandes ne sont pas encore implémentées.

Lire [l'architecture](docs/architecture.md), la [roadmap](docs/roadmap.md), les
[règles de contribution](AGENTS.md) et les [vérifications réalisées](docs/progress.md).

## Démarrage complet avec Docker

Prérequis : Docker Engine/Desktop avec conteneurs Linux et Docker Compose v2 ou
ultérieur, Node.js **24.18.0**. Java n'est pas nécessaire sur l'hôte dans ce mode.
Les premiers builds téléchargent les dépendances et compilent MinIO ; prévoir
plusieurs minutes et suffisamment de mémoire pour Docker.

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

| Service | Adresse par défaut | Usage |
| --- | --- | --- |
| Angular | http://localhost:4200 | Page de disponibilité avec nouvelle vérification manuelle |
| API | http://localhost:8080/api/v1/health | Santé réelle, dépend de PostgreSQL |
| PostgreSQL | localhost:5432 | Base `bricocomptoir`, comptes dans `.env` |
| MinIO S3 | http://localhost:9000 | Réservé à une future intégration de fichiers |
| Console MinIO | http://localhost:9001 | Connexion avec les valeurs `MINIO_ROOT_*` de `.env` |
| Mailpit | http://localhost:8025 | Boîte locale ; SMTP sur localhost:1025 |

Les ports sont configurables dans `.env`. MinIO et Mailpit fonctionnent mais ne
sont pas appelés par l'application à cette étape. Aucun email n'est envoyé et
aucun bucket métier n'est créé automatiquement. Depuis les conteneurs, leurs
adresses seront `http://minio:9000` et `mailpit:1025`.

La page Angular appelle `/api/v1/health` via Nginx sur la même origine. Elle
affiche une indisponibilité en cas de réponse incorrecte, d'erreur HTTP/réseau ou
de délai dépassé après cinq secondes. Les autres routes API sont fermées par
défaut. Aucun compte de test ni mot de passe Spring généré n'est disponible.

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

Ce conteneur de test reçoit le socket Docker pour lancer Testcontainers ; il
n'appartient pas aux services applicatifs. `host.docker.internal` est configuré
pour joindre les ports de test depuis Docker Desktop ou Docker Engine local.

Frontend, depuis `frontend/` :

```sh
npm ci
npm run test:ci
npm run build
```

Vitest vérifie les états attente/disponible/indisponible, les réponses invalides
et la reprise après erreur. Le build optimisé est dans `frontend/dist/frontend/browser`.
Le build Docker utilise le même lockfile ; les images applicatives s'exécutent
avec un utilisateur non privilégié.

La [CI GitHub Actions](.github/workflows/ci.yml) compile et teste les deux
applications à chaque push et pull request. PostgreSQL est lancé par Testcontainers
dans le job backend ; le job frontend valide aussi la configuration Compose.
Les builds d'images ne remplacent pas les tests : `Dockerfile` package le JAR
avec `-DskipTests`, alors que `verify` en CI et dans `backend-test` les exécute.

## Configuration et migrations

| Environnement | Configuration | Secrets |
| --- | --- | --- |
| Local | `application-local.yml`, `.env`, `compose.yaml` | Générés dans `.env`, jamais versionnés |
| Test | Profil `test`, propriétés Testcontainers | Générés au lancement ; base isolée |
| Production future | `application-prod.yml`, variables du déploiement | Injectés depuis un gestionnaire de secrets ; voir `.env.production.example` |

Aucun profil local n'est activé implicitement. Hors local/tests, `DB_URL`,
`DB_USERNAME`, `DB_PASSWORD`, `DB_MIGRATION_USERNAME` et `DB_MIGRATION_PASSWORD`
sont obligatoires. Les cookies sont sécurisés par défaut, avec exception pour
le HTTP local. Le frontend ne contient aucun secret : il garde l'URL relative
`/api/v1` dans tous les environnements, avec un reverse proxy en production.
Le Compose fourni est réservé au développement local.

Flyway possède le schéma applicatif `bricocomptoir`. La migration V1 fixe ses
permissions, sans créer de table métier fictive. Le rôle `brico_migrator` crée
les objets ; `brico_app` n'a que l'usage du schéma et les droits DML sur les
futures tables. Le superutilisateur PostgreSQL ne sert qu'à l'initialisation
locale. Hibernate valide le schéma et ne le modifie pas.

Ajouter une nouvelle migration `V2__<module>_<description>.sql`, puis les
suivantes ; ne jamais modifier une migration déjà appliquée. Les tests vérifient
l'application de V1 sur une base vide, sa validation et son absence de rejeu.
Un test de mise à niveau V1 → V2 devra accompagner la première évolution.

La santé n'expose ni secrets, ni détails des composants :

- `/api/v1/health` : état global, incluant PostgreSQL ; `200` ou `503`.
- `/api/v1/health/readiness` : API prête et base disponible.
- `/api/v1/health/liveness` : vie du processus, sans dépendance PostgreSQL.

Le contrat implémenté est dans [OpenAPI](docs/openapi.yaml). L'authentification
client et les contrats métier de l'architecture seront réalisés ultérieurement.
