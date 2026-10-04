DROP TABLE IF EXISTS ui_messages;

CREATE TABLE ui_messages
  ( message_id      text PRIMARY KEY
  , message_content text NOT NULL DEFAULT ''
  );
