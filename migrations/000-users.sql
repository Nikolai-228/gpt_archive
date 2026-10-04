DROP TABLE IF EXISTS users;

CREATE TABLE users (
  id            serial PRIMARY KEY,
  username      text NOT NULL UNIQUE,
  given_name    text NOT NULL,
  family_name   text NOT NULL,
  password_hash char(60) NOT NULL /* bcrypt hash */
);
