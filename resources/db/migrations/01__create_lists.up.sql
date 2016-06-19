CREATE TABLE lists (
       id BIGSERIAL NOT NULL PRIMARY KEY,
       created_at TIMESTAMP NOT NULL DEFAULT now(),
       name VARCHAR(64) NOT NULL CHECK (length(name) > 4),
       CONSTRAINT unique_name UNIQUE(name)
);
