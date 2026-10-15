CREATE TABLE content_social_links (
    id integer PRIMARY KEY CHECK (id = 1),
    facebook_url varchar(300) NOT NULL DEFAULT '',
    instagram_url varchar(300) NOT NULL DEFAULT '',
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(254)
);

-- Facebook URL supplied by the shop owner; Instagram stays hidden until configured.
INSERT INTO content_social_links(id, facebook_url)
VALUES (1, 'https://www.facebook.com/brico.comptoir.tunisie');

REVOKE INSERT, DELETE ON content_social_links FROM "${applicationUser}";
GRANT SELECT, UPDATE ON content_social_links TO "${applicationUser}";
