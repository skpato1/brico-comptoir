# Progression de BricoComptoir

Dernière mise à jour : 28 septembre 2026.

## Correction du déploiement Vercel — 28 septembre 2026

Diagnostic établi : `https://brico-comptoir.vercel.app/`, `/catalogue` et
`/api/v1/health` retournaient le `404 NOT_FOUND` de la plateforme. Le backend
reste sur localhost, confirmé par l'exploitant. Le connecteur ne listait que
l'autre projet de l'équipe ; sa tentative générique de déploiement a été
refusée automatiquement car la destination n'était pas vérifiable. Aucune
création ni modification de cet autre projet n'a été effectuée.

Le tableau de bord authentifié a ensuite confirmé **le projet existant
`skpato1s-projects/brico-comptoir`**, son domaine, son dépôt GitHub et sa branche
`main`. Son déploiement du commit `6998f91` était Ready avec racine vide,
preset Other, Node 24.x et aucune commande de build/install personnalisée.
Il servait donc une racine sans build Angular. Cette preuve permet de cibler
le projet existant par sa connexion Git, sans en créer un nouveau.

Correctif : `vercel.json` installe le lockfile frontend, puis le build Angular
génère les fichiers et routes Build Output API v3. Les routes navigateur
directes fonctionnent ; `/api` est prioritaire et exclu des caches. L'origine
HTTPS est fournie par `BRICO_API_ORIGIN`, sans secret ; HTTP, localhost et les
URL avec identifiants sont refusés. Sans origine, l'API retourne une
indisponibilité 503 explicite et l'interface n'annonce pas une boutique active.
`.vercel/` est ignoré et refusé par le contrôle de publication. La CI frontend
compile désormais cet artefact et teste les régressions de routage.

Vérifications réellement exécutées sur cette correction :

| Commande ou contrôle | Résultat |
| --- | --- |
| `node --test scripts/vercel-output.test.mjs scripts/release-checks.test.mjs` | **8/8 passent**, après correction d'un échappement du motif SPA détecté au premier passage |
| `BRICO_API_ORIGIN=https://api.example.com node scripts/build-vercel.mjs` dans la copie identique sur D: | Build Angular production réussi, 389,15 ko initiaux ; artefact et routes Vercel générés ; origine fictive de test, aucun appel à ce domaine |
| `npm run test:ci` dans cette copie | **44 tests / 10 fichiers passent** |
| Contrôle Chromium ponctuel de l'artefact servi localement | Accueil, catalogue direct et actualisé, scripts, santé et refus CSRF vérifiés avec Spring Boot local réel ; mode API absente : 503 JSON et écran d'indisponibilité ; aucune erreur JavaScript non capturée |
| `node scripts/check-local.mjs` | Neuf contrôles HTTP passent sur les services principaux existants |
| `node scripts/check-repository.mjs`, `node scripts/check-release.mjs`, `git diff --check` | Contrôles des migrations, modèles sans secrets, exclusions, liens locaux et scan ciblé valides |
| Push du commit `9bab393` sur `origin/main` et tableau de bord Vercel | Projet existant déployé en Production, **Ready**, build de 40 s ; aucune création de projet |
| HTTP sur `https://brico-comptoir.vercel.app` après déploiement | `/`, `/catalogue`, `/packs/demo` et `/gestion` : **200 Angular** ; 13 fichiers JS/CSS : **200** ; fichier JS absent : **404** ; API en GET/POST/DELETE : **503 JSON `API_NOT_CONFIGURED`, `Cache-Control: no-store`** |
| Navigateur sur le domaine public | Interface Angular, navigation et écran d'indisponibilité chargés ; ancienne 404 de plateforme résolue, aucun achat public annoncé comme vérifié |

Le contrôle Chromium utilise un serveur de vérification du routage généré ;
il ne constitue pas une exécution du moteur Vercel ni de sa chaîne HTTPS. Aucun
code backend ni migration ne change : les suites PostgreSQL/achat exécutées
lors de la publication précédente ne sont pas présentées comme relancées ici.
Le [déploiement du correctif](https://vercel.com/skpato1s-projects/brico-comptoir/9No99Q4xu6G9c8LqXp3evqf4JUU1)
a aussi été vérifié sur le domaine réel comme indiqué ci-dessus. L'exploitant
confirme ne disposer actuellement d'aucun hébergement backend. L'API publique,
PostgreSQL/S3/SMTP hébergés et la recette HTTPS/cookies/CSRF/SSE restent
nécessaires avant ouverture ; localhost ne remplit pas ces conditions. Voir
[deployment.md](deployment.md) pour les paramètres et la procédure Vercel.

## État de reprise et préparation de publication

Le code a été comparé aux documents et vérifié à nouveau, sans considérer les
anciennes cases cochées comme une preuve. Avant publication : branche `main`,
un commit de socle `1084e6e`, implémentation des étapes suivantes non commitée,
origine `https://github.com/skpato1/brico-comptoir.git` et aucune branche distante
retournée par `git ls-remote --heads origin`. Aucun historique n'est réécrit.

Les quinze séquences de développement correspondent aux livrables suivants :

| Séquence | État réellement livré et vérifié |
| --- | --- |
| 1 Architecture | Neuf modules métier, ports, dépendances et règles ArchUnit ; adaptations JDBC documentées |
| 2 Socle | Java 21, Angular, PostgreSQL, Flyway, Compose, santé, MinIO/Mailpit et pipeline |
| 3 Identité | Clients/internes, sessions, récupération, quatre rôles, dernier ADMIN et révocation |
| 4 Catalogue | Catégories, marques, SKU, prix TND, brouillons, filtres/tri/pagination et droits |
| 5 Médias/import | Photos validées/réencodées, métadonnées SQL, MinIO privé, CSV aperçu/application |
| 6 Inventaire | Physique/réservé/disponible, ajustement, réservation atomique, libération/conversion, concurrence/rollback |
| 7 Packs | Variantes/composition/prix, disponibilité dérivée, cinq brouillons fictifs DÉMO |
| 8 Panier | Visiteur/client, quantités, persistance, fusion idempotente après connexion, estimation |
| 9 Checkout | Adresse TN, forfait serveur, instantanés immuables, SKU cumulés, idempotence et transitions |
| 10 Interface publique | Accueil, recherche, fiches, packs, panier/checkout/confirmation et images responsives |
| 11 Administration | Catalogue/photos/packs, stocks, commandes, livraison, accueil ; tableaux et permissions |
| 12 Notifications | Outbox transactionnelle, SMTP/reprises, SSE durable authentifié et reconnexion |
| 13 Confidentialité | Consentement distinct, export/rectification/retrait, conservation et archives réservées |
| 14 Vérification/exploitation | Achats réels sur Compose isolé, Mailpit/MinIO/SSE, sauvegarde/restauration et CI préparée |
| 15 Audit | Corrections de révocation, cookie CSRF, confiance proxy et limite photo ; régressions exécutées |

### Résultats du 28 septembre 2026 sur les sources à publier

| Commande exécutée | Résultat |
| --- | --- |
| `docker compose config --quiet` | Configuration valide, sans imprimer les secrets |
| `BRICO_LOCAL_MINIO_TEST=true docker compose --profile tools run --rm --name bricocomptoir-release-verify backend-test -B -ntp clean verify` | Première tentative : 44 unitaires passés, un test d'intégration mal configuré ; échec conservé comme tel |
| Même runner avec `-B -ntp verify`, après correction | **BUILD SUCCESS**, 44 unitaires dont ArchUnit, **55 intégrations**, zéro échec/erreur/test ignoré ; PostgreSQL Testcontainers/Ryuk standard et MinIO réel |
| `npm ci`, `npm run test:ci`, `npm run build` dans la copie Angular de vérification | Installation réussie (zéro vulnérabilité signalée), **44 tests/10 fichiers**, build optimisé réussi |
| `npm ci --prefix e2e`, `node scripts/e2e.mjs up` | Sources backend/frontend reconstruites, cinq services E2E sains |
| `node scripts/e2e.mjs test` | Première tentative : deux courses de navigation du test photo ; après attente de route/fiche, **4/4 parcours passent**, sans mocks ni scénario SMTP ignoré |
| `node scripts/check-restore.mjs` | Dump PostgreSQL et archive froide MinIO restaurés dans des volumes neufs ; nombre de commandes, commande figée authentifiée, Flyway et hash photo identiques ; source remise en route |
| `node scripts/e2e.mjs down` | Projet E2E arrêté, volumes conservés ; boutique principale préservée |
| `node scripts/check-local.mjs` et `docker compose ps` | Neuf contrôles HTTP réussis, cinq services principaux `healthy` ; ces contrôles utilisent les images locales déjà en place |
| `node --test scripts/release-checks.test.mjs` | Trois tests du contrôle de publication passent |
| `node scripts/check-repository.mjs`, `node scripts/check-release.mjs`, `git diff --check` | Migrations V1–V12 consécutives, modèles sans secrets, artefacts ignorés, liens locaux et scan ciblé des fichiers/historique valides |

Sous PowerShell, `BRICO_LOCAL_MINIO_TEST` a été défini avec `$env:`. Faute de
JDK 21 sur l'hôte, Maven a réellement été exécuté dans le runner Docker. Pour
éviter la saturation de C:, Angular a été synchronisé dans
`D:\bricocomptoir-frontend-verification` avec mêmes sources et lockfile ; caches
npm/Chromium et fichiers temporaires navigateur sont sur D:. Le JAR vérifié a
été conservé sur D: après les tests ; ces artefacts ne sont pas publiés. Les
rapports Surefire/Failsafe et Playwright locaux restent ignorés par Git.

Corrections de cette publication : configuration explicite de la classe Boot
dans `IdentityRevocationIT`, attente de la fiche après navigation dans Playwright,
documents conformes aux adaptateurs/routes effectifs et contrôle de publication
testé ajouté à la CI. Aucun défaut métier nouveau n'a nécessité de refonte.
Les trois tests de révocation administrative et les régressions CSRF/proxy
passent ; le PNG synthétique de plus de 1 Mio passe la vraie passerelle Nginx.

### Travaux incomplets et dépendances non configurées

- La livraison technique ne vaut pas ouverture aux clients. La liste publique
  des packs reste non paginée ; le journal de stock est SQL, sans route de lecture
  dédiée. Pas de MFA, paiement en ligne, moteur de campagne marketing ni partage
  de sessions/limiteurs entre instances. Le provisionnement du premier ADMIN de
  production exige encore une procédure contrôlée.
- SMTP peut produire un doublon si l'accusé de remise est perdu ; les baux et
  déduplications internes passent les tests, sans garantie de remise unique.
  Réconciliation des objets S3 orphelins et rotation/rechiffrement des clés ne
  sont pas automatisés.
- À renseigner : hébergement/région, domaine/HTTPS et chaîne proxy approuvée,
  secrets/coffre, PostgreSQL privé et rôles, S3 privé, SMTP réel/délivrabilité,
  vrais produits/photos/compositions/prix, fiscalité et zones/frais de livraison,
  premier ADMIN, notice/contact, durées/formalités Tunisie et éventuel périmètre UE.
  Alertes disque/outbox/services et sauvegardes chiffrées hors hôte restent des
  décisions d'exploitation. C: reste proche de la saturation : stockage Docker
  à prévoir avant de nouveaux gros builds.
- CI GitHub : jobs et commandes livrés, actions épinglées vérifiées à HTTP 200 ;
  aucun succès de workflow distant n'est présumé avant son exécution après push.
  Une validation YAML locale indépendante a été tentée ; les parseurs `yaml`
  /`js-yaml` Node et PyYAML ne sont pas installés dans les runtimes disponibles.
  Les commandes des jobs sont testées localement ; leur orchestration GitHub
  reste à observer après publication, sans prétendre avoir exécuté Actions ici.
  Les liens locaux sont vérifiés. Les pages INPDP ont expiré/retourné une erreur,
  et EUR-Lex un contrôle d'accès ; leurs textes et formalités sont à revérifier.
  La référence officielle PostgreSQL sur les sauvegardes a également rencontré
  une erreur réseau lors du contrôle externe.
- Les scripts `infra/demo/` et fixtures/images E2E sont fictifs et marqués DÉMO.
  Aucun avant/après photographique n'est fourni comme preuve réelle.

La reprise et les décisions restantes sont détaillées dans [README](../README.md),
[deployment.md](deployment.md), [security.md](security.md) et
[privacy-decisions.md](privacy-decisions.md). Le journal ci-dessous est historique :
ses blocages Docker et comptes de tests antérieurs ne remplacent pas ce bilan.

## État des étapes

- [x] 0 — Vérifier le dépôt et documenter l'architecture.
- [x] 1 — Installer le socle technique et la CI.
- [x] 2 — Implémenter identité, authentification et autorisations.
- [x] 3 — Implémenter catalogue et packs.
  - [x] 3a — Catalogue de produits, variantes et administration.
  - [x] 3a-media — Photos de produit, stockage MinIO et import CSV.
  - [x] 3b — Packs et instantané des offres.
- [x] 4 — Implémenter le noyau de stock et réservations (journal SQL, API de lecture dédiée restante).
  - [x] 4a — Noyau par SKU : soldes, ajustements, réservations et transitions.
  - [x] 4b — Vérifier la disponibilité et les réservations avec les packs de 3b.
- [x] 5 — Implémenter achat et commandes.
  - [x] 5a — Panier visiteur/client, reprise après connexion et estimation.
  - [x] 5b — Checkout serveur, livraison, commande et réservation atomique.
- [x] 6 — Implémenter traitement des commandes.
  - [x] 6a — Emails transactionnels en outbox et notifications gestionnaire SSE.
- [ ] 7 — Vérifier le produit et préparer l'ouverture.
  - [x] 7a — Interface publique Angular et parcours réel sur navigateur.
  - [x] 7b — Administration Angular, permissions et réglages persistés.
  - [x] 7c — Protection technique des données, consentement distinct et opérations sur les données.
  - [x] 7d — Vérification locale : builds, tests navigateur, images, Mailpit/SSE et restauration PostgreSQL/MinIO ; CI complète préparée.

Voir les critères de sortie dans la [roadmap](roadmap.md).

## Journal — étape 0

### État initial vérifié

- Le répertoire ne contenait que `.git` ; aucun code, configuration, README
  ou règle `AGENTS.md` préexistante à préserver.
- `git status --short --branch` : `No commits yet on main...origin/main [gone]`.
- `git log -5 --oneline` : absence de commit sur `main`, résultat attendu pour
  une branche à naître ; `git ls-files` et `git branch -a` : aucune entrée.
- `git remote -v` : origine fetch/push
  `https://github.com/skpato1/brico-comptoir.git`.
- `git ls-remote --symref origin` : succès, aucune référence distante.
- La première lecture Git dans le bac à sable a rencontré le contrôle de
  propriétaire Windows ; les lectures ont ensuite réussi dans le contexte
  autorisé, sans modification de la configuration globale Git.

### Livrables terminés

- [Architecture](architecture.md) : quatre modules, règles hexagonales, ports
  et adaptateurs, modèle, session/CSRF, RBAC, transactions et contrats API.
- [Roadmap](roadmap.md) : étapes et critères de sortie jusqu'à l'ouverture.
- [AGENTS.md](../AGENTS.md) : règles durables de réalisation et de vérification.
- Ce journal distingue les documents terminés des fonctionnalités à développer.

Décisions structurantes : monolithe modulaire, domaine/application Java purs,
packs virtuels sans stock autonome, stock réservé atomiquement avec la commande,
session serveur sous même origine, paiement à la livraison comme hypothèse
initiale. Les arbitrages et limites figurent dans l'architecture.

### Vérification de cette étape

- Relecture de cohérence effectuée : frontières des quatre modules, API/RBAC,
  cycle commande/réservation, hypothèses et critères de sortie concordants.
- `git status --short` et inventaire des fichiers non suivis : uniquement
  les quatre documents demandés. `git diff --check` sans erreur ; les fichiers
  nouveaux étant non suivis, contrôle complémentaire de chacun contre `NUL`
  avec `git -c core.autocrlf=false diff --no-index --check` : aucune erreur.
- Validation documentaire : huit liens locaux résolus, deux exemples JSON
  analysés, blocs Markdown fermés, aucun marqueur de conflit ni espace final.
- Aucun test applicatif exécuté : aucun code, build ou suite de tests n'existe.
- Aucun scaffold, migration, fonctionnalité boutique, commit ou déploiement
  réalisé dans cette étape.

### Suite prévue à la fin de l'étape 0

L'étape 0 se termine avec ces documents. La prochaine demande pourra engager
l'étape 1 : figer les versions compatibles, créer le socle et ses contrôles.
Les règles commerciales restant à valider avant ouverture sont recensées dans
l'architecture ; elles ne bloquent pas la préparation du socle.

## Journal — étape 1 : socle technique

Réalisé le 24 septembre 2026. Aucun cas d'usage boutique ajouté.

### Livrables

- Backend Java 21 / Spring Boot 4.1.1 / Spring Security 7.1.1, Maven Wrapper
  3.9.16, JPA et Flyway 12.4.0. Les versions de Security/Flyway sont gérées par
  le parent Spring Boot. Les quatre modules restent des frontières documentées,
  sans services métier factices ; le démarrage et la sécurité sont dans `bootstrap`.
- PostgreSQL 18.6 et migration V1 du schéma `bricocomptoir` : droits séparés
  pour migration et application, Hibernate en validation seule.
- Angular 21.2.24 avec Node 24.18.0 et lockfile npm ; page lisant réellement
  la santé API, états d'attente/panne/reprise, requête bornée à cinq secondes.
- Compose local pour PostgreSQL, backend, frontend Nginx, MinIO construit depuis
  la source officielle figée et Mailpit. Ports de l'hôte limités à `127.0.0.1`,
  volumes persistants et sondes de santé. MinIO/Mailpit ne sont pas intégrés au métier.
- Profils `local`, `test`, `prod`, exemples sans secrets et génération locale
  idempotente de `.env` ignoré par Git. Aucun compte Spring par défaut.
- Contrat de santé [OpenAPI](openapi.yaml), commandes dans le [README](../README.md),
  script de contrôle HTTP et [CI](../.github/workflows/ci.yml) backend/frontend.
- Architecture et roadmap actualisées pour refléter le socle réellement livré.

### Vérifications réellement exécutées

| Vérification | Résultat |
| --- | --- |
| `docker compose --profile tools run --rm backend-test` | `BUILD SUCCESS` : compilation/JAR Java 21, 3 tests ArchUnit et 5 tests d'intégration PostgreSQL réussis, aucun test ignoré |
| Nouvelle exécution ciblée `-Dtest=ArchitectureTest test` après relecture des frontières | 3 tests réussis ; contrôle des packages exacts, graphe autorisé et interdiction de dépendre du bootstrap |
| Flyway sur PostgreSQL Testcontainers isolé | V1 appliquée, schéma validé, deuxième migration sans effet ; rôle applicatif sans droit CREATE |
| Sécurité HTTP en intégration | Santé publique sans détails internes ; routes non autorisées en 401 sans redirection ; POST sans CSRF en 403 |
| `npm ci`, `npm run test:ci`, `npm run build` | Installation depuis le lockfile, 4 tests réussis, build de production réussi ; audit npm : 0 vulnérabilité signalée |
| Builds Docker backend/frontend/MinIO | Réussis ; Java 21 utilisé dans Docker car absent de l'hôte |
| Compose et `node scripts/check-local.mjs` | Cinq services sains ; santé/readiness/liveness, proxy Nginx, page Angular, refus 401/403, MinIO et Mailpit vérifiés par HTTP |
| Navigateur réel, page servie par Compose | « API disponible », bouton fonctionnel, aucune erreur JavaScript observée à l'état sain ; affichage mobile vérifié sans débordement horizontal |
| Arrêt volontaire du seul PostgreSQL BricoComptoir | Santé/readiness en 503, liveness en 200, page affichant « API indisponible » ; redémarrage et nouvelle vérification rétablissent « API disponible » |
| `npm start -- --host 127.0.0.1 --port 4201` | Serveur de développement et proxy `/api/**` vérifiés dans le navigateur ; serveur temporaire arrêté ensuite |
| Générateur `.env` exécuté à nouveau | Fichier existant conservé à l'identique ; secrets jamais affichés |
| Relecture du diff et contrôle de l'index Git | `git diff --cached --check` réussi ; 65 fichiers prévus, aucun secret local ni fichier généré/exclu détecté dans l'index |

Les tests ArchUnit autorisent les couches encore vides, mais une fixture
volontairement invalide prouve le rejet de Spring dans le domaine. Les tests
métier seront ajoutés avec leurs vrais cas d'usage. Un test de mise à niveau
V1 → V2 accompagnera la première évolution de schéma.

Un incident Docker Desktop local a interrompu le premier essai : son redémarrage
était ensuite bloqué par un socket temporaire `dockerInference`. Le répertoire
temporaire a été sauvegardé par renommage puis recréé, sans suppression d'image
ou de volume. Les validations réussies ci-dessus ont été réalisées après reprise.
La mémoire du conteneur de test Java est bornée. La sonde frontend a aussi été
corrigée pour joindre explicitement IPv4, après constat d'un refus sur `localhost`.

### Livraison et limites

- Le commit initial regroupe le socle, ses tests et les documents de cadrage
  préexistants : le dépôt était encore sans commit au début de cette étape.
- La CI est configurée ; elle n'a pas encore été exécutée sur GitHub, aucun
  push n'étant demandé. Ses commandes de build/test ont été exécutées localement.
- Pas de validation du backend en mode natif Windows, faute de JDK 21 sur
  l'hôte ; compilation, tests et démarrage réels ont tous été réalisés avec Java 21
  dans Docker. Angular a été vérifié sous Windows et dans son image Linux.
- MinIO et Mailpit sont prêts pour le développement ; aucun upload, email,
  paiement, compte client ou commande métier n'est présenté comme implémenté.
- Prochaine étape : identité et authentification, selon l'étape 2 de la roadmap.

## Journal — étape 2 : identité et accès

Réalisé le 24 septembre 2026. Le [contrat et la matrice](identity.md) distinguent
les routes effectives des permissions réservées aux futurs modules métier.

### Livrables

- Comptes clients et internes, rôles `CUSTOMER`, `CATALOG_MANAGER`,
  `ORDER_MANAGER`, `ADMIN`, activation, profil et lecture propriétaire ou admin.
  L'inscription publique attribue exclusivement `CUSTOMER`.
- Migration V2 PostgreSQL : comptes, rôles, récupération et audit d'accès.
  Adaptateurs JDBC et transactionnels autour d'un domaine/application Java purs.
- Connexion/déconnexion par session Spring Security, cookies adaptés au profil,
  CSRF SPA Spring/Angular et rotation du jeton après changement de session.
  Version du compte relue à chaque requête authentifiée ; une désactivation,
  un changement de rôles ou de mot de passe révoque les anciennes sessions.
- Récupération fonctionnelle via SMTP : lien dans le fragment URL, jeton aléatoire
  de 256 bits, empreinte en base, délai de 30 minutes et usage unique. Mailpit
  reçoit les emails locaux ; variables SMTP de production sans secret versionné.
- Création explicite du premier admin limitée au profil local, avec secret fourni
  à l'exécution et refus si un administrateur actif existe. Modifications
  d'accès sérialisées ; dernier admin actif protégé.
- Page Angular pour inscription, connexion, déconnexion, profil et récupération.
  Les décisions d'accès sont prises au backend. README, architecture et roadmap
  actualisés.

### Vérifications exécutées

| Vérification | Résultat |
| --- | --- |
| `docker compose --profile tools run --rm backend-test -B -ntp verify` | `BUILD SUCCESS` : 8 tests unitaires/architecture et 8 tests d'intégration PostgreSQL/Spring Security, aucun échec ni test ignoré. Connexion, CSRF, rotation de session, propriété du compte, refus de rôle, rôle injecté à l'inscription, révocation, récupération, expiration du lien, refus du second bootstrap et dernier admin concurrent vérifiés |
| `npm run test:ci` et `npm run build` | 6 tests Angular réussis ; build de production réussi |
| `docker compose config --quiet` et `docker compose up --build --detach --wait backend frontend` | Configuration valide, builds Java/Angular réussis ; migration V2 appliquée à la base locale existante ; cinq services sains |
| `node scripts/check-local.mjs` | Neuf contrôles HTTP du socle réussis après migration |
| Parcours HTTP sur les conteneurs locaux | Inscription `CUSTOMER` malgré rôle `ADMIN` dans le JSON, connexion, lecture du profil, email reçu dans Mailpit, changement de mot de passe, ancienne session 401, reconnexion réussie |
| Reconstruction backend finale | Image compilée avec le dernier correctif ; conteneur sain, readiness directe et santé via le proxy frontend en HTTP 200 |
| Contrôle documentaire, diff et secrets | 27 liens Markdown locaux résolus ; `git diff --check` réussi ; 76 fichiers source parcourus sans secret local, clé privée, marqueur de conflit ni espace final |

Le parcours local a créé un compte de vérification à email aléatoire et un email
dans les volumes de développement ; aucune donnée de production n'a été touchée.
Les tests d'intégration utilisent une base PostgreSQL isolée. Le test du dernier
administrateur tente deux désactivations simultanées : une seule réussit et un
administrateur actif subsiste. Le build frontend a aussi été réalisé dans son
image Docker et sous Windows. Aucun commit n'est demandé pour cette étape.

### Limites et prochaine étape

- Les rôles catalogue/commandes sont créés mais leurs routes métier n'existent
  pas encore. Le premier administrateur réel est à créer par l'exploitant avec
  la commande documentée et ses propres identifiants.
- Sessions et limites d'essais sont en mémoire d'une instance backend. Avant
  l'ouverture publique : mesurer Argon2id, valider le fournisseur SMTP et sa
  délivrabilité, surveiller SMTP séparément de la santé API, configurer le
  proxy/TLS, vérifier les limites et l'exploitation.
- Prochaine étape : catalogue, produits et packs selon la roadmap.

## Journal — étape 3a : catalogue de produits

Réalisé le 24 septembre 2026. Cette tranche termine le catalogue de fiches et
variantes demandé ; l'étape 3b (packs et instantané des offres pour la commande)
reste ouverte. Voir le [contrat catalogue](catalog.md).

### Livrables

- Catégories hiérarchiques et marques actives/archivées ; fiches produits et
  variantes portant SKU, unité de vente, prix TND et état brouillon/publié.
  Caractéristiques textuelles bornées ; seules les données renseignées sont
  exposées. Le domaine et l'application restent en Java pur ; adaptateurs JDBC,
  transactions et REST à la périphérie.
- Migration Flyway V3 : clés étrangères, unicités, versions, statuts, prix
  `numeric(12,3)` positif, prévention des cycles de catégories et index de
  filtres/recherche trigramme. Prix JSON sous forme de chaîne et devise TND.
- API publiques de lecture avec recherche littérale, filtre de sous-catégorie,
  marque, intervalle de prix sur une même variante, tri autorisé et pagination
  serveur. Fiches et variantes brouillon invisibles au public. API de gestion
  réservées à `CATALOG_MANAGER` et `ADMIN` avec CSRF et versions optimistes.
- Page Angular de consultation et formulaires de gestion. Deux fiches de
  [démonstration fictives](../infra/demo/catalog-demo.sql), explicitement
  séparées des migrations et signalées `demo: true` après chargement manuel.
  Aucune spécification technique de produit réel n'a été inventée.
- Architecture, matrice de permissions, roadmap et commandes locales actualisées.

### Vérifications exécutées

| Vérification | Résultat |
| --- | --- |
| `docker compose --profile tools run --rm backend-test -B -ntp verify` | `BUILD SUCCESS` : 10 tests unitaires/architecture et 9 tests d'intégration sur PostgreSQL Testcontainers, aucun échec ni test ignoré. La migration V3 s'applique sur base vierge. |
| `CatalogIT` | Lecture publique et accès administratifs 401/403, CSRF 403, catégorie cyclique 409, brouillon produit/variante masqué, recherche SKU, filtre descendant de catégorie et marque, intervalle de prix sur une même variante, tri/prix et pagination, versions périmées, SKU dupliqué et prix invalides vérifiés. |
| `npm run test:ci` et `npm run build` | 8 tests Angular réussis ; build optimisé réussi. |
| `docker compose up --build --detach --wait --wait-timeout 180 backend frontend` | Images Java/Angular reconstruites ; base locale existante migrée réellement de V2 vers V3 ; backend et frontend sains, ainsi que PostgreSQL et Mailpit. |
| `node scripts/check-local.mjs` et `GET /api/v1/products` via Nginx | Neuf contrôles HTTP du socle réussis ; catalogue public en 200 avec pagination. |
| Chargement manuel du jeu fictif dans le volume local, puis navigateur | Deux fiches marquées `DÉMO`/« Démonstration fictive », prix TND exacts et filtre de recherche observés dans l'interface. |

Les tests d'intégration emploient une base éphémère, sans données commerciales.
Le jeu fictif a été chargé **uniquement** dans la base Compose locale pour la
vérification de l'interface ; il n'est ni automatique ni destiné à la vente.
Les packs, le stock, la disponibilité et la commande restent à réaliser.
Aucun commit n'a été demandé pour cette tranche ; les changements d'identité
antérieurs restent également non committés dans l'arbre de travail.

## Journal — étape 3a-media : photos et import CSV

Réalisé le 27 septembre 2026. Les packs de l'étape 3b restent ouverts.

### Livrables

- Module `media` hexagonal : `ObjectStorage` interchangeable, adaptateur MinIO,
  traitement JPEG/PNG, application et adaptateurs JDBC/REST. Le seul lien vers
  `catalog` passe par `CatalogProductQueries`, port public. Migration V4 pour
  les métadonnées, contraintes d'ordre, image principale unique et index ;
  aucun binaire en PostgreSQL. Bucket privé, originaux non servis en HTTP.
- Gestion de 1 à 12 photos par produit (1 à 4 par requête), ordre et principale.
  Contrôles serveur de rôle/CSRF, type décodé, type déclaré, taille et
  dimensions ; rendus JPEG compressés pour carte et fiche, sans agrandissement.
  L'API publique ne sert que les images de produits encore visibles. Angular
  charge les métadonnées de la page affichée, `srcset`/`sizes`, images différées.
  Une route de rendu réservée aux gestionnaires permet de prévisualiser les
  photos des brouillons sans ouvrir leurs URL publiques.
- Import CSV de création seule en deux temps : aperçu sans écriture avec erreurs
  par ligne/champ, empreinte SHA-256, puis même fichier revalidé dans une
  transaction. Limites et format dans [catalog.md](catalog.md). Aucune donnée
  commerciale ni caractéristique technique réelle ajoutée.
- Compose relie désormais le backend à MinIO ; variables par environnement
  sans secret versionné. Architecture, RBAC, README et roadmap actualisés.

### Vérifications exécutées

| Vérification | Résultat |
| --- | --- |
| `docker compose --profile tools run --rm backend-test -B -ntp verify` | `BUILD SUCCESS` : 13 tests unitaires/architecture, 11 tests d'intégration PostgreSQL/API réussis ; 2 tests MinIO locaux désactivés par défaut, aucun échec |
| `MediaCatalogIT` | Téléversements valides et rejetés, lot atomique à la validation, rôles et CSRF, métadonnées, ordre/principale, brouillons invisibles au public mais prévisualisables par gestionnaire, import aperçu/erreurs/empreinte/application vérifiés |
| Relance ciblée `MediaCatalogIT` après la route de prévisualisation administrative | `BUILD SUCCESS` : 2 tests PostgreSQL/API réussis, y compris refus d'un autre produit et refus du rôle client |
| Tests MinIO locaux ciblés avec `BRICO_LOCAL_MINIO_TEST=true` | `MinioStorageLocalIT` : écriture/lecture/suppression réelles ; `MediaMinioLocalIT` : manager → API → PostgreSQL/MinIO → rendu public, puis suppression des objets ; tous deux réussis |
| `npm run test:ci` et `npm run build` | 9 tests Angular réussis ; build optimisé réussi, y compris chargement limité à la page et `srcset` différé |
| `docker compose config --quiet`, `docker compose up --build --detach --wait backend frontend` | Configuration valide, images finales reconstruites ; PostgreSQL, MinIO, Mailpit, backend et frontend sains ; migration locale V4 appliquée |
| `node scripts/check-local.mjs`, lecture de la version Flyway locale | 9 contrôles HTTP réussis ; version locale `4` confirmée |
| `git diff --check` et contrôle des nouveaux fichiers | Aucune erreur d'espacement ; `.env` reste ignoré, aucun secret ajouté |

Deux essais intermédiaires ont échoué uniquement dans les fixtures des tests
(SKU de fixture en minuscules, puis attente 401 avant contrôle CSRF) ; les
fixtures ont été corrigées, les tests ciblés puis la suite finale ont réussi.
Un premier lancement Angular a rencontré un refus d'accès aux fichiers dans
le bac à sable ; la relance autorisée a réussi. Aucun commit n'est demandé ;
les modifications des étapes précédentes demeurent non committées.

Limite connue : les objets MinIO et la transaction PostgreSQL ne forment pas
une transaction distribuée. L'application supprime les objets déjà écrits si
une écriture du lot échoue ; un échec au commit ou du nettoyage peut laisser
des objets orphelins. Une réconciliation est requise avant un catalogue de
production à volume significatif. Les originaux restent privés et les rendus
HTTP utilisent `Cache-Control: no-store` pour revérifier la publication.

## Journal — étape 4a : noyau d'inventaire

Réalisé le 27 septembre 2026. Les [invariants et la stratégie de concurrence](inventory.md)
ont été documentés avant le code. Le stock physique comprend les unités encore
réservées ; le stock disponible est la différence physique moins réservé.

### Livrables

- Domaine et application Java purs : soldes entiers par UUID de variante/SKU,
  ajustement signé avec motif et identifiant idempotent, réservation atomique
  multi-SKU, libération et conversion en sortie de stock. Un rejeu identique
  d'une transition terminale n'écrit pas de second mouvement ; une transition
  contraire est refusée.
- Adaptateur JDBC et transactionnel PostgreSQL : `READ COMMITTED`, verrou de
  l'en-tête de réservation puis verrous `FOR UPDATE` pris séparément par UUID
  croissant. Migration Flyway V5 pour soldes, réservations, ajustements et
  mouvements, avec unicités, index, checks non négatifs et journal non
  modifiable par le rôle applicatif. Référence catalogue via son port public.
- Disponibilité publique uniquement des variantes publiées ; consultation et
  ajustements HTTP réservés à `ADMIN`, avec CSRF. Les opérations de réservation
  sont des ports Java internes en attente du vrai module `sales` ; aucune
  commande ou expédition fictive n'est exposée.

### Vérifications exécutées

| Vérification | Résultat |
| --- | --- |
| `docker compose --profile tools run --rm backend-test -B -ntp verify` | `BUILD SUCCESS` : 16 tests unitaires/architecture ; 17 tests d'intégration recensés, 15 exécutés sans échec sur PostgreSQL 18.6 via Testcontainers, 2 tests MinIO locaux désactivés par défaut |
| `InventoryRulesTest` | Soldes et non-négativité, agrégation/ordre des SKU, dépassement entier et transitions terminales vérifiés |
| `InventoryIT` | 4 scénarios réussis : mouvements et replays, rollback d'une transaction comprenant ajustement puis échec du second SKU, deux transactions concurrentes sur la dernière unité (une seule réussit), permissions HTTP/CSRF, visibilité des brouillons et contraintes PostgreSQL |
| `FoundationIT` | Migration V5 appliquée, validée et sans nouvelle exécution au second passage |
| `git diff --check` et recherche d'espaces finaux dans les nouveaux fichiers inventory | Aucune erreur ; avertissement CRLF préexistant sur `frontend/src/index.html` sans incidence |

Le premier lancement d'intégration a révélé qu'un adaptateur JDBC `final` ne
pouvait être proxifié par Spring ; sa déclaration a été corrigée. Un test ciblé
a ensuite échoué sur la classe d'exception choisie pour un privilège SQL refusé ;
il vérifie désormais le code PostgreSQL `42501`. La suite complète finale est
verte. À la clôture de 4a, l'étape 4b restait ouverte : les packs de 3b
n'existaient pas encore. Elle a été vérifiée ensuite dans le journal 3b.
Aucun frontend de stock ni parcours de commande n'a été
ajouté dans ce noyau.

## Journal — étape 3b : packs et composants partagés

Réalisé le 27 septembre 2026. La [composition, les invariants et les contrats](packs.md)
ont été documentés avant le code. L'étape 4b est aussi vérifiée pour les SKU
partagés et l'effet d'une réservation sur plusieurs packs.

### Livrables

- Module `packs` hexagonal, distinct de `catalog` et `inventory` : domaine pur,
  cas d'usage, ports de lecture des SKU et des soldes, adaptateurs inter-modules
  n'appelant que les ports publics, persistance JDBC et transactions. Migration
  Flyway V6 avec prix `numeric(12,3)`, contraintes, versions et index ; aucun
  stock de pack autonome. Les SKU répétés dans une composition sont agrégés.
- Fiches avec slogan, guide, statut et versions ; variantes avec composition,
  prix TND et publication. Les API publiques masquent brouillons et composants
  dépubliés ; la gestion est limitée à `CATALOG_MANAGER` et `ADMIN`, avec CSRF.
  Le port d'offre fournit une copie versionnée de la composition et du prix
  pour la future commande, qui devra les revalider avant réservation.
- Interface Angular de consultation et d'administration des packs, avec recherche
  de SKU et édition des variantes. Cinq packs fictifs ont été créés en brouillon
  dans la base Compose locale par [script manuel](../infra/demo/packs-demo.sql) ;
  ils restent hors Flyway, marqués `demo=true` et impossibles à publier via l'API
  de gestion courante. Leurs noms et composants exemples ne sont pas des offres
  commerciales validées.

### Vérifications exécutées

| Vérification | Résultat |
| --- | --- |
| `docker compose --profile tools run --rm backend-test -B -ntp verify` | `BUILD SUCCESS` : 18 tests unitaires/architecture et 19 tests d'intégration recensés, dont 17 exécutés sans échec sur PostgreSQL Testcontainers ; 2 tests MinIO locaux ignorés par défaut |
| `PackRulesTest` et `PacksIT` | Agrégation des occurrences, calcul de disponibilité, épuisement d'un composant, compositions variantes, port d'offre versionné, visibilité des brouillons et composants, permissions HTTP/CSRF, versions périmées et interdiction de publication DÉMO vérifiés |
| `FoundationIT` | Migration V6 appliquée et validée sur PostgreSQL réel |
| `npm.cmd run test:ci`, `npm.cmd run build` | 11 tests Angular réussis, dont 2 sur les packs ; build de production réussi. Un premier essai des tests a rencontré un refus d'accès du bac à sable ; la relance autorisée a réussi |
| `docker compose up --build --detach --wait --wait-timeout 180 backend frontend` | Images reconstruites, cinq services sains ; Flyway local en version 6 |
| Chargement du catalogue DÉMO puis du script packs DÉMO deux fois | 5 fiches brouillon, 8 variantes brouillon et 12 lignes de composants ; seconde exécution sans insertion ; `GET /api/v1/packs` public retourne `[]` |
| `node scripts/check-local.mjs` et navigateur local | 9 contrôles HTTP réussis ; page Angular servie, API disponible, section packs rendue en mobile sans débordement visible, message « Aucun pack publié pour le moment » |

La disponibilité affichée est indicative : deux variantes partageant un SKU ne
peuvent additionner leurs maxima. `PacksIT` vérifie qu'une réservation du SKU
commun réduit la disponibilité des deux packs. La décomposition d'une commande
mixte et sa réservation atomique seront ajoutées à l'étape 5 ; aucune commande
fictive n'a été introduite. L'étape 4 générale reste ouverte pour les réceptions
et l'administration complète du journal de stock. Aucun commit n'a été demandé.

## Journal — étape 5a : panier visiteur et client

Réalisé les 27 et 28 septembre 2026. La [stratégie de reprise et les contrats](cart.md)
ont été documentés avant le code. Le panier visiteur reste dans le navigateur ;
le panier client est désormais persisté par compte dans le module `sales`.

### Livrables

- Lignes `PRODUCT` par UUID de variante/SKU et `PACK` par UUID de variante de
  pack, quantités modifiables, suppression, limites et normalisation. Une
  représentation de demandes SKU déplie les compositions, multiplie les
  quantités et cumule le même composant entre produits et plusieurs packs,
  avec détection de dépassement entier.
- Migration V7 : panier client versionné, lignes et reçus de fusion
  idempotente. Verrou PostgreSQL par compte et transactions pour remplacement
  et reprise. L'identité est tirée de la session ; aucun identifiant de compte
  n'est accepté dans les corps de requête. Les API persistées exigent `CUSTOMER`
  et les écritures CSRF. Domaine/application Java purs, modules catalogue,
  packs et stock joints seulement via leurs ports publics.
- API d'estimation visiteur, API privée de lecture/remplacement/fusion ;
  résolution actuelle des offres et prix TND exacts, ruptures sur la demande
  cumulée. Une offre dépubliée reste identifiable comme indisponible ; aucun
  tarif de livraison, prix définitif ou stock réservé n'est annoncé.
- Angular : ajout depuis produits et variantes de packs, panier vide, quantités,
  retrait, erreurs et estimation. Stockage visiteur limité aux références,
  quantités et UUID de fusion ; à la connexion/session restaurée, fusion
  transactionnelle une seule fois et effacement local uniquement après succès.
  Les lignes locales restent conservées en cas d'échec.

### Vérifications exécutées

| Vérification | Résultat |
| --- | --- |
| Tests ciblés `CartRulesTest` / `CartIT` via Docker | `BUILD SUCCESS` : 3 tests unitaires et 2 tests PostgreSQL/API réussis ; migration V7 réellement appliquée |
| `docker compose --profile tools run --rm backend-test -B -ntp verify` final | `BUILD SUCCESS` : 21 tests unitaires/architecture ; 21 tests d'intégration recensés, 19 exécutés sans échec sur PostgreSQL Testcontainers et 2 tests MinIO locaux désactivés par défaut |
| `CartRulesTest` | Fusion des doublons, limites, empreinte indépendante de l'ordre, demande partagée produit/packs et dépassement entier vérifiés |
| `CartIT` | Estimation visiteur sans persistance ni livraison, CSRF, droits client/interne, isolation entre comptes, lignes en PostgreSQL, reprise idempotente, rejeu incompatible, version périmée, remplacement inchangé sans incrément, rollback d'une fusion dépassant les limites et offre devenue invisible vérifiés |
| `npm.cmd run test:ci`, `npm.cmd run build` finaux | 14 tests Angular réussis ; build de production réussi. Les tests du panier vérifient le stockage sans prix, l'estimation serveur, la conservation après échec puis l'effacement après reprise et l'effacement de l'affichage du compte précédent lors d'une nouvelle session |
| Démarrage Docker Compose avec reconstruction | Backend, frontend, PostgreSQL, MinIO et Mailpit sains ; PostgreSQL local confirme `V7 customer cart`, `success=true` |
| `node scripts/check-local.mjs` | 9 contrôles HTTP réussis : interface, API directe/proxy, santé, refus d'accès, MinIO et Mailpit |
| Parcours navigateur local visiteur | État vide, ajout de l'article DÉMO à 2.375 TND, conservation après rechargement, quantité 2 estimée à 4.750 TND, indication du stock insuffisant et retrait ramenant au panier vide vérifiés |

Les premiers essais ont révélé un nom de type Java ambigu avec l'annotation
Spring `Component`, puis une fixture d'intégration supposant une base vide
malgré l'autre scénario ; ces problèmes ont été corrigés. Le test de connexion
Angular a été adapté à sa nouvelle requête de lecture du panier.

Une première tentative de suite complète est restée bloquée dans le CLI Docker,
sans lancement des tests. Après diagnostic et autorisation explicite de
redémarrage de Docker Desktop, le CLI de redémarrage étant également bloqué,
l'application Windows a été relancée sans supprimer de volume. La suite
complète finale ci-dessus a ensuite réussi.

Le checkout reste à réaliser : il devra recalculer prix, composition,
publication, livraison et demande SKU, puis réserver atomiquement avec la
commande. Le panier n'effectue aucune réservation. Aucun commit n'est demandé.

## Journal — étapes 5b et 6 : checkout COD et cycle de commande

28 septembre 2026. Contrat, propriété invitée, invariants et stratégie de
concurrence documentés avant implémentation dans [checkout.md](checkout.md).
Validation finale terminée : tous les tests critiques passent, ainsi que les
suites backend et Angular. Les étapes 5 et 6 satisfont leurs critères de sortie.

### Livrables

- Checkout invité ou client `CUSTOMER`, adresse tunisienne validée sur la
  forme, zones desservies et forfait de livraison configurables. Paiement
  `CASH_ON_DELIVERY` uniquement. Sans configuration de production, achat fermé.
  Les 7.000 TND et zones TUNIS/SFAX du profil local/test sont des démonstrations,
  pas des tarifs commerciaux validés.
- Récapitulatif serveur, comparaison des versions depuis le panier puis de
  l'empreinte du récapitulatif à confirmation. Relecture des prix, publication,
  composition et versions des composants ; calcul exact TND et cumul des SKU
  partagés entre produits et packs. Empreinte liée au compte/session ayant
  prévisualisé l'achat pour interdire un rejeu après expiration sous une autre
  identité. Aucun prix ni tarif accepté du navigateur.
- Commande, instantané immuable des lignes/composants/adresse/montants, reçu
  idempotent et réservation agrégée dans une seule transaction PostgreSQL.
  Verrouillage de la clé par propriétaire, ordre stable des verrous stock,
  reprise bornée de toute la transaction pour collision `REPEATABLE READ`.
- Migration V8 : commandes, clés, événements, contraintes et index. Privilèges
  SQL limitant les mises à jour de commande aux colonnes de traitement ; ni
  modification ni suppression d'instantanés/événements par le rôle applicatif.
- Préparation, annulation, expédition et livraison via actions explicites.
  Annulation libérant la réservation ; expédition consommant les composants
  une fois. Commande verrouillée avant stock, transitions et événements
  atomiques. Listes paginées, filtre de statut et rôles `ORDER_MANAGER`/`ADMIN`.
- Propriété serveur : compte ou UUID de session invitée `HttpOnly` ; autre
  propriétaire reçoit `404`. Les commandes invitées ne sont pas rattachées
  automatiquement après connexion. CSRF et contrôle de rôle côté backend.
- Angular : formulaire, récapitulatif des lignes/composants et frais, COD,
  confirmation/réessai avec même clé et même corps après réponse incertaine,
  refus de récapitulatif devenu périmé après changement du panier, historique
  client et traitement interne. Le panier acheté est vidé seulement s'il n'a
  pas changé ; un conflit inter-onglets le conserve. Adresse jamais enregistrée
  dans `localStorage` ; vues privées effacées au changement de session.

### Vérifications exécutées

| Vérification | Résultat |
| --- | --- |
| Compilation backend Java 21 via Docker | `BUILD SUCCESS`, puis build Docker final réussi |
| `OrderRulesTest`, `OrderServiceTest`, `ArchitectureTest` ciblés | 9 tests réussis : adresse/frais exacts, versions/doublons, empreinte, transitions, demande partagée et nouvelles confirmations |
| Suite finale `./mvnw -B -ntp verify` via le service Docker `backend-test`, copie temporaire Linux du code final | `BUILD SUCCESS` : 27 tests unitaires/architecture ; 27 tests d'intégration recensés, 25 exécutés sur PostgreSQL Testcontainers sans échec et 2 tests MinIO locaux désactivés par défaut |
| `OrderIT` final | 6 scénarios réussis, aucun ignoré : pack + produit partageant un SKU, double soumission concurrente et rejeu idempotent, dernière unité disputée, prix/composition périmés, rupture et rollback après réservation, propriété et permissions, annulation/libération unique, expédition/consommation unique et course annulation/expédition. Rejeu d'un ancien récapitulatif sous une autre session rejeté |
| `npm.cmd run test:ci` final | 18 tests Angular réussis : récapitulatif, double clic, même clé/corps après réponse incertaine, prix changé, panier modifié, actions manager et changement de session |
| Build Angular natif puis build Docker final | Réussis |
| `docker compose up --build --detach --wait --wait-timeout 180 backend frontend` | Réussi, cinq services sains ; PostgreSQL local confirme V8 `orders checkout`, `success=true` |
| `node scripts/check-local.mjs` et `docker compose ps` finaux après reprise Docker | 9 contrôles HTTP locaux réussis ; backend, frontend, PostgreSQL, MinIO et Mailpit sains |
| Navigateur local | Formulaire invité ouvert depuis un article DÉMO, adresse fictive envoyée au serveur, stock nul refusé avec message français avant toute commande |

Les premières fixtures attendaient une version de fiche zéro malgré la création
du SKU qui l'incrémente ; corrigé après refus `OFFER_CHANGED` réel. Le test
d'immuabilité a ensuite détecté les privilèges DML par défaut de V1 : V8 révoque
`UPDATE/DELETE` avant les droits limités par colonne, et le test tente désormais
une réécriture valide du contenu pour vérifier les droits indépendamment des
contraintes. Le rollback après réservation est testé par une erreur de
persistance injectée, attendue en HTTP 500, puis par un nouvel essai réussi.

Un disque C: plein a ensuite interrompu deux essais de suite backend avant leur
fin. Après autorisation, seuls les deux JAR générés et le cache de compilation
Docker inutilisé ont été supprimés (5.131 GB de cache récupérés). Le redémarrage
de Docker Desktop était bloqué par un socket temporaire orphelin ; son répertoire
runtime a été conservé par renommage puis recréé. Aucun volume de données ni
source n'a été supprimé. La suite finale complète a été relancée seule depuis
une copie temporaire du backend dans le système de fichiers Linux du conteneur,
pour éviter les écritures de JAR sur C:. Ses rapports sont conservés sous
`backend/target/checkout-final-reports/` (ignorés par Git). Les résultats réussis
du tableau proviennent de cette dernière exécution, pas des essais interrompus.

Limites : frais/zones et catalogue commercial à valider avant ouverture,
vérification d'adresse de forme seulement, sessions invitées non récupérables
après expiration/redémarrage, pas d'expiration automatique des réservations COD,
ni paiement en ligne ni retours après expédition. Aucun commit demandé.

## Journal — interface publique, 28 septembre 2026

### Périmètre terminé

- Accueil, entrées solution/produit, liste des packs, catalogue/recherche,
  fiches, panier, livraison, confirmation relue depuis l'API et espace compte.
  Navigation Angular, URLs de recherche partageables, pages de gestion chargées
  séparément. Session et panier communs aux routes, reprise après connexion.
- Design mobile first blanc/rouge/sombre, états de chargement, vide, erreur et
  réessai, liens clavier, focus visible et lien d'évitement. Adresse avec erreurs
  associées, annonce et focus sur le premier champ invalide ; quantités entières.
- Catalogue filtré, trié et paginé par le serveur. Fiches pack avec noms réels
  des composants, quantités, variantes, guide et exclusions calculées depuis les
  compositions publiées. Ajout minimal de `componentNames` au contrat pack via
  un port catalogue ; aucune dépendance d'infrastructure dans le métier.
- Images MinIO dérivées carte/détail, `srcset`/`sizes`, chargement différé,
  apparition progressive, espace réservé et repli explicite sans photo. Respect
  de l'image principale. Pas d'originaux transmis pour les cartes.
- Checkout et confirmation utilisent les API existantes : prix/frais serveur,
  paiement à la livraison, réessai idempotent, avertissement avant de quitter
  une soumission incertaine, confirmation persistée et annulation autorisée.
- Contrats et arbitrages détaillés dans [storefront.md](storefront.md), avec
  liens depuis le README et l'architecture.

### Vérifications exécutées

| Vérification | Résultat |
| --- | --- |
| `npm.cmd run test:ci`, source finale | 26 tests réussis dans 7 fichiers : catalogue/requêtes/erreurs, composition et choix de variante, média/principal, quantités, restauration de session, compte/CSRF, panier et checkout |
| Backend ciblé `ArchitectureTest,PackRulesTest` et `PacksIT` via Docker | 5 tests unitaires/architecture et 2 tests PostgreSQL Testcontainers/API réussis, dont les noms des composants publics ; rapports dans `backend/target/storefront-reports/` |
| Builds | Angular natif réussi, builds Docker backend et frontend réussis ; dernier build frontend après corrections réussi |
| Démarrage final et `node scripts/check-local.mjs` | 5 services sains et 9 contrôles HTTP réussis |
| Navigateur réel, viewport mobile 390 × 844 et bureau | Accueil, solution, variantes/exclusions, recherche avec prix maximum et tri, fiches et images MinIO dérivées chargées, modifications de quantité au clavier, panier mixte, erreurs d'adresse, checkout COD, confirmation après rechargement, annulation |
| Parcours commande/stock réel | Total 18.125 TND (11.125 + 7.000). PostgreSQL : 3 unités du SKU partagé et 1 outil réservés, puis 0/0 après annulation, stock physique inchangé à 20/20 |

Le parcours utilise un jeu local temporaire explicitement nommé `TEST UI`,
une adresse fictive « NE PAS LIVRER » et une image géométrique synthétique
envoyée par l'API média à MinIO. La commande d'essai a été annulée ; les deux
produits et le pack ont été remis en brouillon. Le compte technique éphémère
est désactivé et sans rôle. Les cinq packs DÉMO initiaux restent en brouillon.
Les instantanés et mouvements d'audit locaux sont conservés.

Limites : la petite liste de packs conserve le contrat existant non paginé et
son filtre textuel local. Les exclusions sont dérivées des autres variantes,
sans inventer de promesses commerciales. Photos, fiches, prix et zones réels
restent à valider avant ouverture ; cette livraison ne clôt donc pas l'étape 7.
La suite backend complète de l'étape précédente n'a pas été relancée : seuls
les tests concernés par le contrat pack ont été exécutés ici. Aucun commit demandé.

## Journal — administration, 28 septembre 2026

### Périmètre terminé

- Espace `/gestion` avec sections selon les rôles : catalogue, produits/SKU,
  prix, photos, catégories, marques et packs/composants pour `CATALOG_MANAGER`
  ou `ADMIN` ; commandes pour `ORDER_MANAGER` ou `ADMIN` ; stocks et livraison
  pour `ADMIN`. Accès direct à une section interdite refusé sans charger ses
  données. Autorisations et CSRF restent contrôlés par Spring Security.
- Tableaux paginés côté serveur, recherches, états de chargement et erreurs
  françaises de validation, refus et conflits. Sélection de composants par SKU
  catalogue, quantités cumulées ; gestion des photos et import CSV existants
  repris dans l'espace catalogue. Ajustements de stock avec motif et réessai
  du même identifiant/corps après une réponse incertaine.
- Migration V9 : contenus principaux de l'accueil et frais de livraison
  persistés avec contraintes, version optimiste et dernier éditeur ; index
  pour les lectures paginées. Le petit module `content` reste indépendant.
  L'accueil lit réellement ses textes depuis l'API publique.
- Frais forfaitaires TND exacts, choix des gouvernorats et activation du
  checkout. La configuration d'environnement reste effective jusqu'au
  premier enregistrement. Un changement invalide l'ancien récapitulatif,
  sans modifier les instantanés des commandes déjà créées.
- Matrice, contrats, arbitrages et limites dans
  [administration.md](administration.md), référencés par l'architecture et le README.

### Vérifications exécutées

| Vérification | Résultat |
| --- | --- |
| Suite backend finale `./mvnw -B -ntp verify` via Docker, copie Linux du code final | `BUILD SUCCESS` : 29 tests unitaires/architecture, 28 tests d'intégration exécutés avec PostgreSQL Testcontainers, 2 tests MinIO locaux ignorés par défaut ; rapports dans `backend/target/admin-final-reports/` |
| `AdministrationIT` | 3 scénarios réussis : matrice des quatre rôles et anonyme, écritures hors périmètre et CSRF refusés, pagination stable/bornée, validation et conflits des réglages, frais modifiés et instantanés, transitions de commande et libération du stock |
| Angular `npm run test:ci`, source finale montée dans l'image de test | 35 tests réussis dans 8 fichiers : permissions/navigation, refus serveur, pagination, conflits de contenu, zones, réessai de stock, photos et parcours existants |
| `docker compose build backend frontend` | Deux builds finaux réussis |
| `docker compose up --no-build --detach --wait --wait-timeout 180 backend frontend` | Cinq services sains, migration locale V9 appliquée |
| `node scripts/check-local.mjs` | 9 contrôles HTTP réussis |
| Navigateur réel | Connexion locale, six sections administrateur, produits et stocks réels, frais existants, sauvegarde des textes d'accueil sans changement, filtre des commandes annulées ; aucun avertissement ni erreur JavaScript relevé |

Le premier passage backend a détecté une attente obsolète de huit migrations
dans `FoundationIT`. Elle a été actualisée à neuf, puis la suite complète a
été relancée avec succès. Les contrôles d'autorisation ne reposent pas sur les
onglets : les tests appellent directement les API avec les rôles concernés,
y compris les écritures de catalogue/photos, stocks, commandes et réglages.

Le compte administrateur local éphémère utilisé dans le navigateur a été
déconnecté, désactivé et privé de ses rôles. Aucun tarif, stock ni statut de
commande local n'a été modifié pendant cette vérification. La sauvegarde de
l'accueil a conservé les textes et incrémenté seulement sa version/audit.

Le disque C: plein a nécessité la suppression des seuls fichiers générés
`frontend/.angular/cache`, `frontend/dist` et `frontend/node_modules`.
Docker Desktop et WSL ont été redémarrés après blocage ; les volumes et sources
ont été conservés. Les validations finales ont été exécutées dans Docker.
Pour reprendre un développement Angular natif, réinstaller les dépendances
avec `npm ci` dans `frontend` lorsque l'espace disque le permet.

Limites : forfait unique de livraison, contenus d'accueil structurés sans CMS
généraliste, médias limités aux opérations existantes de téléversement/ordre/
image principale. Les tarifs, zones et données commerciales restent à valider
avant ouverture ; l'étape 7 globale reste ouverte. Aucun commit demandé.

## Journal — emails et notifications, 28 septembre 2026

### Périmètre terminé

- Module `notifications` indépendant, port `EmailProvider` et adaptateur SMTP
  réel vers Mailpit. Confirmation de commande, PREPARING, SHIPPED, DELIVERED,
  CANCELLED et récupération de mot de passe. Les producteurs rejoignent
  l'outbox dans leur transaction ; aucun envoi réseau pendant une transaction
  de commande ou d'identité. Une panne SMTP laisse commande/réservation validées.
- Migration V10 : outbox à clé métier unique, payload chiffré AES-256-GCM,
  état/échéance/essais/lease et index ; journal durable des nouvelles commandes,
  curseur singleton attribué sous verrou pour respecter l'ordre de commit.
  `MAIL_OUTBOX_KEY` injectée hors Git ; initialisation locale idempotente.
  Payload supprimé après envoi/annulation, métadonnées conservées.
- Worker à claims `SKIP LOCKED`, transactions courtes avant/après SMTP,
  comparaison du token de lease, reprise après crash et cinq essais maximum
  avec délais croissants. API paginée d'observation et relance manuelle des
  échecs terminaux réservée à `ADMIN`, avec CSRF et refus d'état/expiration.
- Destinataire figé côté serveur : adresse du compte actif connecté, ou email
  facultatif et validé au checkout invité. Un email fourni à la place de celui
  du compte est ignoré. Sans email invité, suivi par session navigateur.
  Récupérations du même compte sérialisées ; anciennes demandes en attente
  annulées et jetons expirés/réutilisés refusés.
- SSE et historique JSON réservés à `ORDER_MANAGER`/`ADMIN`, sans adresse ni
  téléphone. Reprise par `Last-Event-ID`/`after`, lots bornés, heartbeats, limite
  de connexions et fermeture sur logout/révocation/désactivation. Angular
  affiche les notifications, conserve le curseur par compte, ignore les
  doublons et reconnecte ; Nginx transmet le flux sans buffering.
- Contrats et limites documentés dans [notifications.md](notifications.md),
  architecture, identité, checkout, roadmap et README actualisés.

### Vérifications exécutées

| Vérification | Résultat |
| --- | --- |
| Suite backend finale `./mvnw -B -ntp verify` via Docker, copie Linux des sources | `BUILD SUCCESS` : 33 tests unitaires/architecture, 38 tests d'intégration recensés dont 36 exécutés avec PostgreSQL Testcontainers et 2 tests MinIO locaux ignorés par défaut ; zéro échec. Rapports `backend/target/notifications-final-reports/` |
| `NotificationsIT` | 8 scénarios réussis : destinataire serveur/instantanés/statuts/doublons, panne et reprise bornée/manualisée, workers concurrents et lease abandonnée, rollback après insertion email/événement, récupération/chiffrement/expiration, demandes concurrentes, ordre des commits, SSE/reconnexion/historique/RBAC/révocation sur flux ouvert |
| Tests du fournisseur et de la politique | Message-ID identique après remise, UTF-8 texte brut, refus d'injection d'en-tête, délais bornés, panne enregistrée et claim annulé non envoyé |
| Suite Angular finale via image Docker de test et sources montées | 39 tests réussis dans 9 fichiers, dont 4 sur droits de souscription, curseur/replay, reconnexion/logout et arrêt après 401 |
| `docker compose build backend frontend` | Deux builds réussis ; application Angular compilée, backend Java 21 compilé |
| Démarrage local des images | Cinq services sains ; V10 appliquée depuis V9 sur la base locale |
| Parcours local réel via API derrière Nginx et Mailpit | Mailpit arrêté : commande confirmée et SSE reçu avant envoi ; double placement retourne la même commande. Mailpit rétabli : confirmation reçue une seule fois au deuxième essai SMTP, annulation reçue et réservation libérée, lien de récupération reçu puis consommé, session antérieure invalidée |
| Reconnexion et rôles locaux | Événement retrouvé après interruption du flux ; `CATALOG_MANAGER` et `CUSTOMER` refusés en 403, anonyme en 401, `ORDER_MANAGER` accepté sur événements mais refusé sur outbox |
| `node scripts/check-local.mjs`, initialisation répétée et `git diff --check` | 9 contrôles HTTP réussis ; `.env` existant conservé ; aucun défaut de whitespace |

Les premiers essais ont détecté une copie MIME vide et la traduction d'un
curseur invalide en 500 par le proxy de persistance : transport et mapping
400 corrigés, puis scénarios et suite complète relancés avec succès. Le
script local attendait aussi un en-tête consommé par Nginx ; son assertion a
été corrigée, puis le parcours SMTP/SSE entier exécuté. Les preuves de panne
réelle proviennent du deuxième essai enregistré en base et du message unique
dans Mailpit, pas de la seule présence d'un claim PROCESSING.

Jeu local explicitement `TEST MAIL`, emails `example.invalid`, adresse
« NE PAS LIVRER », article et stock fictifs. Après vérification, commande
annulée, réservation libérée, article remis en brouillon, stock fictif ramené
à zéro et quatre comptes temporaires désactivés sans rôles. Mailpit rétabli,
historique de test conservé. Aucun secret ajouté à Git ni commit demandé.

Limites : SMTP ne garantit pas exactement une remise après réponse ambiguë ;
clés uniques et Message-ID stable limitent les doublons sans promettre une
garantie que le protocole ne fournit pas. Les jetons expirent après 30 minutes
même en cas de panne. Les envois déjà remis ne sont pas rappelables. SMTP de
production, supervision des échecs, rétention et sauvegarde de la clé restent
à définir avant ouverture ; l'étape 7 globale reste ouverte.

## Journal — étape 7c : protection des données

28 septembre 2026. Module `privacy` ajouté, orchestration par ports publics
`identity`, `sales` et `notifications`, règles de conservation et de livraison
en Java pur. Consultation du périmètre courant, préférence marketing par action
séparée, historique, export JSON paginé, rectification de livraison avant
préparation et rectification email avec preuve de possession de la nouvelle
boîte. Aucune case marketing précochée, aucune inscription induite par une
commande, un suivi ou une récupération de mot de passe. Pas de campagne fictive.

V11/V12 appliquées : coordonnées sorties des snapshots financiers immuables,
archives AES-256-GCM, clés distinctes hors Git, durée configurable et gels,
outbox corrélée sans identité exposée. Fermeture après commandes terminées,
suppression du panier et jetons, retrait marketing, compte neutralisé et
sessions révoquées. Le dernier ADMIN reste protégé. Verrou de compte ou scope
invité commun avec le placement protège contre une recréation concurrente ;
rollback intermodules vérifié. La consultation ciblée d’archive exige ADMIN,
réauthentification et référence de dossier, avec audit. Les coordonnées sont
masquées dans les listes administratives ; l’ouverture de fiche charge le détail.

Logs backend limités aux codes statiques, sans arguments ni piles d’exception ;
messages libres masqués. Journaux Nginx contenant IP/URI désactivés, politique
de référent ajoutée, erreurs de persistance génériques, identifiants d’acteurs
remplaçant leurs emails. Les durées techniques, paramètres d’exploitant,
hébergement/prestataires à choisir et points juridiques Tunisie/UE sont dans
[privacy-decisions.md](privacy-decisions.md). Aucune déclaration de conformité
automatique au RGPD.

Vérifications réellement exécutées :

- Suite backend complète `./mvnw -B -ntp verify` sous Java 21/Docker : **39 tests
  unitaires/architecture passés**, **50 intégrations découvertes, 48 exécutées
  et passées**, 2 tests spécifiques MinIO local ignorés par défaut. PostgreSQL
  18.6 réel via Testcontainers. Après extraction finale des règles et validation
  des corrections, relance ciblée identité/confidentialité/architecture :
  réussite, avec **2 nouveaux tests métier supplémentaires** et les **12
  scénarios `PrivacyIT`** passés.
- Scénarios couverts : propriété, absence d’opt-in implicite, retrait, CSRF,
  export sans données d’autrui ni secrets, réauthentification et limite de cinq
  échecs, email vérifié/expiré/rejoué et sessions, livraison/zone/statut,
  refus des commandes actives, dernier ADMIN, archive et gel, conservation
  automatique, refus CATALOG_MANAGER/ORDER_MANAGER, rollback et concurrence.
  Tests de chiffrement : aléa, intégrité et liaison à une commande ; tests de
  logs : arguments et exceptions imbriquées contenant des données masqués.
- Angular : **44 tests passés, 10 fichiers**, dont 5 sur les cases décochées,
  le choix distinct, CSRF, erreurs de permission, paramètres invités et nettoyage
  des saisies. Builds des images backend et frontend réussis ; reconstruction
  finale backend après revue des frontières.
- Compose : cinq services sains, migration depuis la base locale existante,
  `node scripts/check-local.mjs` : neuf contrôles passés. Le générateur de secrets
  ajoute la clé d’archive manquante puis laisse `.env` inchangé à la seconde
  exécution.
- API locale via Nginx : consentement initial faux, acceptation/retrait y compris
  ancienne notice, export propre/no-store, refus d’action administrative,
  `no-referrer`. SMTP réel Mailpit : lien de rectification reçu, confirmation,
  nouvelle adresse utilisable et anciennes sessions invalidées. Fermeture du
  compte de test sans commande réussie ; anciens identifiants refusés, manifeste
  temporaire retiré. Données exclusivement fictives `TEST`, `example.invalid`.
- Navigateur réel `/mes-donnees` : choix initial décoché, acceptation et retrait
  au clavier, message de réussite, export JSON préparé et champ sensible vidé ;
  après retrait/rechargement, retour au mode invité. Contrôle des logs locaux :
  aucun marqueur e-mail/mot de passe/cookie CSRF/adresse de cette vérification.
- `git diff --check` réussi ; seul avertissement CRLF préexistant dans
  `frontend/src/index.html`. Aucun secret ajouté à Git, aucun commit demandé.

Les premiers essais ont révélé une collision de noms de type dans le test,
une limite de connexion partagée par les clients de test, puis deux attentes
anciennes (nombre de migrations et code d’erreur interne). Corrections et
relances effectuées ; les résultats ci-dessus proviennent des essais réussis.
Les limites de connexion réelles restent testées dans la suite identité ;
le test de réauthentification confidentialité vérifie sa propre limitation.

Limites avant ouverture : `PRIVACY_LEGAL_DAYS=0` et conservation générale
désactivée par défaut, afin de demander une décision d’exploitant avant archivage
ou purge légale ; durées de test ne constituent pas une règle de droit. Archives
et références conservées restent personnelles/pseudonymisées, pas irréversiblement
anonymes. Notice publique/contact, formalités INPDP, conservation comptable,
hébergement/transferts, contrats, sauvegardes/clés, traitement des dossiers et
revue des comptes dormants restent à valider. L’étape 7 globale demeure ouverte.

## Journal — étape 7d : vérification reproductible et exploitation

28 septembre 2026. Suite Playwright verrouillée dans `e2e/`, projet Compose
`bricocomptoir-e2e` distinct des données habituelles, administrateur et secrets
aléatoires hors Git, fixtures `DÉMO E2E`/`example.invalid` créées par les API.
Deux achats mobiles et deux scénarios desktop utilisent le backend, PostgreSQL,
MinIO, Mailpit et SSE réels. Aucun réseau métier n’est simulé. Le test mixte
attend trois unités réservées du même SKU (deux dans le pack, une à l’unité),
puis leur sortie à l’expédition ; l’annulation du pack seul libère deux unités.

La CI comporte trois jobs : backend Java/ArchUnit/PostgreSQL/MinIO, Angular
Vitest/build, et images Compose/Chromium/restauration. Les deux tests MinIO
optionnels du socle sont activés en CI. Contrôles de migrations consécutives,
templates sans secrets, fichiers générés ignorés et espaces Git ; rapports
conservés sept jours. YAML du workflow analysé localement. Le workflow GitHub
lui-même n’a pas été exécuté à distance ni présenté comme réussi.
Actions figées par SHA des tags officiels `checkout`/`setup-node`/`upload-artifact`
v7 et `setup-java` v6, références vérifiées par `git ls-remote`. Checkout sans
conservation du jeton et permission `contents: read`.

Commandes réellement exécutées et résultats établis :

- `node scripts/e2e.mjs up` : construction des trois images réussie, cinq
  services sains sur des volumes neufs, douze migrations et bootstrap local.
- `mvnw -B -ntp verify` dans le conteneur d’outillage avec
  `BRICO_LOCAL_MINIO_TEST=true` : **41 tests unitaires/architecture et 50 tests
  d’intégration passés**, **zéro ignoré**, JAR compilé. PostgreSQL 18.6 réel via
  Testcontainers ; téléversement/rendus et stockage objet MinIO réels.
- `npm run test:ci` Angular : **44 tests passés, 10 fichiers**, d’abord dans
  le conteneur puis nativement sur une copie identique des sources sur D:.
  `npm ci` et `npm run build` natifs : build optimisé réussi, budgets respectés.
- `node scripts/check-local.mjs` : neuf contrôles passés sur l’environnement
  habituel. `node scripts/check-repository.mjs`, syntaxe des scripts et
  configuration Compose/override : réussites.
- `node scripts/e2e.mjs test`, dernière exécution complète : **4 tests passés,
  zéro ignoré**. Achats invité/client, reprise du panier persisté, totaux exacts,
  composants cumulés, confirmation relue, apparition dans l’administration,
  réservation/annulation/expédition, photos responsives réelles, originaux/bucket
  privés, emails de confirmation/statut, nouvelles commandes SSE et refus RBAC.
  Cette exécution couvre aussi une panne réelle par suspension de Mailpit
  E2E, commande durable, erreur d’outbox constatée, reprise, une confirmation
  reçue, événement retrouvé après interruption et aucun rejeu après rechargement.
- `node scripts/check-restore.mjs` : **restauration vérifiée dans des volumes
  neufs**. Même nombre de commandes, session ADMIN avec CSRF, lecture de la
  commande et montant figé identiques, démarrage Flyway réussi et SHA-256
  identique du JPEG MinIO. Contrôles HTTP réels via Nginx sur le réseau du clone,
  indépendamment du relais Windows de nouveaux ports.
  Commande terminée avec code zéro ; clone, volumes temporaires et auxiliaires
  supprimés, cinq services E2E sources sains. Neuf contrôles de l’environnement
  habituel relancés et passés après restauration ; ses conteneurs restent actifs.

Les premiers essais ont corrigé des attentes du test : CSRF `204`, SKU
majuscules et codes/slugs minuscules, sélecteur du formulaire catalogue et
filtre du tableau après une transition. Les API et protections d’authentification
n’ont pas été assouplies. Les fixtures administratives appellent directement
l’API locale ; les achats et opérations gestionnaire passent par Angular/Nginx.

Incident local : C: s’est saturé. Les anciens rapports générés de cette tâche
ont été nettoyés ; dépendances E2E, cache binaire Chromium et profils temporaires
ont été déplacés sur D: en conservant leurs chemins. Aucun secret, catalogue ou
volume métier n’a été supprimé. Le canal Docker Windows habituel ne répondait
plus. Le canal direct `npipe:////./pipe/docker_engine_linux` permet les contrôles
sans redémarrer le moteur. L’utilisateur a demandé de **conserver les autres
conteneurs actifs** : aucun redémarrage de Docker Desktop n’a été effectué.
Seuls les clients de vérification bloqués ont été arrêtés. Le test SMTP utilise
désormais `pause`/`unpause` sur Mailpit E2E ; son nettoyage ne reprend pas deux
fois un conteneur déjà actif. La suite complète passe sans option de réduction.

Les premiers essais de restauration ont révélé des problèmes de montage Windows,
de confirmation de démarrage et de relais de nouveaux ports. Le script transfère
les archives par copies binaires, initialise les rôles sans montage hôte et
contrôle explicitement la santé des conteneurs. Le client HTTP utilise le nom
`localhost` avec connexion interne à Nginx pour envoyer effectivement le cookie
CSRF ; une connexion de diagnostic a passé sans modifier la sécurité serveur.

| Périmètre | État constaté |
| --- | --- |
| Identité, CSRF, RBAC, propriété, confidentialité | Vérifiés par la suite backend complète ; choix marketing et opérations couverts par les tests Angular existants |
| Catalogue produits, filtres/pagination, photos/import | Tests unitaires/API réels et deux tests MinIO passés |
| Stock, commandes mixtes, idempotence/concurrence/rollback | Tests PostgreSQL passés ; achats navigateur et transitions vérifiés |
| Angular public et administration | 44 tests passés, build optimisé et parcours d’achat/gestion réels réussis |
| Email et notifications | Confirmation/statuts, panne/reprise Mailpit et événements SSE après interruption vérifiés dans la dernière suite complète ; panne/doublons/RBAC aussi testés côté backend |
| Sauvegarde/restauration PostgreSQL + MinIO | Dump personnalisé, archives objet et restauration sur volumes neufs vérifiés ; commande authentifiée et image relues ; copie hors hôte de production encore à configurer |
| Liste publique des packs | Fonctionnelle, recherche actuellement côté navigateur, sans pagination publique serveur ; amélioration encore incomplète, administration paginée côté serveur |
| Réceptions fournisseurs dédiées, retours, paiement en ligne, transporteur | Non implémentés ; ajustements positifs disponibles, paiement à la livraison seul opérationnel |
| Catalogue/tarifs/fiscalité/zones réels | Non validés par l’exploitant ; données et frais de démonstration uniquement |
| Hébergement, domaine/TLS, SMTP/S3 de production, sauvegardes hors hôte, supervision | Dépendances externes non configurées ; Compose reste local |
| Conservation légale, notice/contact et formalités Tunisie/UE | Décisions externes non validées ; aucune déclaration automatique de conformité |

Le README décrit commandes, variables, migrations V1–V12, données fictives,
procédure de sauvegarde/restauration, limites et préparation de production.
L’étape 7d locale est vérifiée ; l’étape 7 globale reste ouverte. Le workflow
doit être exécuté sur GitHub et les décisions métier, juridiques et services de
production doivent être validés avant ouverture. Les lacunes fonctionnelles
indiquées dans le tableau restent distinctes de ces dépendances externes.

## Audit final des risques de lancement — 28 septembre 2026

**Revue et corrections effectuées ; validation finale non terminée.** Le
[rapport d'audit](launch-audit.md) classe quatre défauts confirmés et décrit les
preuves, limites et paramètres d'exploitation. Aucun changement esthétique ni
refonte des modules n'a été effectué.

- P1 : révocation du rôle administrateur pendant l'attente d'un verrou. Le
  rôle est maintenant contrôlé sous le verrou pour création de compte interne,
  changement de rôles et activation. Trois régressions unitaires et trois
  courses PostgreSQL déterministes ont été ajoutées.
- P1 : le mode CSRF SPA remplaçait le dépôt de cookies personnalisé. L'ordre
  de configuration est corrigé et un test HTTP vérifie `Secure`/`SameSite` sur
  le cookie initial derrière une terminaison TLS.
- P1 : le proxy masquait l'adresse client utilisée par le limiteur, partageant
  les quotas de connexion entre visiteurs. Les pairs de confiance sont
  explicités, les en-têtes entrants écrasés et deux régressions vérifient
  l'indépendance des quotas et le refus du contournement par en-tête forgé.
- P2 : Nginx refusait les images valides de plus de 1 Mio. La route de photos
  accepte maintenant 26 Mio par lot, en conservant les limites serveur et les
  limites basses des autres routes. Les fixtures E2E passent par Nginx avec un
  vrai PNG synthétique supérieur à 1 Mio.

Résultats **de cette session** : trois échecs unitaires attendus avant la
correction d'identité, puis trois cas passés après correction ; compilation et
packaging Java atteints avant la phase d'intégration. Le rejet d'image HTTP 413
a été reproduit sur l'ancienne passerelle. Les 44 tests Angular (10 fichiers)
et le build optimisé passent ; les sources exécutées sur D: ont été comparées
au dépôt. Contrôle du dépôt, syntaxe des scripts E2E et recherche ciblée de
signatures de secrets réussis.

L'exécution PostgreSQL/Testcontainers ciblant `IdentityRevocationIT` et
`FoundationIT` est restée bloquée dans l'initialisation Docker. Après saturation
de C:, le binaire Chromium des tests a été déplacé sur D: en préservant son
chemin ; les deux interfaces Docker et les endpoints locaux ne répondaient
toutefois plus. Aucun redémarrage global ni arrêt d'un autre projet n'a été
effectué. Les résultats d'intégration et les quatre parcours navigateur réussis
de l'étape précédente **ne valident pas les dernières corrections**.

Restent donc à exécuter : compilation de la dernière correction CSRF, suite
backend complète avec PostgreSQL/MinIO, rebuild Nginx et quatre parcours E2E
enrichis, santé locale puis CI standard. Le client Docker temporaire de
diagnostic n'est ni une dépendance du dépôt ni une modification de la CI.
Le rapport précise les ressources d'audit dont le nettoyage reste à vérifier
lorsque Docker répondra. L'étape de lancement n'est pas cochée comme terminée.

Avant production : `TRUSTED_PROXY_PATTERN` et chaîne HTTPS validés, secrets et
clés sauvegardés en coffre, SMTP/S3 et PostgreSQL de production configurés,
tarifs/compositions/zones approuvés, conservation et notice décidées, sauvegardes
hors hôte/restauration et alertes disque/outbox opérationnelles. SMTP après un
accusé perdu et réconciliation d'objets privés orphelins restent des limites
documentées. Aucune conformité juridique automatique n'est déclarée.

## Incident de disponibilité localhost:4200 — 28 septembre 2026

Diagnostic demandé après l'audit : le frontend `4200`, l'API `8080`, MinIO
`9000` et Mailpit `8025` expirent tous lors des requêtes HTTP locales. Les ports
restent détenus par le processus Docker Desktop, mais `docker ps` sur le canal
Linux direct et `docker desktop status` expirent également. Les derniers
journaux du moteur montrent des délais dépassés lors du transfert des ports
vers le service Desktop. C: dispose à présent d'environ 1,1 Go libres.

L'accès WSL a été examiné en lecture seule ; il n'a pas fourni de socket du
moteur permettant de relancer seulement BricoComptoir. Le statut des conteneurs
ne peut donc pas être confirmé via l'API Docker. Aucune réparation fiable limitée
à l'application n'a pu être exécutée dans cet état.

L'utilisateur a de nouveau demandé de conserver les autres conteneurs sans
redémarrage. Aucun redémarrage Desktop/WSL, arrêt d'un autre projet, suppression
de volume ou remplacement du frontend n'a été effectué. Le rétablissement de la
boutique reste bloqué par l'indisponibilité de Docker ; les validations finales
de l'audit restent en attente.

Nouvelle tentative de récupération sans arrêt : `docker desktop start --detach
--timeout 15` réussit en signalant `Docker Desktop is already running`. Elle ne
rétablit pas le moteur : la lecture de sa version sur le canal Linux direct
expire après 20 secondes. À ce stade, le redémarrage global n'était pas
autorisé ; l'application n'était pas rétablie.

## Rétablissement local après autorisation — 28 septembre 2026

Après autorisation explicite de l'utilisateur, Docker Desktop a été redémarré.
La commande normale a expiré lors de l'arrêt ; les processus Docker vérifiés
ont été arrêtés, puis son environnement WSL fermé. Docker était l'unique
distribution WSL installée. Le premier démarrage a rencontré un disque signalé
comme déjà utilisé ; après fermeture complète de WSL, le moteur Linux
`29.6.1` a répondu de nouveau. Aucune réinitialisation, suppression de volume
ou commande manuelle de formatage n'a été effectuée. La copie de sauvegarde
du VHD envisagée n'a pas été exécutée : le moteur était revenu avant la copie.

`docker compose start --wait --wait-timeout 180` a redémarré les cinq
conteneurs existants : PostgreSQL, MinIO, Mailpit, backend et frontend, tous
`healthy`. Les volumes et les images existants ont été conservés. Le conteneur
Supabase Vector précédemment actif est revenu automatiquement ; son état
`unhealthy` préexistant n'a pas été modifié. Les autres services de ce projet,
déjà arrêtés avant l'incident, n'ont pas été démarrés.

Correction de la reprise : les cinq services de la boutique avaient une
politique `no`. `compose.yaml` utilise désormais `restart: unless-stopped` ;
cette politique a aussi été appliquée aux conteneurs existants, après contrôle
de leur label de projet, sans recréation. Un arrêt explicite reste respecté.
La commande de reprise et la vigilance disque sont documentées dans le README.

Vérifications réellement exécutées : validation Compose, inspection des cinq
politiques et contrôles de santé, `node scripts/check-local.mjs` (neuf contrôles
HTTP réussis : API directe/proxy, frontend, MinIO, Mailpit, refus `401` et CSRF
`403`), contrôle du dépôt et des espaces. L'accueil et le catalogue ont été
ouverts dans le navigateur avec les API réelles ; les deux produits de
démonstration existants s'affichent. L'accueil est laissé ouvert à `4200`.

C: est descendu sous 100 Mo libres pendant la reprise. Les caches de navigateur
inactifs `chromium-1243` et `chromium_headless_shell-1243` ont été déplacés vers
`D:\bricocomptoir-browser-runtime`, avec des jonctions préservant leurs chemins,
libérant environ 700 Mo. L'espace reste insuffisant pour lancer sereinement de
gros builds ; la gestion du stockage Docker reste à prévoir. Ce rétablissement
avec les images existantes **ne valide pas les corrections de l'audit encore
en attente de compilation et de tests d'intégration**.

## Format des prochaines entrées

Pour chaque étape : date, périmètre terminé, fichiers/contrats modifiés,
vérifications réellement exécutées et résultats, limites ou blocages, puis
prochaine étape. Cocher uniquement ce qui satisfait les critères de la roadmap.

## Accueil et hero — 29 septembre 2026

Lecture des styles existants et application de `angular-ui-ux-skill.md` local.
Hiérarchie du hero renforcée, actions et cartes packs/catalogue clarifiées,
espacements et grille responsive ajustés, état de chargement visuel et titres
sémantiques ajoutés. Rouge `--red`, neutres, typographie, focus global et
illustration des cartons conservés. Illustration décorative isolée dans
`HomeArt` (HTML/SCSS encapsulés), sans données ni interactions ; `HomePage`
conserve les signals, contrats et chargements API existants. Aucun changement
d'architecture métier, de style global ou de dépendance déclarée.

Vérifications : build Angular de production réussi sans dépassement des budgets
après extraction du composant ; 44 tests Angular passent dans 10 fichiers.
Dépendances locales réutilisées via une jonction vers le répertoire existant
sur D: ; lockfiles identiques, aucune nouvelle installation. Les refus de lecture
Windows du premier essai ont nécessité une exécution hors bac à sable.
Chromium : largeurs 320/390/768/1440 px sans débordement horizontal, navigation
vers les solutions au clavier, focus visible, erreur d'accueil et réessai validés,
aucune erreur JavaScript observée. Captures bureau/mobile relues ; hauteur de
l'illustration mobile ajustée pour séparer les cartons de leur légende.

Limite : API locale inaccessible pendant cette session. Les vérifications
visuelles utilisent exclusivement des réponses interceptées dans le navigateur
de test (textes initiaux de V9, catalogue vide), sans modifier l'application ou
la base. Elles ne valident pas le parcours connecté au backend. Aucun test
backend requis par ces modifications de présentation, aucun déploiement effectué.

## Activation locale du nouvel accueil — 29 septembre 2026

Après autorisation, redémarrage de Docker Desktop et fermeture complète de son
WSL bloqué. Le moteur 29.6.1 répond de nouveau ; les cinq services existants
BricoComptoir sont sains. Aucune suppression de volume ou réinitialisation
manuelle du stockage. Backend relancé avec son image existante ; frontend
reconstruit depuis les sources modifiées puis recréé seul sur localhost:4200.

Vérifications : build Docker Angular réussi, neuf contrôles HTTP de
`node scripts/check-local.mjs` réussis après activation, cinq conteneurs healthy.
Chromium sans interception réseau confirme `app-home-art`, le titre réel du
hero et deux cartes produits provenant du catalogue local. La limite API
indisponible de la vérification visuelle précédente est levée pour cet accueil.
Ce redémarrage ne constitue pas une validation des modifications backend non
reconstruites. Aucun déploiement distant effectué.

## Hero photographique et carrousel — 29 septembre 2026

Application du skill UI UX Pro Max demandé, lu depuis son dépôt officiel
(`nextlevelbuilder/ui-ux-pro-max-skill`) dans `.local/` : recherches ciblées
réduction des animations et signals Angular. La version locale annoncée n’a
pas été trouvée dans les dossiers de skills examinés. La charte existante et
Angular 21 sont conservés ; aucune dépendance ajoutée.

`HeroCarousel` remplace l’illustration CSS statique. Trois ambiances : outils,
quincaillerie, kits/packs. Message principal et CTA restent fixes ; défilement
à 6,5 secondes, transition de 450 ms, sélection directe, précédent/suivant,
pause/reprise, geste horizontal et flèches clavier. Pause temporaire au survol,
arrêt sur focus et navigation manuelle ; pas de rotation avec animations
réduites ou onglet masqué. Nettoyage du timer et des écouteurs à la destruction.
Les diapositives inactives sont `inert` et masquées aux lecteurs d’écran ;
annonces actives seulement hors rotation. Repli textuel si une image échoue.

Trois images générées avec l’outil imagegen intégré, six fichiers WebP
responsives dans `frontend/public/images/hero/` (47 à 255 ko par fichier).
Mention visible de leur nature illustrative/IA ; aucun pack commercial,
prix ou stock inventé. Prompts exacts et provenance : [hero-images.md](hero-images.md).
Les textes principaux de l’accueil et les produits restent issus des API.
Aucun changement d’architecture métier ou de contrat serveur.

Vérifications exécutées : build Angular natif et Docker réussis, budgets
respectés ; 49 tests passent dans 11 fichiers, dont cinq tests du carrousel
(timer/nettoyage, focus/clavier, réduction du mouvement, onglet masqué/swipe,
erreur image). Chromium avec API locale réelle : 320/390/768/1024/1440 px sans
débordement, images décodées, navigation clavier et lien packs, réduction du
mouvement et absence d’erreur JavaScript. Captures relues sur mobile et bureau.
Les premières assertions navigateur ont nécessité d’attendre le décodage et
le rendu Angular ; aucune panne applicative déduite de ces attentes prématurées.
Pas de test backend relancé pour cette modification de présentation.

Activation finale : image frontend reconstruite et conteneur seul recréé sur
localhost:4200, neuf contrôles HTTP réussis. Chromium sur 4200 confirme le
chargement des photos, le passage automatique à la deuxième diapositive,
la pause et la sélection directe. `git diff --check` réussi. Aucun déploiement distant.

## Hero pleine largeur — 29 septembre 2026

Refonte selon la capture fournie : photo en fond sur toute la largeur réelle,
texte blanc superposé avec voile de contraste, CTA rouge et lien vers tous les
packs. Première diapositive consacrée aux kits ; les deux suivantes orientent
vers quincaillerie et outils. Aucun assortiment commercial inventé. Fondu
600 ms, entrée du texte et très léger zoom photo ; réduction du mouvement,
pause, clavier, swipe et éléments inactifs inertes conservés.

Le conteneur principal enlève ses limites uniquement en présence d’`app-home`
avec un sélecteur local dans `app.scss`. Les autres sections d’accueil gardent
une largeur maximale et leurs marges responsives. Les textes administrables
(title/accent/description) sont maintenant dans l’introduction sous le hero ;
le test d’échappement HTML vise cet emplacement, conservant sa vérification.
Les contrats API et l’architecture métier ne changent pas.

Vérifications : build Angular réussi, 49 tests réussis après adaptation du
sélecteur de titre éditorial. Chromium mesure x=0 et largeur=viewport pour le
hero à 320/390/768/1440/1920 px, sans débordement horizontal. Captures mobile
et bureau relues ; navigation suivante et préférence de mouvement réduit
vérifiées. Images existantes réutilisées, mention illustrative conservée.

Activation : build Docker final sans dépassement CSS après suppression de
règles redondantes ; frontend recréé seul, neuf contrôles HTTP réussis.
Chromium sur localhost:4200 confirme la largeur complète et le lien vers
`/packs`. `git diff --check` passé ; aucun déploiement distant.

## Publication du hero sur GitHub / Vercel — 29 septembre 2026

Périmètre du commit : frontend, images du hero et documentation associée.
Les modifications préexistantes de configuration/démarrage du backend et
leurs documents restent locales, hors de cette publication de l’interface.

Validation avant commit : 49 tests Angular réussis à l’étape précédente ;
`node scripts/build-vercel.mjs` réussi sur les sources finales, budgets respectés,
artefact Build Output API v3 et images produits ; cinq tests
`vercel-output.test.mjs` réussis. Contrôles dépôt, publication/secrets/liens et
`git diff --check` réussis. Le build ne présente pas de défaut reproduit ;
la configuration Vercel existante est conservée.

Le connecteur Vercel ne donne pas accès au projet BricoComptoir (403) ;
le projet existant est relié à `origin/main`, comme confirmé lors du précédent
déploiement. Le domaine public sert Angular en HTTP 200, mais la requête de
santé API expire après 15 secondes. Ce problème distant n’est pas assimilé à
un échec de compilation. Le backend public et ses dépendances restent à vérifier.

## Correction du test panier en CI — 29 septembre 2026

Le run GitHub Actions `36504925414` réussissait les jobs backend et frontend,
mais échouait dans le parcours mobile connecté : une seule ligne de panier
était retrouvée après un rechargement immédiat suivant l'ajout du produit.
Le test déclenchait `page.goto` pendant l'enregistrement asynchrone (CSRF puis
PUT), susceptible d'être interrompu par la navigation complète.

Le parcours attend désormais la réponse réelle PUT `/api/v1/cart`, exige
HTTP 200 et deux lignes enregistrées avant de recharger. Les assertions de
persistance après rechargement, commande, stock et expédition sont conservées.
Aucun délai arbitraire, retry supplémentaire ou API simulée n'est ajouté.

Validation locale : quatre tests Playwright réussis en 59,5 secondes sur
Compose E2E isolé ; syntaxe JavaScript, contrôles dépôt et publication,
relecture du diff et `git diff --check` réussis. Cette exécution locale utilise
le backend de l'arbre de travail, qui contient des modifications préexistantes
non incluses dans ce correctif. Le pipeline distant vérifiera le commit seul.
Aucun changement d'architecture ni de code applicatif dans ce correctif.

## Visuels fournis et photos de packs — 29 septembre 2026

Le compte administrateur de production est maintenant vérifié par l'API réelle :
connexion avec CSRF, rôle ADMIN, session, lectures administratives privées,
refus anonyme/sans CSRF et révocation après déconnexion. Aucun bootstrap de
production n'a été exécuté par l'agent pour ce contrôle.

À la demande de l'exploitant, quatre fiches sont créées par les API autorisées
en production : « Kit entretien du bois — à valider », « Gants — référence à
valider », « Pinceau plat — référence à valider », « Papier abrasif — référence
à valider ». Brouillons vérifiés en 404 côté public, sans variante/SKU, prix,
stock ou quantité de composition inventés. Les descriptions identifient les
visuels générés et les caractéristiques à valider ; l'avant/après du kit est
explicitement simulé, sans preuve photographique ni promesse de résultat.

Les quatre PNG originaux (1,99 à 2,39 Mo ; 1254×1254 et 1586×992) sont préparés
dans un répertoire privé exclu de Git. L'import opérateur garde un checkpoint
d'identifiants et vérifie les fiches existantes avant de créer, sans répétition
aveugle d'un téléversement incertain. Aucun fichier fourni ni identifiant de
connexion n'est inclus dans la publication du code.

L'absence de photos de packs est corrigée : métadonnées V13, port public de
visibilité des packs, stockage partagé interchangeable, mêmes contrôles de
fichiers/rendus que les produits, administration ajout/ordre/image principale,
cartes et fiches responsives. Contrats et limites dans [pack-media.md](pack-media.md).

Vérifications locales : `mvn verify` avec les quatre suites d'intégration
`PackMediaIT,MediaCatalogIT,PacksIT,MediaMinioLocalIT` : 51 tests unitaires/ArchUnit
et 9 tests PostgreSQL/Testcontainers/MinIO réussis, aucun ignoré. Les sources
locales incluent les diagnostics de démarrage préexistants, qui restent hors
du commit photo. Les nouveaux tests couvrent RBAC/CSRF, type réel, taille,
dimensions, lot invalide, métadonnées, ordre/principale, propriété, brouillons,
retrait de publication d'un composant, rendus et rollback/nettoyage après panne.
Angular : `npm run test:ci` passe 52 tests, dont photos de packs, refus d'accès
et réponses tardives ; `BRICO_API_ORIGIN=https://brico-comptoir.onrender.com node
scripts/build-vercel.mjs` réussit, budgets respectés. Cinq tests de routes Vercel,
contrôles dépôt/publication/liens locaux et `git diff --check` réussis.

Limite de production explicite : l'exploitant configure Backblaze B2 dans Render.
Les valeurs de stockage inspectées restent des placeholders ; aucun des quatre
fichiers n'a encore été déclaré téléversé en production. Un téléversement réel
et sa lecture administrative doivent confirmer B2 après configuration. Les fiches
resteront en brouillon jusqu'à validation commerciale ; le stock du pack demeure
dérivé des composants. SMTP et lancement commercial ne sont pas vérifiés par
cette tranche.

## Activation et contrôle CI des photos de packs — 29 septembre 2026

Commit photo `293bbf2` publié sur `origin/main`. Vercel signale le déploiement
réussi ; Render `dep-dattfmjrjlhs73c4ini0` devient Live en 3 min 22 s, avec
Flyway exécuté au démarrage. L'API réelle confirme les quatre brouillons sans
variante et leurs listes d'images en HTTP 200, dont la nouvelle table de packs.
Les quatre listes sont encore vides : B2 attend la configuration de l'exploitant.
La vitrine ne doit donc pas annoncer les fichiers téléversés. Le navigateur
confirme connexion administrative, recherche du kit, brouillon et contrôles
de photos ; la connexion doit être renouvelée après le redémarrage du backend.

CI `36589776538` : frontend réussi, parcours navigateur et restauration réussis.
Le backend exécute 59 tests d'intégration ; son seul échec est le test de socle
qui attend encore 12 migrations au lieu de 13. Son assertion est mise à jour
pour V13 sans réduire la validation ni le contrôle de non-réexécution. Les
7 tests `FoundationIT` sont ensuite exécutés sur PostgreSQL/Testcontainers réel
et réussissent localement. Le correctif déclenchera une nouvelle CI complète ;
elle n'est pas présentée comme réussie avant son résultat.

## CI finale et tentative B2 — 29 septembre 2026

Le correctif `d1ac31a` est poussé sur `origin/main`. La
[CI 36590703796](https://github.com/skpato1/brico-comptoir/actions/runs/36590703796)
réussit ses trois jobs : backend (44 tests unitaires/ArchUnit et 59 tests
d'intégration, zéro échec/erreur/ignoré), frontend (52 tests Angular et cinq
tests de routage Vercel, builds réussis), navigateur (quatre parcours réels
réussis en 49,7 s). La restauration vérifie le nombre de commandes, une
commande authentifiée figée, le démarrage Flyway et une image MinIO identique.
Render confirme le commit `d1ac31a` Live ; Vercel signale son déploiement réussi.

Après l'annonce d'activation du stockage par l'exploitant, le premier
téléversement réel du PNG des gants retourne HTTP 500. Aucune répétition
aveugle n'est effectuée. Une nouvelle lecture authentifiée confirme les quatre
fiches en brouillon sans variante et zéro métadonnée d'image pour chacune ;
la lecture publique des médias du pack reste vide. Le fichier n'est donc
pas déclaré importé.

La page Environment de Render, actualisée après cet échec, affiche toujours
`MEDIA_ENDPOINT=https://A_REMPLACER_ENDPOINT_S3`. L'activation du compte B2
ne suffit pas à relier l'application : l'exploitant doit renseigner dans Render
le véritable endpoint S3, le bucket et les deux accès, puis déployer ces valeurs.
Aucun accès n'est copié dans Git. L'import et la lecture des rendus JPEG
de production restent à exécuter après cette configuration ; aucun succès B2
ni aucune photographie de résultat réel ne sont annoncés.

## Intégration Supabase Storage — 29 septembre 2026

À la demande de l'exploitant, ajout d'un adaptateur S3 compatible avec le
préfixe `/storage/v1/s3` de Supabase, via le port `ObjectStorage` existant.
`MEDIA_PROVIDER=minio` conserve le local ; `s3` active AWS SDK Java 2.55.7
avec `MEDIA_REGION`, adressage par chemin, Signature V4 et reprises bornées
à deux tentatives/60 secondes par appel. Il exige un bucket précréé et ne
modifie ni les autorisations applicatives ni les métadonnées PostgreSQL.
Configuration et limites dans [supabase-storage.md](supabase-storage.md),
avec accès S3 exclusivement côté serveur, bucket privé et sauvegarde objet
distincte. Les clés S3 Supabase couvrent tous les buckets du projet et
contournent les RLS : cette limite est explicite, un projet dédié est prévu.

Vérifications réellement exécutées sur la copie native du backend sur D: :
test ciblé `S3ObjectStorageTest` (trois tests réussis), puis `mvn -B -ntp verify`
avec `MEDIA_PROVIDER=s3`, région `us-east-1` et `BRICO_LOCAL_MINIO_TEST=true` :
BUILD SUCCESS, 54 tests unitaires/ArchUnit et 60 tests d'intégration, aucun
échec, erreur ou test ignoré. Le test HTTP vérifie signature/région, préfixe
Supabase, octets, type, absence de checksum/chunking AWS optionnels et reprises.
Les tests réels PostgreSQL/MinIO vérifient les téléversements de produits et
packs, les rendus JPEG, métadonnées, brouillons et permissions ; le nouvel
adaptateur réalise aussi un aller-retour binaire avec le MinIO existant.
L'arbre local inclut toujours les diagnostics de démarrage préexistants,
hors du commit stockage. Les 13 migrations ne changent pas. Contrôles dépôt,
publication/secrets/liens locaux et `git diff --check` réussis.

État de provisionnement : le connecteur Supabase liste « skpato1's Org »
avec deux autres projets actifs. Son devis de création est de 0 par mois ;
le choix explicite de l'organisation et la confirmation du coût restent
nécessaires avant création. Le tableau de bord est déconnecté ; l'exploitant
a indiqué qu'il allait se connecter. Aucun projet, bucket ou accès Supabase
n'est encore déclaré créé, et aucune valeur Render n'est remplacée par un
secret fictif. L'import des quatre illustrations et la lecture réellement
hébergée restent à terminer après provisionnement et déploiement. Les fiches
restent en brouillon et l'avant/après du kit reste identifié comme simulé.

Vérification du commit stockage `030a109` après publication : la
[CI 36595299634](https://github.com/skpato1/brico-comptoir/actions/runs/36595299634)
réussit ses trois jobs. Le commit exact exécute 47 tests unitaires/ArchUnit
et 60 tests d'intégration, sans échec, erreur ou test ignoré ; les sept
tests locaux supplémentaires des diagnostics préexistants restent hors
de ce commit. Le frontend réussit 52 tests Angular, cinq tests de routage
et son build de production. Les quatre parcours navigateur réussissent
en 47,4 s ; le contrôle de sauvegarde/restauration PostgreSQL et MinIO
réussit également. Ces vérifications n'utilisent pas un projet Supabase.

Render indique `030a109` Live (déploiement automatique en 3 min 49 s),
Vercel indique son déploiement réussi. Après déploiement, les lectures de
`/api/v1/health` sur Render et via le proxy Vercel retournent HTTP 200 et
`status=UP`. La santé ne vérifie pas la connexion au stockage d'images.
Le tableau de bord Supabase affiche encore la page de connexion. Le plan
Free de l'organisation et ses deux projets actifs sont confirmés : ils
occupent la limite publiée de deux projets actifs gratuits. Aucun projet
existant n'est suspendu, supprimé ou réutilisé ; aucun abonnement payant
n'est souscrit. La destination, la capacité disponible, le bucket privé,
les clés saisies dans Render et l'import réel restent à résoudre avant
de déclarer le stockage Supabase opérationnel.

## 6 octobre 2026 — Logos de marques sur l'accueil

Une section « Choisissez votre marque » présente 18 logos distincts dans six
familles, avec 19 emplacements car TOTAL figure aussi en jardinage. Les images
sont servies localement depuis `frontend/public/brand-logos/` (365 ko au total)
et chaque logo ouvre le catalogue avec le filtre `brandId` de l'API publique.
Les marques inactives ou absentes de l'API ne sont pas annoncées comme
disponibles. Les visuels de collection SQES ont été examinés ; ACEM,
DEUTSCHCOLOR, GARDENA et WADFOW utilisent des fichiers de marque distincts
parce que leurs images de collection étaient des scènes promotionnelles ou
un autre logo. NOBLEX n'a actuellement ni marque ni produit dans le catalogue
de production et reste à intégrer après création de fiches validées et
obtention de son logo exact.

`npm run test:ci --prefix frontend` : 57 tests réussis.
`npm run build --prefix frontend` : réussi. Prévisualisation locale Angular
avec l'API publique réelle : 19 emplacements affichés et leurs images chargées ;
le logo BOSCH ouvre le catalogue avec BOSCH sélectionné et 18 produits.
`node scripts/check-release.mjs` et
`node scripts/check-repository.mjs` réussissent ; `git diff --check` ne
signale aucune erreur d'espacement.

## 6 octobre 2026 — Bandeau horizontal des marques

L'accueil présente désormais les marques dans une seule rangée de logos,
défilable au toucher et avec des flèches. Des boutons filtrent les six rayons ;
« Toutes » déduplique TOTAL, présent dans deux rayons. La navigation garde les
liens vers le filtre `brandId` fourni par l'API. Les mouvements respectent la
préférence système de réduction des animations.

`npm run test:ci --prefix frontend` : 59 tests réussis, dont filtres et
défilement. `npm run build --prefix frontend` : réussi. Vérification dans le
navigateur local avec l'API de production : 18 logos chargés, filtres actifs,
flèche mobile déplaçant la rangée, aucune erreur JavaScript observée.
`node scripts/check-release.mjs` et `node scripts/check-repository.mjs`
réussissent ; `git diff --check` ne signale aucune erreur d'espacement.
Le backend n'a pas été modifié et ses tests n'ont pas été relancés.
