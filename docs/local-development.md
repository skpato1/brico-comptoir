# Développement local dans VS Code

Les tâches VS Code lancent le backend Spring sur `localhost:8080` et Angular sur
`localhost:4200`. Le backend utilise uniquement PostgreSQL, MinIO et Mailpit
locaux. Ce parcours ne modifie pas la configuration de production Render/Vercel.

## Première préparation

Installer Docker Desktop ou Docker Engine avec Compose, Node 24 et Java 21.
Depuis la racine du dépôt :

```powershell
node scripts/init-local-env.mjs
npm ci --prefix frontend
```

Le script crée un `.env` avec des secrets locaux aléatoires s'il n'existe pas.
S'il existe déjà, il conserve ses valeurs et complète seulement les clés de
confidentialité et de courrier manquantes. `.env`, `.local/` et les volumes
Docker restent hors Git. Si Java 21
n'est pas déjà fourni par `JAVA_HOME` ou le `PATH`, créer le fichier ignoré
`.local/dev-tools.json` avec les chemins propres au poste :

```json
{
  "JAVA_HOME": "C:\\chemin\\vers\\jdk-21",
  "MAVEN_USER_HOME": "C:\\chemin\\vers\\cache-maven",
  "MAVEN_OPTS": "-Dmaven.repo.local=C:\\chemin\\vers\\cache-maven"
}
```

Le lanceur ne lit dans ce fichier que ces trois variables d'outillage. Ne pas
y placer de mot de passe ni d'URL de service.

## Démarrer et vérifier

Dans VS Code, ouvrir le dossier du dépôt et choisir **Tasks: Run Task** :

1. `Brico: Backend (Spring Boot)` initialise `.env`, démarre PostgreSQL,
   MinIO et Mailpit, puis lance Spring avec Java 21.
2. `Brico: Frontend (Angular)` lance Angular et son proxy `/api` local.
3. `Brico: Check local app` vérifie frontend, API et services locaux.

Les deux serveurs restent ouverts dans leurs terminaux. Les arrêter avec
Ctrl+C avant de relancer les tâches. `Brico: Stop infrastructure` arrête les
conteneurs sans effacer les volumes. Éviter `docker compose down --volumes`
si la base ou les photos locales doivent être conservées.

En terminal, les commandes équivalentes sont `node scripts/init-local-env.mjs`,
`docker compose up --detach --wait postgres minio mailpit`, puis
`node scripts/run-backend.mjs` ; dans un second terminal, `npm start` depuis
`frontend/`.

## Données et séparation de la production

Par défaut, le backend utilise la base Compose `bricocomptoir`. Pour conserver
une copie locale du catalogue dans une autre base **déjà créée**, `.env` peut
définir par exemple :

```dotenv
DB_URL=jdbc:postgresql://localhost:5432/bricocomptoir_prodlocal
```

Le port doit correspondre à `POSTGRES_PORT` de `.env`. Le lanceur refuse une
URL PostgreSQL distante ou contenant des identifiants, et impose le profil
`local`, MinIO et Mailpit locaux. Le proxy Angular refuse toute origine API
non locale. Les données et photos d'une copie du catalogue doivent être
importées séparément dans cette base et le MinIO local ; elles ne sont jamais
téléchargées automatiquement depuis la production. Une nouvelle installation
sans import a un catalogue vide après Flyway. Le jeu `infra/demo/` est fictif
et reste réservé aux essais explicitement identifiés.

Sur le poste ayant reçu l'instantané du 13 octobre 2026, la base
`bricocomptoir_prodlocal` et ses photos restent dans les volumes Docker après
un `git pull`. Les fichiers de reprise et identifiants nécessaires à une
nouvelle copie sont privés et ignorés par Git. Aucun compte client ni commande
de production n'est inclus dans cet instantané ; créer un compte local pour
tester les fonctions qui nécessitent une connexion.

Le build Vercel et le backend hébergé n'appellent pas `scripts/run-backend.mjs`
ni le proxy de `ng serve`. Fusionner ces tâches et garde-fous dans `main` ne
change donc ni leurs connexions de production ni leur catalogue.
