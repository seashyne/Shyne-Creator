-- Shyne Cloud Backend Database Schema

CREATE TABLE IF NOT EXISTS accounts (
  uuid TEXT PRIMARY KEY,
  username TEXT NOT NULL,
  created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  last_login_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000)
);

CREATE TABLE IF NOT EXISTS auth_challenges (
  challenge_id TEXT PRIMARY KEY,
  server_id TEXT NOT NULL,
  username TEXT NOT NULL,
  created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  expires_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  token TEXT PRIMARY KEY,
  account_uuid TEXT NOT NULL REFERENCES accounts(uuid),
  created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  expires_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS avatars (
  id TEXT NOT NULL,
  owner_uuid TEXT NOT NULL REFERENCES accounts(uuid),
  name TEXT NOT NULL,
  version TEXT NOT NULL DEFAULT '1.0.0',
  description TEXT NOT NULL DEFAULT '',
  total_size INTEGER NOT NULL DEFAULT 0,
  manifest TEXT NOT NULL DEFAULT '{}',
  visibility TEXT NOT NULL DEFAULT 'private',
  share_id TEXT,
  package_hash TEXT,
  permissions TEXT NOT NULL DEFAULT '[]',
  license TEXT NOT NULL DEFAULT '',
  created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  updated_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  PRIMARY KEY (id, owner_uuid)
);

CREATE INDEX IF NOT EXISTS idx_avatars_owner ON avatars(owner_uuid);
CREATE INDEX IF NOT EXISTS idx_avatars_share ON avatars(share_id);
CREATE INDEX IF NOT EXISTS idx_avatars_visibility ON avatars(visibility);

CREATE TABLE IF NOT EXISTS uploads (
  upload_id TEXT PRIMARY KEY,
  avatar_id TEXT NOT NULL,
  owner_uuid TEXT NOT NULL,
  manifest TEXT NOT NULL DEFAULT '{}',
  expected_chunks TEXT NOT NULL DEFAULT '[]',
  received_chunks TEXT NOT NULL DEFAULT '[]',
  created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  expires_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS avatar_chunks (
  hash TEXT NOT NULL,
  avatar_id TEXT NOT NULL,
  owner_uuid TEXT NOT NULL,
  size INTEGER NOT NULL,
  created_at INTEGER NOT NULL DEFAULT (unixepoch() * 1000),
  PRIMARY KEY (hash, avatar_id, owner_uuid)
);

CREATE INDEX IF NOT EXISTS idx_chunks_avatar ON avatar_chunks(avatar_id, owner_uuid);
