import { Hono } from 'hono';
import type { Env, AuthAccount, AvatarRow, CloudAvatarResponse, CloudPageResponse } from '../types';
import { SAFE_ID, MAX_FILES, MAX_AVATAR_BYTES, UPLOAD_TTL_MS } from '../types';
import { requireAuth } from '../middleware';

type Variables = { account: AuthAccount };
const avatars = new Hono<{ Bindings: Env; Variables: Variables }>();

// GET /v1/me/avatars — List user's own avatars
avatars.get('/me/avatars', requireAuth, async (c) => {
  const account = c.get('account');
  const query = c.req.query('q') || '';
  const limit = Math.min(Math.max(1, parseInt(c.req.query('limit') || '30')), 100);
  const offset = Math.max(0, parseInt(c.req.query('offset') || '0'));

  let sql = 'SELECT * FROM avatars WHERE owner_uuid = ?';
  const params: (string | number)[] = [account.uuid];

  if (query.trim()) {
    sql += ' AND (name LIKE ? OR id LIKE ? OR description LIKE ?)';
    const like = `%${query.trim()}%`;
    params.push(like, like, like);
  }
  sql += ' ORDER BY updated_at DESC LIMIT ? OFFSET ?';
  params.push(limit + 1, offset);

  const { results } = await c.env.DB.prepare(sql).bind(...params).all<AvatarRow>();
  const hasMore = results.length > limit;
  const items = results.slice(0, limit);

  const response: CloudPageResponse = {
    items: items.map((row) => avatarToResponse(row, account)),
    next_offset: hasMore ? offset + limit : null,
  };
  return c.json(response);
});

// GET /v1/avatars/:avatarId — Get avatar detail with manifest
avatars.get('/avatars/:avatarId', requireAuth, async (c) => {
  const account = c.get('account');
  const avatarId = c.req.param('avatarId')!;
  if (!SAFE_ID.test(avatarId)) return c.json({ error: 'Invalid Avatar id' }, 400);

  const row = await c.env.DB.prepare(
    'SELECT * FROM avatars WHERE id = ? AND owner_uuid = ?'
  ).bind(avatarId, account.uuid).first<AvatarRow>();

  if (!row) return c.json({ error: 'Avatar not found' }, 404);

  const response = avatarToResponse(row, account);
  try {
    response.manifest = JSON.parse(row.manifest);
  } catch {
    response.manifest = {};
  }
  return c.json(response);
});

// POST /v1/avatars — Create avatar (initiate chunked upload)
avatars.post('/avatars', requireAuth, async (c) => {
  const account = c.get('account');
  const body = await c.req.json<{
    id?: string;
    name?: string;
    version?: string;
    description?: string;
    manifest?: Record<string, unknown>;
  }>();

  const avatarId = body?.id?.toLowerCase();
  if (!avatarId || !SAFE_ID.test(avatarId)) {
    return c.json({ error: 'Invalid Avatar id' }, 400);
  }

  const name = body?.name?.trim() || avatarId;
  const version = body?.version?.trim() || '1.0.0';
  const description = body?.description?.trim() || '';
  const manifest = body?.manifest || {};

  // Validate manifest structure
  const files = (manifest as Record<string, unknown>)?.files;
  if (!Array.isArray(files) || files.length < 1 || files.length > MAX_FILES) {
    return c.json({ error: 'Manifest must contain 1-' + MAX_FILES + ' files' }, 400);
  }

  const totalSize = (manifest as Record<string, unknown>)?.total_size;
  if (typeof totalSize === 'number' && totalSize > MAX_AVATAR_BYTES) {
    return c.json({ error: 'Avatar exceeds 64 MiB size limit' }, 400);
  }

  // Collect expected chunk hashes from manifest
  const expectedChunks = new Set<string>();
  for (const file of files as Array<{ chunks?: Array<{ hash: string }> }>) {
    if (file.chunks) {
      for (const chunk of file.chunks) {
        if (chunk.hash) expectedChunks.add(chunk.hash);
      }
    }
  }

  const uploadId = crypto.randomUUID();
  const now = Date.now();
  const expiresAt = now + UPLOAD_TTL_MS;

  await c.env.DB.prepare(
    `INSERT INTO uploads
     (upload_id, avatar_id, owner_uuid, manifest, expected_chunks, received_chunks, created_at, expires_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?)`
  ).bind(
    uploadId, avatarId, account.uuid,
    JSON.stringify(manifest), JSON.stringify([...expectedChunks]),
    '[]', now, expiresAt
  ).run();

  return c.json({
    upload_id: uploadId,
    avatar_id: avatarId,
    expected_chunks: [...expectedChunks],
  });
});

// PUT /v1/avatars/:avatarId/publication — Publish as Public Share
avatars.put('/avatars/:avatarId/publication', requireAuth, async (c) => {
  const account = c.get('account');
  const avatarId = c.req.param('avatarId')!;
  if (!SAFE_ID.test(avatarId)) return c.json({ error: 'Invalid Avatar id' }, 400);

  const row = await c.env.DB.prepare(
    'SELECT * FROM avatars WHERE id = ? AND owner_uuid = ?'
  ).bind(avatarId, account.uuid).first<AvatarRow>();
  if (!row) return c.json({ error: 'Avatar not found; upload it first' }, 404);

  // Read the zip body
  const zipBytes = await c.req.arrayBuffer();
  if (zipBytes.byteLength === 0 || zipBytes.byteLength > MAX_AVATAR_BYTES) {
    return c.json({ error: 'Invalid Public Avatar ZIP size' }, 400);
  }

  // Parse permissions from header
  const permHeader = c.req.header('X-Shyne-Permissions') || '';
  const permissions = permHeader.split(',').map((p) => p.trim()).filter((p) => p.length > 0);

  // Compute package hash
  const hashBuffer = await crypto.subtle.digest('SHA-256', zipBytes);
  const packageHash = Array.from(new Uint8Array(hashBuffer))
    .map((b) => b.toString(16).padStart(2, '0')).join('');

  // Store zip in R2
  const shareId = row.share_id || crypto.randomUUID();
  await c.env.AVATAR_BUCKET.put(`shares/${shareId}/package.zip`, zipBytes, {
    httpMetadata: { contentType: 'application/vnd.shyne.avatar+zip' },
    customMetadata: { avatarId, ownerUuid: account.uuid, packageHash },
  });

  // Update avatar row
  const now = Date.now();
  await c.env.DB.prepare(
    `UPDATE avatars SET visibility = 'public', share_id = ?, package_hash = ?,
     permissions = ?, updated_at = ? WHERE id = ? AND owner_uuid = ?`
  ).bind(shareId, packageHash, JSON.stringify(permissions), now, avatarId, account.uuid).run();

  return c.json({ share_id: shareId, package_hash: packageHash });
});

// DELETE /v1/avatars/:avatarId/publication — Unpublish
avatars.delete('/avatars/:avatarId/publication', requireAuth, async (c) => {
  const account = c.get('account');
  const avatarId = c.req.param('avatarId')!;
  if (!SAFE_ID.test(avatarId)) return c.json({ error: 'Invalid Avatar id' }, 400);

  const row = await c.env.DB.prepare(
    'SELECT share_id FROM avatars WHERE id = ? AND owner_uuid = ?'
  ).bind(avatarId, account.uuid).first<{ share_id: string | null }>();
  if (!row) return c.json({ error: 'Avatar not found' }, 404);

  if (row.share_id) {
    await c.env.AVATAR_BUCKET.delete(`shares/${row.share_id}/package.zip`);
  }

  const now = Date.now();
  await c.env.DB.prepare(
    `UPDATE avatars SET visibility = 'private', share_id = NULL, package_hash = NULL,
     permissions = '[]', updated_at = ? WHERE id = ? AND owner_uuid = ?`
  ).bind(now, avatarId, account.uuid).run();

  return c.json({ ok: true });
});

function avatarToResponse(row: AvatarRow, account: AuthAccount): CloudAvatarResponse {
  let permissions: string[] = [];
  try { permissions = JSON.parse(row.permissions); } catch { /* empty */ }
  return {
    id: row.id,
    name: row.name,
    description: row.description,
    version: row.version,
    visibility: row.visibility,
    share_id: row.share_id || '',
    package_hash: row.package_hash || '',
    permissions,
    license: row.license,
    owner: {
      uuid: row.owner_uuid,
      username: account.username,
      creator_id: row.owner_uuid,
    },
  };
}

export default avatars;
