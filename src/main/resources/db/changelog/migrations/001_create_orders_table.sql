--liquibase formatted sql

--changeset dev:001-create-orders-table
CREATE TABLE orders
(
    id           UUID           DEFAULT gen_random_uuid() NOT NULL,
    user_id      UUID                                      NOT NULL,
    status       SMALLINT                                  NOT NULL,
    total_price  NUMERIC(12, 2)                            NOT NULL,
    deleted      BOOLEAN        DEFAULT false              NOT NULL,
    created_at   TIMESTAMP                                 NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP                                 NOT NULL DEFAULT now(),

    CONSTRAINT pk_orders PRIMARY KEY (id)
);

--changeset dev:001-create-orders-indexes
-- Covers findAllByUserIdAndDeletedFalse (most frequent query)
CREATE INDEX idx_orders_user_id_deleted ON orders (user_id, deleted);
-- Covers filtering by status with soft-delete condition
CREATE INDEX idx_orders_status_deleted ON orders (status, deleted);
