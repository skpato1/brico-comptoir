# Roadmap de BricoComptoir

Référence : [architecture](architecture.md). État réel : [progression](progress.md).
Chaque étape produit une tranche vérifiable ; aucune case n'est cochée sur la
seule base d'un contrat ou d'une maquette. Les étapes 2, 3a et 3b livrent
l'identité, le catalogue de produits et les packs ; voir
son état de vérification dans [progression](progress.md).

| Étape | Livrable | Critères de sortie |
| --- | --- | --- |
| 0 — Architecture | Audit du dépôt, architecture, règles agents, roadmap, suivi | État local/distant établi ; décisions, dépendances, RBAC, stock et API documentés ; aucun code boutique ajouté |
| 1 — Socle technique | Backend Java 21/Spring Boot/Maven, Angular, PostgreSQL local, Flyway, Docker Compose, MinIO/Mailpit locaux et CI | Versions compatibles figées ; builds reproductibles ; démarrage local vérifié et documenté ; migration initiale et test PostgreSQL réel ; règles ArchUnit ; santé réelle affichée dans Angular ; OpenAPI ; configuration sans secrets |
| 2 — Identité et sécurité | Inscription client, session, CSRF, profil, récupération du mot de passe, bootstrap admin et gestion des accès | Matrice RBAC appliquée côté serveur ; mots de passe hachés ; tests `401/403`, fixation/fin de session, CSRF, rôle injecté, propriété des comptes, désactivation, récupération et dernier admin ; aucune route métier ouverte par défaut |
| 3a — Catalogue de produits | Catégories hiérarchiques, marques, fiches, variantes/SKU, prix TND, publication, administration et lecture Angular | CRUD réel, versions et conflits ; catalogue public filtré/paginé ; tests unitaires et d'intégration PostgreSQL/API |
| 3a-media — Photos et import CSV | Métadonnées PostgreSQL, fichiers MinIO, rendus responsives et import validé en deux temps | Contrôles serveur et permissions ; originaux privés ; tests PostgreSQL/API et vérification MinIO local |
| 3b — Packs et offres | Packs composés de variantes, prix propre, publication et instantané des offres | Composition valide sans imbrication ; snapshot cohérent pour l'achat ; tests métier et de persistance |
| 4 — Stock | Réceptions, ajustements, soldes, journal, ports de réservation/libération/consommation | Contraintes PostgreSQL, droits admin, opérations idempotentes ; tests de concurrence/rollback ; disponibilité publique calculée. La validation des SKU partagés entre packs attend 3b. |
| 5 — Achat et commandes | Panier visiteur navigateur, panier client persisté et repris après connexion, prévisualisation, frais serveur, commande mixte produit/pack, historique client | Total recalculé ; versions confirmées ; commande et réservations atomiques ; rejeu sans double commande ; tests propriété, rupture et achat concurrent ; aucun paiement simulé |
| 6 — Traitement des commandes | Préparation, annulation, expédition et livraison dans l'administration | Transitions permises uniquement ; libération/consommation unique du stock ; course annulation/expédition testée ; commandes en attente visibles ; trace des opérations |
| 7 — Préparation à l'ouverture | Parcours E2E, configuration de production, exploitation et validation métier | Catalogue réel et tarifs validés ; zones/livraison/fiscalité validées ; politique de compte et récupération définie ; TLS/cookies/CSRF, logs, sauvegarde/restauration, migrations de mise à niveau et absence de secrets vérifiés |
| 7c — Protection des données | Préférence marketing distincte, export, rectification vérifiée, retrait et conservation configurable | Tests propriété/RBAC/CSRF, concurrence avec checkout, rollback, archives et gels, logs ; mesures et décisions Tunisie/UE dans `privacy-decisions.md` ; validation juridique et exploitation restent nécessaires avant ouverture |
| 7d — Vérification locale et CI | Suite navigateur sur Compose isolé, pipeline complet, guide d'exploitation et essai de restauration | Achats pack et SKU partagé, confirmation/administration, images, Mailpit et SSE réels ; builds et suites exécutés ; PostgreSQL et MinIO restaurés dans des volumes neufs ; résultats et dépendances de production distingués dans `progress.md` |

Ordre cible : 0 → 1 → 2 → 3a → 3a-media → 3b → 4 → 5 → 6 → 7.
Le noyau de l'étape 4 peut précéder 3b car il ne dépend que des variantes ;
le calcul de disponibilité des packs sera validé après leur implémentation.
Le frontend est livré avec chaque
tranche backend correspondante, sans écran annonçant une action non implémentée.
Les réservations de l'étape 4 sont testées via leurs ports ; leur exposition
indirecte au parcours client n'arrive qu'avec les commandes de l'étape 5.
Le panier constitue une première tranche de l'étape 5 : il estime sans réserver
et ne calcule pas de livraison. Voir [cart.md](cart.md). Le checkout invité ou
connecté et les transitions de commande utilisent [checkout.md](checkout.md).

Les cinq brouillons de démonstration retenus pour 3b, après clarification de
l'absence de liste initiale, sont **DÉMO — Fixation légère**, **DÉMO —
Assemblage simple**, **DÉMO — Petit atelier**, **DÉMO — Réassort fictif** et
**DÉMO — Découverte**. Ils utilisent uniquement les SKU fictifs
`DEMO-FIX-001` et `DEMO-OUTIL-001` du jeu local ; les variantes « Sans outils »
et « Tout compris » en modifient la composition. Noms, textes, quantités et
prix ne sont pas validés commercialement. Leur script manuel reste hors des
migrations Flyway de production : [packs-demo.sql](../infra/demo/packs-demo.sql).

À chaque étape :

1. Préciser le contrat API et les invariants de la tranche avant son code.
2. Implémenter domaine/application, puis adaptateurs et interface nécessaires.
3. Exécuter les tests adaptés : unitaires, architecture, intégration réelle,
   puis E2E lorsque le parcours complet existe.
4. Relire le diff, vérifier l'absence de secrets et exécuter `git diff --check`
   avant tout commit. Documenter ce qui n'a pas pu être vérifié.
5. Mettre à jour `progress.md` avec preuves, limites et prochaine étape ;
   réviser `architecture.md` si une décision change.

Une tranche complémentaire de l'étape 6 livre les emails transactionnels via
outbox et les notifications SSE des nouvelles commandes ; tests et exploitation
dans [notifications.md](notifications.md).

Extensions après validation du premier périmètre : intégration SMTP de production, paiement en ligne, intégration
transporteur, retours, promotions, panier visiteur partagé entre appareils, quantités fractionnaires
et plusieurs dépôts. Elles ne sont ni requises ni prétendues opérationnelles
par cette étape d'architecture.
