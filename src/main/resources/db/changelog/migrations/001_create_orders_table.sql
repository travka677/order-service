--liquibase formatted sql

--changeset dev:001-create-orders-table
CREATE TABLE orders
(
    id           UUID         DEFAULT gen_random_uuid() NOT NULL,
    user_id      UUID                                    NOT NULL,
    status       VARCHAR(50)                             NOT NULL,
    total_price  NUMERIC(12, 2)                          NOT NULL,
    deleted      BOOLEAN      DEFAULT false              NOT NULL,
    created_at   TIMESTAMP                               NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP                               NOT NULL DEFAULT now(),

    CONSTRAINT pk_orders PRIMARY KEY (id)
);

--changeset dev:001-create-orders-indexes
CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_deleted ON orders (deleted);
