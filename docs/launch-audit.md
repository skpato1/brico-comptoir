# Audit de lancement — 28 septembre 2026

**Validation technique locale achevée lors de la préparation de publication.**
Les quatre corrections ci-dessous ont été compilées et leurs régressions passent :
44 tests unitaires backend, 55 intégrations PostgreSQL/MinIO, 44 tests Angular
et quatre parcours navigateur. La restauration complète est également vérifiée.
Les commandes et limites actualisées figurent dans [progress.md](progress.md).
La chaîne HTTPS et les paramètres externes de production restent à valider.

## Défauts classés et corrections

| Gravité | Défaut constaté | Correction et régression |
| --- | --- | --- |
| P1 — élevée | Une écriture d'administration vérifiait le rôle avant d'attendre le verrou. Une révocation effectuée pendant cette attente n'empêchait donc pas la mutation, notamment une réattribution de rôle. La création de compte interne ne prenait pas ce verrou. | Acquisition du verrou d'administration avant le contrôle de l'acteur pour création, rôles et activation. Trois cas unitaires reproduisent le défaut ; `IdentityRevocationIT` ajoute trois courses déterministes avec le véritable verrou PostgreSQL. |
| P1 — élevée | `csrfTokenRepository(csrf).spa()` remplaçait le dépôt personnalisé par celui créé par `spa()`. Les cookies initiaux pouvaient perdre `Secure` derrière une terminaison TLS et `SameSite=Lax`. L'ordre réel a été confirmé dans le bytecode Spring Security 7.1.1 installé. | `spa().csrfTokenRepository(csrf)`. `FoundationIT` vérifie le véritable en-tête du cookie avec `Secure` et `SameSite=Lax`, y compris sur la liaison HTTP située derrière TLS. Le cookie CSRF reste lisible par Angular ; le cookie de session reste HttpOnly. |
| P1 — élevée | Le limiteur utilisait l'adresse de Nginx, commune à tous ses clients : dix tentatives pouvaient bloquer toutes les connexions passant par cette passerelle pendant quinze minutes. | Traitement natif des en-têtes uniquement depuis des pairs de confiance ; Nginx écrase les en-têtes fournis par le client. `FoundationIT` vérifie deux budgets clients distincts ; le scénario E2E de permissions teste qu'un `X-Forwarded-For` forgé ne contourne pas la limite. `TRUSTED_PROXY_PATTERN` est à configurer en production. |
| P2 — modérée | La limite Nginx par défaut rejetait à 1 Mio les images pourtant valides selon l'API (6 Mio par fichier). | Limite de 26 Mio uniquement sur la route des photos, sans relever celle des autres API. Les fixtures E2E téléversent désormais via Nginx un vrai PNG synthétique supérieur à 1 Mio. L'ancien comportement a été reproduit : HTTP 413 au lieu de 201. |

## Parcours examinés

- Pack → panier → devis → commande : résolution serveur des offres publiées,
  versions et empreinte du devis, cumul des SKU partagés, montants décimaux,
  instantané et frais recalculés.
- Stock : réservation multi-SKU dans la transaction de commande, verrous dans
  un ordre stable, reprise des conflits PostgreSQL, annulation/libération et
  expédition/conversion protégées par l'état de la commande et de la réservation.
- Idempotence : clé limitée au propriétaire, empreinte contrôlée, reçu verrouillé
  et conservé dans la même transaction que commande, stock et outbox.
- RBAC et données personnelles : rôles attribués côté serveur, accès propriétaire
  ou gestionnaire, invalidation des sessions, exports et rectifications bornés,
  archives chiffrées réservées à l'administration, consentement distinct.
- Images : décodage réel JPEG/PNG, limites avant décodage complet, dimensions,
  dérivés réencodés, originaux privés et contrôle de publication à la lecture.
- Emails/SSE : outbox transactionnelle chiffrée, clés de déduplication, baux,
  reprises bornées, événements durables sans coordonnées et révocation du flux.
- Secrets : modèles vides et variables externes ; clés factices limitées au
  profil de test. Aucun motif de clé privée, jeton GitHub ou clé AWS détecté
  dans les fichiers suivis et non ignorés. Ce contrôle ciblé ne certifie pas
  l'absence de tout secret ni l'historique Git complet.

Aucun autre défaut bloquant n'a été établi par cette revue de code. Les tests
existants `OrderIT`, `InventoryIT`, `PrivacyIT`, `MediaCatalogIT`, `IdentityIT`
et `NotificationsIT` couvrent déjà les courses de stock, rollback, doublons,
propriété, permissions, fichiers rejetés et panne/reprise. Ils ont été relancés
avec succès lors de la préparation de publication, y compris les trois courses
de révocation, les attributs CSRF et les budgets réseau. Le PNG synthétique
supérieur à 1 Mio est accepté par la passerelle réelle.

## Journal historique : premier audit interrompu

Les résultats suivants décrivent l'exécution initiale avant le rétablissement
de Docker. Ils sont conservés pour traçabilité et ne constituent pas le bilan
de publication indiqué en tête de ce document.

| Vérification de cette session | Résultat |
| --- | --- |
| `IdentityAdministrationTest` avant correction | 3 échecs attendus, défaut reproduit |
| Même test après correction, via Maven `verify` ciblé | 3 cas passés ; Maven a ensuite atteint la phase d'intégration |
| Compilation Java et packaging pendant cette exécution ciblée | Réussis avant le blocage d'intégration ; la dernière modification de l'ordre `spa()` reste à recompiler |
| `IdentityRevocationIT,FoundationIT` via PostgreSQL Testcontainers | Tentés, bloqués au démarrage Docker ; aucun succès d'intégration revendiqué |
| Téléversement PNG >1 Mio via la vraie passerelle E2E avant correction | HTTP 413 reproduit ; nouvelle exécution après correction encore requise |
| Angular `npm run test:ci` | 44 tests, 10 fichiers, tous passés |
| Angular `npm run build` | Réussi ; 64 fichiers source et manifestes comparés à la copie de vérification sur D: |
| `node scripts/check-repository.mjs`, syntaxe des scripts E2E | Réussis ; migrations V1–V12, modèles sans secrets, fichiers générés ignorés et espaces vérifiés |
| `node scripts/check-local.mjs` en fin de session | Échec par expiration du délai HTTP ; Docker ne répond plus sur ses deux interfaces Windows |

Le refus utilisateur de redémarrer Docker a été respecté. Aucun autre projet
n'a été arrêté. Le binaire Chromium 1234 des tests a été déplacé vers D: avec
une jonction préservant son chemin, libérant environ 410 Mio. Un essai de relais
réseau Docker a été refusé par le contrôle automatique et n'a pas été exécuté.
Une suppression forcée de conteneurs/volumes a également été refusée faute de
preuve préalable suffisante sur leurs identifiants ; elle n'a pas été exécutée.
L'inspection suivante a identifié les ressources Testcontainers ; les arrêts du
seul conteneur Maven d'audit ont déclenché une partie de leur nettoyage normal.
Un essai de client Java local bornant les accusés, sans port réseau ajouté,
n'a pas permis d'achever les tests avant le blocage général. Ces diagnostics
restent dans `.local/`, hors sources et hors CI.

La nouvelle vérification a utilisé le client Docker standard et Ryuk, sans
diagnostic personnalisé ni suppression de volumes existants. Commandes de
reproduction, avec un Docker sain :

```sh
BRICO_LOCAL_MINIO_TEST=true docker compose --profile tools run --rm backend-test -B -ntp verify
node scripts/e2e.mjs up
node scripts/e2e.mjs test
node scripts/check-local.mjs
node scripts/check-repository.mjs
```

Sous PowerShell, définir `BRICO_LOCAL_MINIO_TEST` avec `$env:`. Le lancement E2E
reconstruit les images ; ne pas tester les nouvelles sources contre les anciennes
images. Conserver les rapports Surefire/Failsafe et Playwright. Le pipeline CI
standard doit également passer ; aucun résultat GitHub n'est revendiqué ici.

## Risques et paramètres avant ouverture

- **Avant ouverture :** valider la chaîne HTTPS/proxy de production, les cookies, deux adresses clients
  distinctes et le rejet des en-têtes forgés. Le Compose fourni reste local.
- Configurer `TRUSTED_PROXY_PATTERN`, profil `prod`, domaine et
  `APP_PUBLIC_URL`, accès réseau privé du backend, PostgreSQL et rôles séparés,
  compte S3 restreint/bucket privé, SMTP réel avec TLS et délivrabilité testée.
  Garder les clés `MAIL_OUTBOX_KEY` et `DATA_ARCHIVE_KEY` dans un coffre avec
  sauvegarde et procédure de rotation. Aucune valeur réelle n'est intégrée au code.
- Valider catalogue, compositions, prix/fiscalité et frais/zones de livraison,
  procédure de création du premier administrateur de production, conservation,
  notice/contact et décisions juridiques Tunisie/UE. Pas de conformité automatique.
- Limites persistantes : un accusé SMTP perdu peut produire un doublon malgré
  la déduplication interne ; un échec au commit PostgreSQL après écriture S3 peut
  laisser des objets privés orphelins (réconciliation à prévoir). Le limiteur
  mémoire doit être partagé si plusieurs instances sont utilisées.
- Exploitation : alertes disque, outbox en échec, disponibilité S3/SMTP,
  sauvegardes chiffrées hors hôte et restauration répétée. La pagination publique
  des packs reste une limitation connue, distincte de ces corrections.

Référence de la frontière de confiance des en-têtes :
[RemoteIpValve, source Apache Tomcat](https://github.com/apache/tomcat/blob/11.0.x/java/org/apache/catalina/valves/RemoteIpValve.java).
