import { Hono } from 'hono';
import type { Env, AuthAccount, AvatarRow, CloudAvatarResponse, CloudPageResponse } from '../types';
import { SAFE_SHARE_ID } from '../types';
import { requireAuth } from '../middleware';

type Variables = { account: AuthAccount };
const shares = new Hono<{ Bindings: Env; Variables: Variables }>();

// GET /v1/shares/:shareId — Public share metadata (no auth required)
shares.get('/shares/:shareId', async (c) => {
  const shareId = c.req.param('shareId')!;
  if (!SAFE_SHARE_ID.test(shareId)) return c.json({ error: 'Invalid share id' }, 400);

  const row = await c.env.DB.prepare(
    `SELECT a.*, acc.username FROM avatars a
     JOIN accounts acc ON acc.uuid = a.owner_uuid
     WHERE a.share_id = ? AND a.visibility = 'public'`
  ).bind(shareId).first<AvatarRow & { username: string }>();

  if (!row) return c.json({ error: 'Public Share not found' }, 404);

  return c.json(publicAvatarResponse(row));
});

// GET /v1/shares/:shareId/package — Download public share ZIP (auth required)
shares.get('/shares/:shareId/package', requireAuth, async (c) => {
  const shareId = c.req.param('shareId')!;
  if (!SAFE_SHARE_ID.test(shareId)) return c.json({ error: 'Invalid share id' }, 400);

  // Verify share exists and is public
  const row = await c.env.DB.prepare(
    `SELECT id FROM avatars WHERE share_id = ? AND visibility = 'public'`
  ).bind(shareId).first<{ id: string }>();
  if (!row) return c.json({ error: 'Public Share not found' }, 404);

  // Fetch ZIP from R2
  const r2Key = `shares/${shareId}/package.zip`;
  const object = await c.env.AVATAR_BUCKET.get(r2Key);
  if (!object) return c.json({ error: 'Public Avatar package not found' }, 404);

  return new Response(object.body, {
    status: 200,
    headers: {
      'Content-Type': 'application/vnd.shyne.avatar+zip',
      'Content-Length': String(object.size),
      'Cache-Control': 'private, max-age=300',
    },
  });
});

// GET /v1/discover — Browse/search public avatars (no auth required)
shares.get('/discover', async (c) => {
  const query = c.req.query('q') || '';
  const limit = Math.min(Math.max(1, parseInt(c.req.query('limit') || '30')), 100);
  const offset = Math.max(0, parseInt(c.req.query('offset') || '0'));

  let sql = `SELECT a.*, acc.username FROM avatars a
    JOIN accounts acc ON acc.uuid = a.owner_uuid
    WHERE a.visibility = 'public' AND a.share_id IS NOT NULL`;
  const params: (string | number)[] = [];

  if (query.trim()) {
    sql += ' AND (a.name LIKE ? OR a.id LIKE ? OR a.description LIKE ?)';
    const like = `%${query.trim()}%`;
    params.push(like, like, like);
  }
  sql += ' ORDER BY a.updated_at DESC LIMIT ? OFFSET ?';
  params.push(limit + 1, offset);

  const { results } = await c.env.DB.prepare(sql).bind(...params)
    .all<AvatarRow & { username: string }>();
  const hasMore = results.length > limit;
  const items = results.slice(0, limit);

  const response: CloudPageResponse = {
    items: items.map(publicAvatarResponse),
    next_offset: hasMore ? offset + limit : null,
  };
  return c.json(response);
});

function publicAvatarResponse(row: AvatarRow & { username: string }): CloudAvatarResponse {
  let permissions: string[] = [];
  try { permissions = JSON.parse(row.permissions); } catch { /* empty */ }
  return {
    id: row.id,
    name: row.name,
    description: row.description,
    version: row.version,
    visibility: 'public',
    share_id: row.share_id || '',
    package_hash: row.package_hash || '',
    permissions,
    license: row.license,
    owner: {
      uuid: row.owner_uuid,
      username: row.username,
      creator_id: row.owner_uuid,
    },
  };
}

export default shares;
