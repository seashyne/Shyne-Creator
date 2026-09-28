export interface Env {
  DB: D1Database;
  AVATAR_BUCKET: R2Bucket;
  MOJANG_SESSION_URL: string;
}

export interface AuthAccount {
  uuid: string;
  username: string;
}

export interface SessionRow {
  token: string;
  account_uuid: string;
  created_at: number;
  expires_at: number;
}

export interface AvatarRow {
  id: string;
  owner_uuid: string;
  name: string;
  version: string;
  description: string;
  total_size: number;
  manifest: string;
  visibility: string;
  share_id: string | null;
  package_hash: string | null;
  permissions: string;
  license: string;
  created_at: number;
  updated_at: number;
}

export interface UploadRow {
  upload_id: string;
  avatar_id: string;
  owner_uuid: string;
  manifest: string;
  expected_chunks: string;
  received_chunks: string;
  created_at: number;
  expires_at: number;
}

export interface ChallengeRow {
  challenge_id: string;
  server_id: string;
  username: string;
  created_at: number;
  expires_at: number;
}

export interface ChunkRow {
  hash: string;
  avatar_id: string;
  owner_uuid: string;
  size: number;
  created_at: number;
}

export interface CloudAvatarResponse {
  id: string;
  name: string;
  description: string;
  version: string;
  visibility: string;
  share_id: string;
  package_hash: string;
  permissions: string[];
  license: string;
  owner: {
    uuid: string;
    username: string;
    creator_id: string;
  };
  manifest?: Record<string, unknown>;
}

export interface CloudPageResponse {
  items: CloudAvatarResponse[];
  next_offset: number | null;
}

// Validation constants (must match ShyneCloudClient.java)
export const CHUNK_BYTES = 512 * 1024; // 512 KiB
export const MAX_JSON_BYTES = 2 * 1024 * 1024; // 2 MiB
export const MAX_FILES = 256;
export const MAX_AVATAR_BYTES = 64 * 1024 * 1024; // 64 MiB
export const SAFE_ID = /^[a-z0-9][a-z0-9_.-]{0,63}$/;
export const SAFE_HASH = /^[a-f0-9]{64}$/;
export const SAFE_SHARE_ID = /^[a-f0-9-]{36}$/;
export const SESSION_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 days
export const CHALLENGE_TTL_MS = 5 * 60 * 1000; // 5 minutes
export const UPLOAD_TTL_MS = 30 * 60 * 1000; // 30 minutes
