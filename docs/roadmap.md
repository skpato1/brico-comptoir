# Roadmap de BricoComptoir

Référence : [architecture](architecture.md). État réel : [progression](progress.md).
Chaque étape produit une tranche vérifiable ; aucune case n'est cochée sur la
seule base d'un contrat ou d'une maquette. Le périmètre actuel s'arrête à l'étape 1.

| Étape | Livrable | Critères de sortie |
| --- | --- | --- |
| 0 — Architecture | Audit du dépôt, architecture, règles agents, roadmap, suivi | État local/distant établi ; décisions, dépendances, RBAC, stock et API documentés ; aucun code boutique ajouté |
| 1 — Socle technique | Backend Java 21/Spring Boot/Maven, Angular, PostgreSQL local, Flyway, Docker Compose, MinIO/Mailpit locaux et CI | Versions compatibles figées ; builds reproductibles ; démarrage local vérifié et documenté ; migration initiale et test PostgreSQL réel ; règles ArchUnit ; santé réelle affichée dans Angular ; OpenAPI ; configuration sans secrets |
| 2 — Identité et sécurité | Inscription client, session, CSRF, profil, bootstrap admin et gestion des accès | Matrice RBAC appliquée côté serveur ; mots de passe hachés ; tests `401/403`, fixation/fin de session, CSRF, rôle injecté, désactivation et dernier admin ; aucune route métier ouverte par défaut |
| 3 — Catalogue et packs | Produits, catégories, packs, prix TND, publication, administration et lecture Angular | CRUD réel, versions et conflits ; composition valide sans imbrication ; catalogue public filtré ; instantané des offres cohérent ; tests unitaires et de persistance |
| 4 — Stock | Réceptions, ajustements, soldes, journal, ports de réservation/libération/consommation | Contraintes PostgreSQL, droits admin, opérations idempotentes ; tests de concurrence/rollback et produits partagés entre packs ; disponibilité publique calculée |
| 5 — Achat et commandes | Panier navigateur, prévisualisation, frais serveur, commande mixte produit/pack, historique client | Total recalculé ; versions confirmées ; commande et réservations atomiques ; rejeu sans double commande ; tests propriété, rupture et achat concurrent ; aucun paiement simulé |
| 6 — Traitement des commandes | Préparation, annulation, expédition et livraison dans l'administration | Transitions permises uniquement ; libération/consommation unique du stock ; course annulation/expédition testée ; commandes en attente visibles ; trace des opérations |
| 7 — Préparation à l'ouverture | Parcours E2E, configuration de production, exploitation et validation métier | Catalogue réel et tarifs validés ; zones/livraison/fiscalité validées ; politique de compte et récupération définie ; TLS/cookies/CSRF, logs, sauvegarde/restauration, migrations de mise à niveau et absence de secrets vérifiés |

Ordre : 0 → 1 → 2 → 3 → 4 → 5 → 6 → 7. Le frontend est livré avec chaque
tranche backend correspondante, sans écran annonçant une action non implémentée.
Les réservations de l'étape 4 sont testées via leurs ports ; leur exposition
indirecte au parcours client n'arrive qu'avec les commandes de l'étape 5.

À chaque étape :

1. Préciser le contrat API et les invariants de la tranche avant son code.
2. Implémenter domaine/application, puis adaptateurs et interface nécessaires.
3. Exécuter les tests adaptés : unitaires, architecture, intégration réelle,
   puis E2E lorsque le parcours complet existe.
4. Relire le diff, vérifier l'absence de secrets et exécuter `git diff --check`
   avant tout commit. Documenter ce qui n'a pas pu être vérifié.
5. Mettre à jour `progress.md` avec preuves, limites et prochaine étape ;
   réviser `architecture.md` si une décision change.

Extensions après validation du premier périmètre : récupération de mot de passe
automatisée et emails avec fournisseur réel, paiement en ligne, intégration
transporteur, retours, promotions, panier synchronisé, quantités fractionnaires
et plusieurs dépôts. Elles ne sont ni requises ni prétendues opérationnelles
par cette étape d'architecture.
