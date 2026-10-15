# Audit UX de la boutique — 13 octobre 2026

Périmètre : parcours public Angular 21, de l'accueil à la confirmation. Cet audit
repose sur les routes, composants, contrats API et captures locales à 1440 et
375 px, avec le catalogue local copié de la production. Les changements restent
sur la branche locale `codex/storefront-ux-redesign` tant qu'ils ne sont pas
explicitement publiés.

## Parcours et contraintes réelles

`app.routes.ts` relie accueil, solutions/packs, catalogue, fiche, panier,
commande, confirmation, compte, contact, confidentialité et administration.
L'accueil et le checkout sont chargés immédiatement ; les autres pages passent
par `loadComponent`. `CatalogApi` fournit recherche, catégories, marques,
prix, tri par nom/prix/date, fiches, images et disponibilité par variante.
`CartState` gère le panier invité ou client ; `OrderApi` impose une prévisualisation
serveur avant la commande. La confirmation reste idempotente avec la même clé
après une réponse incertaine. La commande invitée existe déjà : ne pas imposer
la création d'un compte.

Le contrat actuel ne fournit pas de notes clients, favoris, promotions,
ancien prix, ventes classées, estimation de délai, suggestions de recherche,
ville/délégation normalisée ni mode de livraison alternatif. Ces informations
ne doivent pas être simulées dans l'interface. Le prix, les frais et le stock
sont calculés ou validés côté serveur ; le design ne peut pas les promettre
avant les réponses API correspondantes. La copie locale du catalogue contient
6 620 fiches publiées, mais 6 616 sans description ni caractéristiques :
l'interface doit rester utile sans texte produit inventé.

## Constats priorisés

| Priorité | Constat vérifié | Conséquence | Correction prévue |
| --- | --- | --- | --- |
| CRITICAL | Sur mobile, le formulaire de filtres complet précède les résultats du catalogue. | Les premiers produits se trouvent loin sous le pli ; explorer devient laborieux. | Barre de recherche/résultats visible, filtres dans un panneau accessible, filtres actifs et réinitialisation claire. |
| CRITICAL | La fiche mobile ne garde pas prix et action d'achat visibles pendant le défilement. | Retour en haut nécessaire après lecture des détails. | Barre d'achat fixe, synchronisée avec variante, quantité, disponibilité et droits de session. |
| HIGH | Les cartes ne montrent pas la marque ni un libellé d'action explicite ; la description est vide sur presque tout le catalogue. | Comparaison difficile, action « ↗ » ambiguë. | Hiérarchie image–marque–nom–prix–action « Voir le produit ». La disponibilité reste sur la fiche, car l'API de liste ne la fournit pas. |
| HIGH | L'accueil met les logos puis quatre dernières fiches, sans véritable entrée par besoin/catégorie. | Découverte peu guidée pour 358 catégories principalement plates. | Entrées de recherche, catégories ou besoins éditorialement validés, plus un bloc produit réel sélectionné. Ne pas inventer de meilleures ventes ou promotions. |
| HIGH | Le checkout montre un long formulaire puis un récapitulatif séparé ; le prix de livraison n'apparaît qu'après prévisualisation serveur. | Les étapes et le coût final sont peu lisibles avant confirmation. | Présentation en étapes visibles sur une page, résumé persistant et explication claire du calcul. Conserver le protocole prévisualisation → empreinte → confirmation. |
| MEDIUM | Le panier contient les informations nécessaires, mais sous forme de liste textuelle avec une estimation en bas. | Quantité, prix et prochaine action demandent plus d'effort à repérer. | Lignes structurées, prix et sous-total lisibles, action de commande dominante, livraison indiquée comme à calculer. |
| MEDIUM | Les styles partagés utilisent peu de tokens et certains composants ont des règles locales similaires. | Variations de rayon, d'espacement et d'états selon les pages. | Tokens CSS et primitives réutilisées avant la refonte page par page. |
| MEDIUM | Chargements du catalogue et de la fiche principalement textuels ; états vides génériques. | Sauts visuels et aide limitée après une recherche sans résultat. | Squelettes simples et actions de reprise pertinentes, sans fausses recommandations. |
| LOW | La navigation mobile ouvre une liste A–Z longue de catégories. | Une catégorie précise peut rester difficile à trouver. | Conserver la recherche de catégories, améliorer les raccourcis et la navigation inférieure. La hiérarchie commerciale exige des données métier. |

## Ordre d'implémentation

1. Définir couleurs, typographie, espacements, rayons, états focus et boutons
   partagés ; établir les largeurs de contenu et les règles mobile-first.
2. Revoir en-tête, recherche et navigation mobile. Garder les liens existants
   et le menu de catégories réel ; ne pas créer de favoris/promotions fictifs.
3. Structurer l'accueil autour de la recherche, du catalogue réel, des marques
   et des catégories existantes ; messages de confiance limités aux services
   réellement opérationnels.
4. Améliorer la carte partagée et le catalogue : recherche, tri, filtres
   mobiles, compteur, pagination et états de chargement/erreur.
5. Revoir la fiche produit/pack : galerie, sélection de variante, quantité,
   disponibilité, détails techniques existants et achat mobile persistant.
6. Revoir panier, adresse/checkout et confirmation sans toucher aux invariants
   serveur, à la clé d'idempotence ni au traitement de réponse incertaine.
7. Harmoniser compte, contact, confidentialité et administration seulement
   après stabilisation du parcours d'achat public.

Chaque tranche doit compiler, passer les tests concernés, et être parcourue
sur ordinateur et sur mobile. La revue finale couvre 320, 375, 390, 430,
768, 1024, 1280, 1440 et 1920 px, clavier/focus, absence de débordement
horizontal, chargement d'images, erreurs API et achat invité. Les critères
commerciaux restent : trouver un produit, comprendre variante/prix/frais,
l'ajouter au panier puis confirmer sans confusion, en conservant les fonctions
existantes. Le taux de conversion réel nécessitera des mesures après lancement ;
une amélioration visuelle seule ne permet pas de l'affirmer.

## Écarts explicitement laissés au contrat métier

La carte ouvre la fiche au lieu d'ajouter directement au panier : une fiche peut
avoir plusieurs variantes et seule l'API de disponibilité par variante permet
de contrôler l'action. Le panier ne contient que l'identifiant de l'offre et
son libellé ; aucune association sûre à une photo de produit n'est fournie.
L'adresse exige actuellement un code postal et ne propose pas de note : retirer
ce champ ou en ajouter un nécessiterait un changement coordonné du contrat
backend et de ses validations. Les catégories source sont nombreuses et souvent
plates ou en doublon ; les quatre raccourcis de l'accueil sont donc des
recherches réellement testées, pas une hiérarchie commerciale inventée.
Les promotions, évaluations, meilleures ventes, délais de livraison et
recommandations ne sont pas affichés sans données réelles et service dédié.
