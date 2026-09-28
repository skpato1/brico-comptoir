# Identité et accès — étape 2

Le module `identity` possède les comptes, rôles, empreintes de mot de passe et
jetons de récupération. Son domaine et son application sont en Java pur. Les
adaptateurs JDBC, SMTP, HTTP et Spring Security restent à sa périphérie. La
migration Flyway V2 crée les tables et l'audit des changements d'accès.

## Permissions

Les rôles sont cumulables et ne s'impliquent pas mutuellement. `ADMIN` ne donne
pas le droit d'acheter sans `CUSTOMER`. Toutes les décisions sont prises côté
backend ; Angular ne décide pas des autorisations.

| Action | Visiteur | CUSTOMER | CATALOG_MANAGER | ORDER_MANAGER | ADMIN |
| --- | --- | --- | --- | --- | --- |
| Obtenir un jeton CSRF, s'inscrire comme client, demander ou terminer une récupération | Oui | Oui | Oui | Oui | Oui |
| Se connecter | Oui | Oui | Oui | Oui | Oui |
| Voir son propre compte, se déconnecter | Non | Oui | Oui | Oui | Oui |
| Voir un autre compte, créer un compte interne, changer rôles ou état actif | Non | Non | Non | Non | Oui |
| Lire le catalogue publié | Oui | Oui | Oui | Oui | Oui |
| Estimer un panier produit/pack sans réserver | Oui | Oui | Oui | Oui | Oui |
| Lire, modifier et récupérer son panier client persisté | Non | Oui | Si aussi CUSTOMER | Si aussi CUSTOMER | Si aussi CUSTOMER |
| Acheter et consulter/annuler sa commande | Oui, session invitée propriétaire | Oui, propre compte | Si aussi CUSTOMER | Si aussi CUSTOMER | Si aussi CUSTOMER |
| Gérer catégories, marques, produits et variantes (étape 3a) | Non | Non | Oui | Non | Oui |
| Gérer les photos de produit et l'import CSV | Non | Non | Oui | Non | Oui |
| Gérer les fiches et variantes de packs | Non | Non | Oui | Non | Oui |
| Lister, préparer, annuler, expédier et marquer livrées les commandes | Non | Non | Non | Oui | Oui |
| Consulter les soldes ou ajuster le stock (étape 4) | Non | Non | Non | Non | Oui |

Le catalogue de produits est ouvert selon cette matrice depuis l'étape 3a.
La disponibilité publique et la consultation/les ajustements de stock admin
sont ouverts selon [inventory.md](inventory.md). La lecture publique et la
gestion des packs suivent [packs.md](packs.md). Les réservations restent des
ports Java internes ; le panier suit [cart.md](cart.md) et le checkout invité/client,
la propriété des commandes et leurs transitions suivent [checkout.md](checkout.md).
La matrice complète, incluant contenu, notifications et confidentialité, est
dans [security.md](security.md).
Les routes inconnues restent fermées. Un client ne peut
pas fournir de rôle à l'inscription : le serveur crée toujours `CUSTOMER`. Seul
`ADMIN` peut créer un compte interne, avec au moins un rôle interne. Pour lire
`GET /accounts/{id}`, le serveur vérifie que l'identifiant est celui de la
session ou que le rôle `ADMIN` est présent. Un refus de rôle vaut 403 ; une
session absente vaut 401 ; un compte introuvable après autorisation vaut 404.

## Session et récupération

- Mot de passe de 12 à 128 caractères, hachage Argon2id avec préfixe de format
  `DelegatingPasswordEncoder`. L'email est normalisé et unique.
- Session serveur de 30 minutes d'inactivité. Le cookie de session est `HttpOnly`,
  `SameSite=Lax`, sans domaine ; `Secure` est obligatoire hors profil local/test.
  L'identifiant de session change à la connexion. Une déconnexion invalide la
  session. Un redémarrage du backend invalide les sessions en mémoire.
- Le navigateur appelle `GET /auth/csrf` avant une mutation. Spring Security
  publie `XSRF-TOKEN`, lisible par Angular, qui renvoie `X-XSRF-TOKEN`. Les jetons
  différés et la protection BREACH utilisent la configuration SPA Spring Security
  7. Après connexion/déconnexion, Angular demande un nouveau jeton. Toutes les
  mutations, y compris inscription et récupération, exigent le CSRF.
  La configuration applique `spa()` avant le dépôt personnalisé : le cookie
  CSRF conserve ainsi `SameSite=Lax` et `Secure` hors local/test, même si le
  proxy termine TLS et contacte le backend en HTTP.
- Le filtre relit la version et l'état du compte en base à chaque requête
  authentifiée. Un retrait de rôle, une désactivation ou une réinitialisation
  invalide les anciennes sessions. Les droits du nouveau rôle prennent effet à
  la prochaine connexion.
- La demande de récupération répond 202 pour un email existant ou absent. Un
  lien est mis en outbox transactionnelle pour un compte actif uniquement,
  puis envoyé par le port fournisseur SMTP. Il contient un jeton
  aléatoire de 256 bits dans le fragment URL, jamais envoyé au serveur par la
  navigation. Identity conserve uniquement son empreinte SHA-256 ; le lien dans
  l'outbox est chiffré avec une clé injectée hors Git et supprimé après envoi.
  Il expire après 30 minutes,
  est à usage unique et les anciens liens du même compte sont supprimés. Mailpit
  reçoit réellement ces emails en local. Les demandes du même compte sont
  sérialisées sous verrou, les anciens emails en attente sont annulés, et une
  panne SMTP déclenche des reprises bornées sans supprimer la demande validée.
  Voir [notifications.md](notifications.md). La production exige une configuration
  SMTP et une URL publique injectées au déploiement.
- Limite en mémoire par adresse réseau : 10 tentatives de connexion/15 minutes,
  5 inscriptions/heure et 5 demandes de récupération/heure. Nginx écrase les
  en-têtes de transfert fournis par le client ; Spring les interprète uniquement
  depuis les pairs de confiance. `TRUSTED_PROXY_PATTERN` doit identifier la
  passerelle de production et le backend doit rester privé. En multi-instance,
  remplacer le limiteur mémoire par une protection partagée.
- Les modifications de rôles/état sont transactionnelles et sérialisées par un
  verrou PostgreSQL. Le dernier administrateur actif ne peut être ni désactivé
  ni privé du rôle `ADMIN`. Une trace sans mot de passe ni email est conservée.
  Le rôle de l'acteur est relu après acquisition du verrou, y compris pour la
  création d'un compte interne : une révocation intervenue pendant l'attente
  interdit l'écriture en attente.

## Routes effectivement livrées

Préfixe `/api/v1`. Toutes les mutations JSON utilisent `X-XSRF-TOKEN` obtenu par
`GET /auth/csrf`. Les réponses de compte contiennent uniquement `id`, `email`,
`roles`, `active` ; jamais l'empreinte du mot de passe.

| Méthode et route | Corps | Succès | Accès |
| --- | --- | --- | --- |
| `GET /auth/csrf` | — | 204 + cookie | Public |
| `POST /auth/register` | `{email,password}` | 201 + compte CUSTOMER | Public, CSRF |
| `POST /auth/login` | `{email,password}` | 200 + compte, session | Public, CSRF |
| `POST /auth/logout` | — | 204 | Authentifié, CSRF |
| `GET /auth/me` | — | 200 + compte | Authentifié |
| `GET /accounts/{id}` | — | 200 + compte | Propriétaire ou ADMIN |
| `POST /auth/password-reset/request` | `{email}` | 202 | Public, CSRF |
| `POST /auth/password-reset/complete` | `{token,password}` | 204 | Public, CSRF |
| `POST /admin/internal-accounts` | `{email,password,roles}` | 201 + compte | ADMIN, CSRF |
| `PUT /admin/accounts/{id}/roles` | `{roles}` | 200 + compte | ADMIN, CSRF |
| `PATCH /admin/accounts/{id}/active` | `{active}` | 200 + compte | ADMIN, CSRF |

Inscription et création interne rejettent un email déjà présent (409). Les
identifiants de connexion invalides donnent le même 401. Les jetons expirés ou
réutilisés donnent 400. Les limitations donnent 429. Le refus de supprimer le
dernier administrateur donne 409.

Le premier administrateur ne passe par aucune route publique. La commande locale
explicite du [README](../README.md) exige email et secret fournis à l'exécution ;
elle est disponible uniquement avec `local & !prod`, si aucun administrateur
actif et aucun compte portant cet email n'existent. Aucun identifiant n'est
intégré au code ou à Git.
