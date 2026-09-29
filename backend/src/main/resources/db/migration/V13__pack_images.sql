CREATE TABLE media_pack_image (
    id uuid PRIMARY KEY,
    pack_id uuid NOT NULL,
    original_key varchar(220) NOT NULL UNIQUE,
    card_key varchar(220) NOT NULL UNIQUE,
    detail_key varchar(220) NOT NULL UNIQUE,
    source_mime varchar(16) NOT NULL CHECK (source_mime IN ('image/jpeg', 'image/png')),
    source_bytes bigint NOT NULL CHECK (source_bytes > 0 AND source_bytes <= 6291456),
    source_sha256 char(64) NOT NULL CHECK (source_sha256 ~ '^[0-9a-f]{64}$'),
    width integer NOT NULL CHECK (width BETWEEN 320 AND 6000),
    height integer NOT NULL CHECK (height BETWEEN 320 AND 6000),
    sort_order integer NOT NULL CHECK (sort_order BETWEEN 0 AND 11),
    is_primary boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT media_pack_image_order UNIQUE (pack_id, sort_order) DEFERRABLE INITIALLY DEFERRED
);
CREATE UNIQUE INDEX media_pack_image_one_primary ON media_pack_image(pack_id) WHERE is_primary;
CREATE INDEX media_pack_image_pack_idx ON media_pack_image(pack_id, sort_order);
GRANT SELECT, INSERT, UPDATE, DELETE ON media_pack_image TO "${applicationUser}";
