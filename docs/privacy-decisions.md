# Protection des données — décisions et exploitation

28 septembre 2026. Périmètre technique implémenté pour un lancement en Tunisie.
Ce document ne constitue ni une politique juridique publiée ni une certification
de conformité au RGPD. L’ouverture commerciale reste soumise aux décisions et
vérifications indiquées ci-dessous.

État de reprise : les mesures ci-dessous correspondent aux modules, migrations
V11/V12 et écrans livrés, examinés lors de la préparation de publication. Les
résultats effectivement exécutés et les limites sont dans
[progress.md](progress.md). Les accès sont détaillés dans
[security.md](security.md), et les variables, sauvegardes et responsabilités
d'exploitation dans [deployment.md](deployment.md).

## Mesures effectivement implémentées

Le module `privacy` orchestre les ports publics `PersonalIdentity`,
`PersonalSales` et `PersonalNotifications`. Il ne lit pas les tables des autres
modules. Les règles et la politique de conservation restent en Java pur ;
les transactions, PostgreSQL, chiffrement, HTTP et Spring Security sont dans
les adaptateurs. Flyway V11/V12 sépare les coordonnées des instantanés commerciaux
existants et corrèle les emails transactionnels à un périmètre opaque.

### Collecte, consentement et exposition

- Le compte demande seulement e-mail et mot de passe. La commande demande nom du
  destinataire, téléphone tunisien et adresse de livraison ; l’e-mail invité est
  facultatif. Aucun identifiant national, date de naissance, sexe ou donnée
  bancaire n’est collecté. Les mots de passe utilisent le hachage existant Argon2id.
- La commande, le suivi par email et la récupération de mot de passe ne demandent
  jamais le consentement marketing. Une propriété `marketing` ajoutée à ces
  requêtes n’accorde aucun consentement. Absence de préférence = `false`.
- `/mes-donnees` propose une case marketing initialement décochée, avec action
  d’enregistrement distincte. L’utilisateur connecté peut retirer son choix sans
  annuler ses commandes ou ses messages transactionnels. Une nouvelle acceptation
  exige la version courante de la notice ; le retrait reste possible avec une
  ancienne version. Chaque choix est horodaté avec version de notice et UUID du
  compte, sans adresse IP ni empreinte du navigateur. Aucun moteur de campagne
  marketing n’est livré : la préférence est enregistrée, pas une promesse d’envoi.
- Panier visiteur : références/quantités uniquement en stockage local. Les
  coordonnées et mots de passe de ces écrans ne sont pas persistés dans le
  navigateur. Les saisies sensibles de la page de données sont effacées après
  utilisation et à sa destruction. Aucun traceur publicitaire ou outil d’analytics
  n’est installé dans l’application.
- Les listes administratives de commandes ne transmettent plus le destinataire,
  le téléphone, la rue ou l’e-mail. Les coordonnées utiles se consultent en ouvrant
  une fiche, avec `ORDER_MANAGER` ou `ADMIN`. `CATALOG_MANAGER` n’y accède pas.
  Les SSE ne contiennent que référence, type et date. Les originaux photo restent
  privés ; les listes ne chargent que les dérivés.
- Les réponses de données personnelles utilisent `Cache-Control: no-store`.
  Session HttpOnly, Secure en production, SameSite=Lax, CSRF sur les mutations,
  propriété serveur et révocation des sessions par version restent appliqués.
  Les liens de vérification email transportent leur jeton dans un fragment, retiré
  de la barre d’adresse par Angular. `Referrer-Policy: no-referrer` est ajouté au
  proxy local.

### Consultation, rectification, export et retrait

| Opération | Contrat | Protection et limites |
| --- | --- | --- |
| Profil et préférence | `GET /api/v1/privacy/me` | Compte actif courant, tous rôles ; aucun identifiant cible fourni par le client |
| Historique des choix/opérations | `GET /api/v1/privacy/history?page=0` | Périmètre courant, pages de 100, codes/dates/version seulement |
| Choix marketing | `PUT /api/v1/privacy/consent` `{marketing,noticeVersion}` | Session, CSRF, booléen explicite, notice `2026-09-28` |
| Export | `POST /api/v1/privacy/export?page=0` `{password}` | Réauthentification ; JSON version 1 : profil/préférence, commandes, panier, actions ; pages de 100 et `hasMore` |
| Rectifier l’e-mail | `POST /api/v1/privacy/email/request` `{password,email}` puis `/confirm` `{token}` | Réauthentification, vérification de la nouvelle boîte, jeton aléatoire 256 bits haché en base, 30 minutes, une utilisation, compte courant uniquement |
| Rectifier une livraison | `PATCH /api/v1/privacy/orders/{id}/address` | Propriétaire uniquement, avant préparation (`CONFIRMED`), validation tunisienne, même gouvernorat ; e-mail et données commerciales inchangés |
| Retrait/fermeture | `POST /api/v1/privacy/anonymize` `{password,confirm:true}` | Réauthentification, confirmation explicite, transaction intermodules ; dernier ADMIN protégé |
| Invité | `/api/v1/privacy/guest/export`, `/guest/orders/{id}/address`, `/guest/anonymize` `{confirm:true}` | CSRF et périmètre invité stocké en session serveur ; aucun email, UUID de compte ou scope fourni dans le corps ne vaut preuve de propriété |
| Conservation exceptionnelle | `PUT /api/v1/admin/privacy/orders/{id}/hold` `{held,reasonCode}` | ADMIN actif vérifié ; codes `LEGAL_OBLIGATION`, `DISPUTE`, `HOLD_RELEASED`, trace du dossier commande sans texte personnel |
| Traitement de conservation | `POST /api/v1/admin/privacy/retention` | ADMIN, CSRF, paramètres serveur, aucune date limite arbitraire dans le corps |
| Accès aux coordonnées archivées | `POST /api/v1/admin/privacy/orders/{id}/archive-export` `{password,caseReference}` | ADMIN actif, réauthentification, référence `CASE-…`, export ciblé, audit transactionnel ; procédure de vérification du demandeur obligatoire |

Angular assemble les pages d’export dans un fichier JSON local. Les hachages de
mots de passe, jetons, clés, contenus chiffrés, données d’autres comptes et scopes
de session ne figurent jamais dans cet export. L’export ordinaire couvre les
données opérationnelles ; un dossier de droits vérifié utilise l’accès réservé
aux archives pour compléter la copie, y compris après fermeture du compte.
Pour un invité ayant perdu sa session, une référence de commande ou un e-mail
seul ne suffit pas : l’exploitant vérifie la demande par un canal convenu, ouvre
un dossier, puis emploie l’accès réservé. Ne pas ajouter de recherche publique
de commandes par adresse e-mail. Aucun justificatif d’identité n’est collecté
automatiquement par le site.

La modification d’e-mail annule les réinitialisations en attente et révoque toutes
les anciennes sessions ; la nouvelle adresse ne remplace pas automatiquement
l’adresse historique d’une commande. Les changements de zone, corrections après
préparation et mentions historiques devant être conservées nécessitent le
traitement d’un dossier par l’exploitant, sans réécrire les prix ou les lignes.

Le retrait refuse les commandes `CONFIRMED`, `PREPARING` ou `SHIPPED`, avec erreur
compréhensible. Les commandes `DELIVERED`/`CANCELLED` quittent ensuite les écrans
avec leurs coordonnées usuelles : copie AES-256-GCM dans une archive dédiée,
suppression du contact opérationnel, suppression du panier et des jetons,
annulation des emails en attente, préférence marketing retirée, adresse du compte
remplacée par une valeur opaque, mot de passe inutilisable et compte désactivé.
Les lignes, montants, réservations et journaux de stock restent intacts.

Un verrou de compte sérialise fermeture et nouveaux achats/paniers. Les scopes
invités utilisent un verrou transactionnel PostgreSQL commun au checkout et au
retrait, avec une clôture persistée empêchant un checkout déjà parti de recréer
des données. Les commandes sont verrouillées dans l’ordre UUID. Tout échec
annule l’ensemble de l’opération.

**Le retrait n’est pas une anonymisation irréversible de toutes les archives.**
Les coordonnées chiffrées, UUID et références conservés pour une obligation ou
un litige restent des données personnelles ou pseudonymisées. Le terme du chemin
API `anonymize` désigne le retrait des données usuelles et la fermeture. L’archive
n’est supprimée qu’après échéance et en l’absence de gel. L’application ne reçoit
pas de droit SQL de lecture directe sur la table d’archives : fonctions ciblées,
chemin HTTP réservé et audité. Le propriétaire du schéma, les sauvegardes et les
clés exigent aussi des contrôles d’exploitation.

### Conservation et logs

| Paramètre | Défaut technique | Effet livré |
| --- | --- | --- |
| `DATA_ARCHIVE_KEY` | Aucun | Clé AES de 32 octets Base64 obligatoire, indépendante de `MAIL_OUTBOX_KEY`, générée localement hors Git |
| `PRIVACY_LEGAL_DAYS` | `0` | Durée des coordonnées archivées depuis création de commande ; `0` bloque l’archivage tant que l’exploitant ne décide pas de la durée |
| `PRIVACY_RETENTION_ENABLED` | `false` | Activation explicite ; exige durée légale positive ; exécution horaire et déclenchement ADMIN |
| `PRIVACY_CONTACT_DAYS` | `90` | Depuis fin/dernière transition d’une commande terminée, retrait des coordonnées opérationnelles vers l’archive ; lots de 100 |
| `PRIVACY_CART_DAYS` | `30` | Suppression des paniers clients sans modification depuis cette durée, avec lignes/fusions en cascade |
| `PRIVACY_MAIL_DAYS` | `30` | Suppression des lignes d’outbox anciennes, y compris payload des échecs, sauf bail d’envoi encore actif |
| `PRIVACY_AUDIT_DAYS` | `365` | Purge des traces de choix et opérations de confidentialité |

Ces défauts sont des arbitrages techniques, **pas des durées légales tunisiennes**.
Valider le point de départ de conservation (ici création de commande), les
pièces vraiment nécessaires et la durée avant activation. Les gels conservent
l’archive au-delà de l’échéance et demandent une revue régulière. La fonction de
purge refuse implicitement une date située après l’horloge PostgreSQL.
Les jetons de récupération et de rectification expirent en 30 minutes ; leur
nettoyage physique horaire fonctionne même avec la conservation générale
désactivée. Une indisponibilité de base diffère le nettoyage ; elle ne prolonge
pas la validité logique du jeton.

Les comptes actifs restent nécessaires au service jusqu’à fermeture. Pas de
suppression automatique d’un compte actif basée sur une date de création qui
pourrait ignorer son utilisation ; l’exploitant doit définir et traiter sa revue
des comptes dormants. Les instantanés commerciaux sans coordonnées, références
SSE, mouvements de stock et traces de gestion des accès restent conservés pour
traçabilité ; l’exploitant doit définir leur archivage et leur nécessité résiduelle.

L’encodeur Logback autorise exclusivement des codes opérationnels statiques
majuscules ; messages libres, arguments SQL/HTTP et messages/piles d’exception
sont remplacés par `EVENT_DETAILS_REDACTED`/`EXCEPTION_PRESENT`. Date, niveau et
nom de logger restent disponibles. Les erreurs API de persistance et JSON sont
génériques. Les logs d’accès Nginx sont désactivés et ses logs d’erreur ne sont
pas conservés ; pas d’IP, URI, paramètre, cookie ou corps dans ces logs. Les
références d’acteurs de stock/contenu/livraison remplacent l’e-mail par l’UUID ;
la migration remplace les anciennes valeurs email par `legacy-staff`.
Le contrôle anti-brute-force conserve temporairement les IP en mémoire pour
ses fenêtres au plus d’une heure, avec nettoyage aux appels et limite de taille.

Arbitrage : moins de détail de diagnostic, mais aucune libre sérialisation de
données personnelles. Ne pas remplacer l’encodeur ou activer un access log brut
en production. Les métriques de santé et les codes d’erreur permettent le suivi ;
un incident doit être reproduit avec données de test pour obtenir davantage de
détail. Les exports eux-mêmes ne sont jamais journalisés par le backend.

## Décisions à renseigner par l’exploitant avant ouverture

- Identité/adresse/contact du responsable, canal pour les demandes de droits,
  notice publique complète, finalités, destinataires, bases/conditions applicables
  et procédure pour les destinataires de cadeaux ou autres personnes concernées.
  `/mes-donnees` explique les fonctionnalités mais ne remplace pas cette notice.
- Durées et points de départ justifiés, politique des comptes dormants, revue
  des gels, supervision des échecs `RETENTION_FAILED` et calendrier de purge.
  Désactivation par défaut ne doit pas devenir une conservation sans décision.
- Délai de traitement des dossiers de droits, vérification proportionnée du
  demandeur, personnel autorisé, trace de remise et rectifications à transmettre
  aux destinataires. Une extraction d’archive nécessite une référence de dossier
  sans nom, email ou téléphone dans `caseReference`.
- Secrets distincts en gestionnaire de secrets, accès minimum aux rôles SQL,
  sauvegarde des clés et procédure de rotation/rechiffrement. Aucune rotation
  automatique ni chiffrement complet des volumes de production n’est livré.
- TLS, chiffrement des disques/sauvegardes, accès aux exports, rétention/rotation
  des logs de l’hébergeur, supports SMTP, supervision et réponse aux incidents.
  Les suppressions ne modifient pas les copies déjà présentes dans une sauvegarde :
  fixer son échéance et rejouer les retraits avant remise en service après restauration.
- Le SMTP ne permet pas de rappeler un message déjà remis, ni un envoi en cours
  entre la dernière vérification du bail et sa remise. Contrôler également la
  suppression chez le prestataire, dans les boîtes et les outils de support.

## Prestataires et hébergement

En développement : PostgreSQL, MinIO privé et Mailpit dans Docker local, volumes
sur la machine de développement, accès liés à `127.0.0.1`. Mailpit est une boîte
de test persistante, avec plafond de messages : nettoyer les messages de test et
éviter d’y utiliser de vraies données personnelles. Il ne constitue pas un
prestataire SMTP de production. Les photos originales sont privées, les dérivés
réencodés ; l’exploitant doit vérifier les photos et métadonnées originales avant
publication et gérer leur suppression avec les objets.

Le frontend public est hébergé sur Vercel et le backend/PostgreSQL sur Render.
Supabase Storage est retenu pour les photos dans cette tranche ; le projet,
sa région, les accès et le transfert réel doivent encore être confirmés dans
[progress.md](progress.md). Il est destiné à recevoir les fichiers originaux et dérivés ainsi
que ses métadonnées techniques, sans transfert des comptes ou commandes vers
Supabase Auth/Data API. Aucune suppression du stockage antérieur n'est déduite
du seul changement de configuration.

Le fournisseur SMTP commercial, le transporteur et les outils de support ne
sont pas validés par cette intégration. Renseigner pour chaque prestataire,
y compris Vercel/Render/Supabase : société/contact, rôle, finalités, données transmises, pays de stockage et
d’accès à distance, sous-traitants ultérieurs, durée, garanties contractuelles,
sécurité, export/suppression et procédure d’incident. Le stockage interchangeable
et le port `EmailProvider` facilitent un choix validé ; ils ne valident pas ce choix.
Ne pas envoyer les archives ou clés à un service de journalisation externe.

## Points juridiques à vérifier — Tunisie d’abord

Faire confirmer les textes et formalités en vigueur, le champ de la loi organique
2004-63, l’information des personnes, les conditions du consentement et ses
exceptions, ainsi que l’exercice de leurs droits. Vérifier la déclaration préalable
et les éventuelles autorisations INPDP pour chaque traitement, particulièrement
l’hébergement ou les accès depuis l’étranger. La page officielle distingue les
déclarations des autorisations. [Formulaires INPDP](https://www.inpdp.tn/Formulaires.html).

Faire valider les transferts internationaux (notamment les articles 51/52), la
prospection électronique, la preuve du choix marketing, les données d’un
destinataire différent de l’acheteur et les règles relatives aux mineurs. Déterminer
la forme du consentement exigée et la valeur de sa preuve électronique : une
case horodatée n’est pas validée juridiquement par ce code. Déterminer
les pièces et durées comptables/fiscales, les délais de litige et les besoins de
livraison, sans appliquer une durée européenne par défaut. Le code ne qualifie pas
juridiquement chaque finalité. [Texte officiel de la loi publié par le ministère](https://www.mtc.gov.tn/fileadmin/texte_juridiques/L2004-0063.pdf),
[ressources INPDP](https://www.inpdp.tn/Ressources.html).

## Éventuelle clientèle dans l’Union européenne

Faire analyser l’applicabilité territoriale de l’article 3 (établissement,
offre ciblant des personnes dans l’UE ou suivi), les bases distinctes de l’article
6 et les conditions du consentement. Vérifier information/droits, opposition
marketing, exceptions à l’effacement, contrats de sous-traitance, sécurité,
notification d’incident et transferts vers les pays tiers. Examiner selon le
périmètre le représentant dans l’UE, le registre, une analyse d’impact ou un DPO,
ainsi que les règles nationales de prospection et cookies. La présence d’un
visiteur européen ne suffit pas, à elle seule, à conclure automatiquement sur
toutes ces obligations. Référence primaire : [règlement UE 2016/679, notamment
articles 3, 5–7, 12–22, 27–35 et 44–49](https://eur-lex.europa.eu/eli/reg/2016/679).

Les protections livrées constituent un socle technique vérifiable. L’existence
d’un export, d’une case marketing ou d’une archive chiffrée ne vaut pas conformité
globale ni validation de la mise en production.

Contrôle des références du 28 septembre 2026 : le PDF ministériel est accessible.
Les deux pages INPDP ont expiré ou retourné une erreur lors de cette vérification ;
EUR-Lex a répondu avec un contrôle d'accès, sans lecture complète du texte.
Ces liens restent des références officielles à revérifier, pas des formalités
juridiques réputées accomplies. Faire confirmer les textes à jour et les démarches
par l'exploitant et son conseil avant ouverture.
