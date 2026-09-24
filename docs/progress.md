# Progression de BricoComptoir

Dernière mise à jour : 24 septembre 2026.

## État des étapes

- [x] 0 — Vérifier le dépôt et documenter l'architecture.
- [x] 1 — Installer le socle technique et la CI.
- [ ] 2 — Implémenter identité, authentification et autorisations.
- [ ] 3 — Implémenter catalogue et packs.
- [ ] 4 — Implémenter stock et réservations.
- [ ] 5 — Implémenter achat et commandes.
- [ ] 6 — Implémenter traitement des commandes.
- [ ] 7 — Vérifier le produit et préparer l'ouverture.

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

## Format des prochaines entrées

Pour chaque étape : date, périmètre terminé, fichiers/contrats modifiés,
vérifications réellement exécutées et résultats, limites ou blocages, puis
prochaine étape. Cocher uniquement ce qui satisfait les critères de la roadmap.
