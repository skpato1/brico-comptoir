# Interface publique

Les routes Angular `/`, `/solutions`, `/packs`, `/packs/:id`, `/catalogue`,
`/produits/:id`, `/panier`, `/commande`, `/confirmation/:id` et `/compte`
utilisent les API existantes. Recherche, filtres, tri et pagination du catalogue
restent côté serveur et dans l'URL. Les packs sont filtrés dans leur petite
liste publique actuelle ; aucun moteur de recommandation fictif.

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
de gestion existants restent accessibles depuis le compte aux rôles concernés ;
la décision de sécurité reste au serveur.
