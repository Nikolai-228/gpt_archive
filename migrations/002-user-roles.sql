DROP TABLE IF EXISTS roles;

CREATE TABLE roles
  ( role_id   serial PRIMARY KEY
  , role_name text NOT NULL UNIQUE
  );

ALTER TABLE users RENAME COLUMN id TO user_id;

CREATE TABLE user_roles
  ( user_id integer REFERENCES users ( user_id ) ON DELETE CASCADE
  , role_id integer REFERENCES roles ( role_id ) ON DELETE CASCADE
  , PRIMARY KEY ( user_id, role_id )
  );
