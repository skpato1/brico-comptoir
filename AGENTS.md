# Règles durables — BricoComptoir

- Lire `docs/architecture.md`, `docs/roadmap.md` et `docs/progress.md` avant de modifier le projet. Respecter l'étape demandée et documenter les changements d'architecture.
- Conserver Java 21, Spring Boot, Spring Security, Angular, PostgreSQL et Flyway, dans un monolithe modulaire hexagonal.
- Garder le domaine en Java pur, sans Spring, JPA, HTTP ou dépendance frontend. L'application orchestre les ports ; les adaptateurs portent la persistance, la sécurité technique et les transactions.
- Passer entre modules par leurs contrats publics, jamais par leurs entités, repositories ou tables. Ne pas créer de dépendance circulaire.
- Appliquer côté serveur les autorisations, la propriété des ressources, les validations, les prix et les invariants de stock. Les contrôles Angular améliorent l'interface mais ne constituent pas une protection.
- Ajouter des tests unitaires pour les règles métier modifiées et des tests d'intégration PostgreSQL/Spring Security pour les comportements concernés ; tester les concurrences de stock et les accès interdits quand ils sont touchés.
- Ne pas présenter de données, paiements, livraisons ou fonctionnalités fictifs comme opérationnels. Réserver les doubles et jeux de données aux tests ou à une démonstration explicitement identifiée. Ne jamais committer de secret.
- Avant tout commit : relire le diff, exécuter les vérifications adaptées aux fichiers modifiés et `git diff --check`, puis consigner les résultats et limites dans `docs/progress.md`. Ne pas annoncer un test non exécuté comme réussi.
