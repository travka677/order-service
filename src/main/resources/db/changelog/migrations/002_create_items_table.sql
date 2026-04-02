--liquibase formatted sql

--changeset dev:002-create-items-table
CREATE TABLE items
(
    id          UUID         DEFAULT gen_random_uuid() NOT NULL,
    name        VARCHAR(255)                           NOT NULL,
    price       NUMERIC(12, 2)                         NOT NULL,
    created_at  TIMESTAMP                              NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP                              NOT NULL DEFAULT now(),

    CONSTRAINT pk_items PRIMARY KEY (id)
);

--changeset dev:002-create-items-indexes
CREATE INDEX idx_items_name ON items (name);
