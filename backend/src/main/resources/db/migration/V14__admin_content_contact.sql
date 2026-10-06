CREATE TABLE content_hero (
    id integer PRIMARY KEY CHECK (id = 1),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(254)
);
INSERT INTO content_hero(id) VALUES (1);

CREATE TABLE content_hero_slide (
    id uuid PRIMARY KEY,
    position integer NOT NULL UNIQUE CHECK (position BETWEEN 0 AND 9),
    visible boolean NOT NULL,
    image varchar(30) NOT NULL CHECK (image IN ('kits','hardware','tools')),
    label varchar(80) NOT NULL CHECK (length(trim(label)) > 0),
    title varchar(140) NOT NULL CHECK (length(trim(title)) > 0),
    description varchar(400) NOT NULL CHECK (length(trim(description)) > 0),
    alt varchar(180) NOT NULL CHECK (length(trim(alt)) > 0),
    link varchar(120) NOT NULL,
    action varchar(80) NOT NULL CHECK (length(trim(action)) > 0),
    detail varchar(160) NOT NULL CHECK (length(trim(detail)) > 0)
);
INSERT INTO content_hero_slide(id,position,visible,image,label,title,description,alt,link,action,detail) VALUES
('4f50a2a1-246a-4267-b157-a46f10793811',0,true,'kits','Kits & packs',E'Le bon kit.\nLe début de votre projet.','Découvrez les packs publiés, comparez leur contenu et choisissez la composition adaptée à votre besoin.','Illustration : boîte de projet avec outils et sachets de fixations.','/solutions','Découvrir les kits','Compositions détaillées · Variantes à comparer'),
('4f50a2a1-246a-4267-b157-a46f10793812',1,true,'hardware','Fixations & quincaillerie',E'Chaque pièce\na son importance.','Vis, chevilles, accessoires : consultez les références du catalogue pour compléter votre sélection.','Illustration : vis, chevilles, rondelles et équerres sur un établi.','/catalogue','Explorer la quincaillerie','Références et caractéristiques dans chaque fiche'),
('4f50a2a1-246a-4267-b157-a46f10793813',2,true,'tools','Outils & équipement',E'À vous de faire.\nÀ nous d’équiper.','Un article à l’unité ou un pack : partez de votre projet et retrouvez les produits publiés au comptoir.','Illustration : perceuse, marteau, pince et tournevis sur un établi.','/catalogue','Voir les produits','À l’unité ou en pack · Prix en dinars tunisiens');

CREATE TABLE content_contact (
    id integer PRIMARY KEY CHECK (id = 1),
    email varchar(254) NOT NULL,
    phone varchar(40) NOT NULL DEFAULT '',
    address varchar(240) NOT NULL DEFAULT '',
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(254)
);
INSERT INTO content_contact(id,email) VALUES (1,'');

CREATE TABLE content_contact_message (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL,
    email varchar(254) NOT NULL,
    subject varchar(160) NOT NULL,
    body varchar(3000) NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('NEW','RESOLVED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    resolved_at timestamptz
);
CREATE INDEX content_contact_message_queue_idx ON content_contact_message(status,created_at DESC,id);

REVOKE INSERT, DELETE ON content_hero, content_contact FROM "${applicationUser}";
GRANT SELECT, UPDATE ON content_hero, content_contact TO "${applicationUser}";
GRANT SELECT, INSERT, UPDATE, DELETE ON content_hero_slide, content_contact_message TO "${applicationUser}";
