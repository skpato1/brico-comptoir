# Interface publique

Les routes Angular `/`, `/solutions`, `/packs`, `/packs/:id`, `/catalogue`,
`/produits/:id`, `/panier`, `/commande`, `/confirmation/:id` et `/acces-equipe`
utilisent les API existantes. Recherche, filtres, tri et pagination du catalogue
restent côté serveur et dans l'URL. Les packs sont filtrés dans leur petite
liste publique actuelle ; aucun moteur de recommandation fictif.

L'accueil affiche « Meilleures ventes » lorsque l'administration a enregistré
trois produits publiés distincts. La section reprend leurs fiches et photos
réelles, avec un défilement horizontal adapté au mobile. Le choix est
éditorial : le catalogue ne fournit pas de classement de ventes. La section
reste cachée sans sélection complète et visible.

Le carrousel d'accueil commence, pour la sélection initiale, par la bannière
« Commande en ligne » fournie par l'exploitant et adaptée au catalogue réel.
Elle utilise une composition horizontale sur ordinateur et une composition
verticale sur mobile pour préserver le logo, le titre et les produits
illustratifs. Les indications de paiement en ligne, de retrait en agence et de
rapidité de livraison présentes sur le fichier reçu ont été retirées car ces
services ne sont pas confirmés par le parcours actuel. Les autres bannières et
leur ordre restent modifiables dans l'administration.

La fiche produit affiche « Autres produits favoris des professionnels » lorsque
quatre produits publiés ont été sélectionnés dans Boutique → Favoris des
professionnels. Les trois cartes sont tirées du catalogue réel, dans l'ordre
éditorial, après exclusion du produit consulté et des variantes sans stock
disponible. Le quatrième produit sert de repli. Si trois références disponibles
ne restent pas, la section est masquée. Le défilement horizontal mobile accepte
le geste tactile et des boutons de navigation ; les cartes mènent aux fiches.

L'ouverture réussie d'une fiche produit publiée enregistre une vue agrégée pour
les statistiques de l'administration. Les chargements de cartes, de résultats
de recherche ou de produits recommandés ne sont pas comptés. Les visites des
comptes équipe sont exclues par le serveur.

La session et le panier vivent au niveau de l'application, indépendamment des
changements de page. Une confirmation est relue depuis l'API propriétaire,
sans stocker d'adresse dans le navigateur. Le checkout conserve sa clé de
réessai tant que la réponse est incertaine et empêche de quitter cette étape
sans avertissement.

Les noms de composants sont ajoutés au contrat pack (`componentNames`) via
les ports publics catalogue. « Non inclus » compare les quantités avec les
autres variantes publiées ; tout article non listé reste exclu. Aucun outil,
service ou caractéristique réelle n'est inventé. Sans photos, une indication
explicite remplace l'image. Les rendus carte/détail du module media utilisent
`srcset`, `sizes`, dimensions réservées, décodage asynchrone et chargement différé.

Interface blanche, actions rouges, texte sombre, liens de navigation natifs,
lien d'évitement, focus visible et report du focus au contenu à chaque navigation.
Les erreurs de formulaire sont associées aux champs et annoncées. Les écrans
de gestion restent accessibles par l'accès équipe aux rôles concernés ;
la décision de sécurité reste au serveur.

La navigation publique ne présente plus de rubrique « Mon compte ». Le checkout
invité recueille les coordonnées requises sans inscription ; l'e-mail reste
facultatif. L'ancienne route `/compte` reste accessible pour les anciens liens
de récupération et les comptes clients préexistants, sans création de compte.

Le bandeau supérieur affiche les icônes Facebook et Instagram lorsqu'une URL
officielle est configurée. Les liens ouvrent un nouvel onglet avec libellés
accessibles ; sur mobile, les icônes restent visibles à côté du libellé court.
Les URL sont chargées depuis le contenu public et administrables sous « Boutique
→ Réseaux sociaux ». Un réseau sans URL ne génère aucun lien inactif.
