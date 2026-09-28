CREATE TABLE inventory_stock (
    variant_id uuid PRIMARY KEY,
    on_hand bigint NOT NULL DEFAULT 0,
    reserved bigint NOT NULL DEFAULT 0,
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT inventory_stock_nonnegative CHECK (on_hand >= 0 AND reserved >= 0),
    CONSTRAINT inventory_stock_reserved_within_physical CHECK (reserved <= on_hand)
);

CREATE TABLE inventory_reservation (
    id uuid PRIMARY KEY,
    status varchar(10) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'RELEASED', 'CONSUMED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE inventory_reservation_line (
    reservation_id uuid NOT NULL REFERENCES inventory_reservation(id) ON DELETE RESTRICT,
    variant_id uuid NOT NULL,
    quantity bigint NOT NULL CHECK (quantity > 0),
    PRIMARY KEY (reservation_id, variant_id)
);
CREATE INDEX inventory_reservation_line_variant_idx ON inventory_reservation_line(variant_id);

CREATE TABLE inventory_adjustment (
    id uuid PRIMARY KEY,
    variant_id uuid NOT NULL,
    delta bigint NOT NULL CHECK (delta <> 0),
    reason varchar(500) NOT NULL CHECK (length(trim(reason)) > 0),
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE inventory_movement (
    id uuid PRIMARY KEY,
    variant_id uuid NOT NULL,
    reservation_id uuid REFERENCES inventory_reservation(id) ON DELETE RESTRICT,
    operation_id uuid REFERENCES inventory_adjustment(id) ON DELETE RESTRICT,
    actor_name varchar(254),
    type varchar(10) NOT NULL CHECK (type IN ('ADJUST', 'RESERVE', 'RELEASE', 'CONSUME')),
    on_hand_delta bigint NOT NULL,
    reserved_delta bigint NOT NULL,
    reason varchar(500) NOT NULL DEFAULT '',
    created_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT inventory_movement_shape CHECK (
        (type = 'ADJUST' AND operation_id IS NOT NULL AND reservation_id IS NULL
            AND actor_name IS NOT NULL AND on_hand_delta <> 0 AND reserved_delta = 0)
        OR (type = 'RESERVE' AND reservation_id IS NOT NULL AND operation_id IS NULL
            AND on_hand_delta = 0 AND reserved_delta > 0)
        OR (type = 'RELEASE' AND reservation_id IS NOT NULL AND operation_id IS NULL
            AND on_hand_delta = 0 AND reserved_delta < 0)
        OR (type = 'CONSUME' AND reservation_id IS NOT NULL AND operation_id IS NULL
            AND on_hand_delta = reserved_delta AND reserved_delta < 0)
    )
);
CREATE UNIQUE INDEX inventory_movement_operation_idx ON inventory_movement(operation_id)
    WHERE operation_id IS NOT NULL;
CREATE UNIQUE INDEX inventory_movement_reservation_line_type_idx
    ON inventory_movement(reservation_id, variant_id, type) WHERE reservation_id IS NOT NULL;
CREATE INDEX inventory_movement_variant_time_idx ON inventory_movement(variant_id, created_at DESC, id);

GRANT SELECT, INSERT, UPDATE ON inventory_stock, inventory_reservation,
    inventory_reservation_line, inventory_adjustment TO "${applicationUser}";
GRANT SELECT, INSERT ON inventory_movement TO "${applicationUser}";
REVOKE UPDATE, DELETE ON inventory_movement FROM "${applicationUser}";
