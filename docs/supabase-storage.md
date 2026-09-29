# Stockage des photos avec Supabase

Le module `media` utilise le même port `ObjectStorage` pour les produits et
les packs. PostgreSQL Render conserve leurs métadonnées ; les fichiers
originaux et les rendus JPEG vont dans un bucket privé. Le domaine, les
comptes Spring Security et Angular ne dépendent pas de Supabase.

## Adaptateur et configuration

Le client MinIO local reste sélectionné par défaut (`MEDIA_PROVIDER=minio`).
`MEDIA_PROVIDER=s3` sélectionne `S3ObjectStorage`, avec AWS SDK Java 2.55.7,
pour accepter le préfixe `/storage/v1/s3` que le client MinIO 9.0.3 refuse.
Les requêtes utilisent Signature V4, le chemin du bucket et la région fournie.
Les extensions de checksum optionnelles et l'encodage chunked AWS sont
désactivés pour la compatibilité ; HTTPS reste nécessaire en production.

Le bucket doit être créé à l'avance : cet adaptateur ne crée ni bucket,
ni projet, ni droits d'accès. Chaque appel a une limite globale de 60 secondes,
une limite de 30 secondes par tentative et au plus deux tentatives SDK.
Les PUT rejouent la même clé et les mêmes octets ; ils ne créent pas une
deuxième métadonnée métier. Les erreurs applicatives restent génériques et
les diagnostics privés du fournisseur ne doivent pas être exposés dans les logs.

Valeurs **à compléter dans Render**, jamais à copier telles quelles :

```dotenv
MEDIA_PROVIDER=s3
MEDIA_ENDPOINT=https://YOUR_PROJECT_REF.storage.supabase.co/storage/v1/s3
MEDIA_REGION=YOUR_PROJECT_REGION
MEDIA_ACCESS_KEY=YOUR_S3_ACCESS_KEY_ID
MEDIA_SECRET_KEY=YOUR_S3_SECRET_ACCESS_KEY
MEDIA_BUCKET=bricocomptoir-media
```

Copier l'endpoint et la région affichés dans Storage → S3. Les identifiants
sont la paire S3, et non une clé publishable/anon ou le mot de passe PostgreSQL.
Les valeurs fictives ci-dessus ne rendent pas le stockage opérationnel.

## Provisionnement et accès

1. Choisir explicitement l'organisation et vérifier le coût avant création du
   projet `brico-comptoir`. Ne pas toucher aux autres projets pour libérer une
   place. Le plan gratuit inclut actuellement 1 Go de fichiers et peut suspendre
   un projet inactif après une semaine ; il ne constitue pas une garantie de
   disponibilité commerciale.
2. Créer `bricocomptoir-media` avec l'option **Public désactivée**. Limiter les
   types aux JPEG/PNG et la taille à 6 Mio si les réglages du bucket le permettent.
   Les contrôles serveur existants de type réel, taille et dimensions restent
   obligatoires.
3. Générer la paire S3 dans le tableau de bord et la saisir dans Render. Les
   clés S3 Supabase accèdent à tous les buckets du projet et contournent les RLS :
   utiliser un projet dédié, ne pas promettre une restriction au seul bucket,
   ne pas ajouter de politique d'accès public et ne rien transmettre à Angular.
4. Déployer le backend compatible avec ces valeurs, renouveler la connexion
   administrative et téléverser par les API BricoComptoir.
5. Vérifier les rendus card/detail, leurs dimensions, les métadonnées, l'ordre
   et l'image principale. Les brouillons doivent rester inaccessibles au public.

La création de projet et de nouveaux identifiants peut nécessiter une étape
effectuée par l'exploitant, selon l'accès du connecteur et le tableau de bord.
Le suivi doit distinguer les tests locaux du véritable aller-retour Supabase.

## Exploitation et limites

Les originaux ne sont jamais servis par les listes paginées. L'application
produit déjà les images des cartes et fiches ; aucune transformation payante
Supabase n'est nécessaire. Le changement de fournisseur ne transfère pas
automatiquement les fichiers d'un stockage antérieur : copier et vérifier
les objets existants avant de basculer s'il y a des métadonnées à conserver.

La sauvegarde de PostgreSQL Render ne sauvegarde pas les fichiers Supabase.
Prévoir une copie objet distincte, un inventaire et une restauration testée.
Supabase S3 ne fournit pas le versionnement S3 : une suppression d'objet est
définitive. La limite de nettoyage/orphelins après une panne décrite dans
[pack-media.md](pack-media.md) reste applicable.

Avant ouverture : région/hébergement, sous-traitance et transferts de données
à valider dans [privacy-decisions.md](privacy-decisions.md), quotas et alerte
opérateur, sauvegardes et disponibilité de l'offre choisie.

Références vérifiées : [authentification S3 Supabase](https://supabase.com/docs/guides/storage/s3/authentication),
[compatibilité](https://supabase.com/docs/guides/storage/s3/compatibility),
[tarifs](https://supabase.com/pricing),
[client S3 Java](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/examples-s3.html).
