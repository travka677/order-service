--liquibase formatted sql

--changeset dev:003-create-order-items-table
CREATE TABLE order_items
(
    id          UUID         DEFAULT gen_random_uuid() NOT NULL,
    order_id    UUID                                    NOT NULL,
    item_id     UUID                                    NOT NULL,
    quantity    INT                                     NOT NULL,
    created_at  TIMESTAMP                               NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP                               NOT NULL DEFAULT now(),

    CONSTRAINT pk_order_items PRIMARY KEY (id),
    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id) REFERENCES orders (id),
    CONSTRAINT fk_order_items_item FOREIGN KEY (item_id) REFERENCES items (id)
);

--changeset dev:003-create-order-items-indexes
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
CREATE INDEX idx_order_items_item_id ON order_items (item_id);
CREATE UNIQUE INDEX idx_order_items_order_item_unique ON order_items (order_id, item_id);
