CREATE TABLE IF NOT EXISTS homes (
  owner VARCHAR(36) NOT NULL,
  name VARCHAR(32) NOT NULL,
  location VARCHAR(255) NOT NULL,
  created_at BIGINT NOT NULL,
  PRIMARY KEY (owner, name)
);
