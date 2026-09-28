import { Hono } from 'hono';
import type { Env, AuthAccount, UploadRow } from '../types';
import { SAFE_HASH, SAFE_ID, CHUNK_BYTES } from '../types';
import { requireAuth } from '../middleware';

type Variables = { account: AuthAccount };
const uploads = new Hono<{ Bindings: Env; Variables: Variables }>();

// PUT /v1/uploads/:uploadId/chunks/:hash — Upload a binary chunk
uploads.put('/uploads/:uploadId/chunks/:hash', requireAuth, async (c) => {
  const account = c.get('account');
  const uploadId = c.req.param('uploadId')!;
  const hash = c.req.param('hash')!;

  if (!SAFE_HASH.test(hash)) return c.json({ error: 'Invalid chunk hash' }, 400);

  const now = Date.now();
  const upload = await c.env.DB.prepare(
    'SELECT * FROM uploads WHERE upload_id = ? AND owner_uuid = ? AND expires_at > ?'
  ).bind(uploadId, account.uuid, now).first<UploadRow>();

  if (!upload) return c.json({ error: 'Upload not found or expired' }, 404);

  // Verify this hash is expected
  const expectedChunks: string[] = JSON.parse(upload.expected_chunks);
  if (!expectedChunks.includes(hash)) {
    return c.json({ error: 'Unexpected chunk hash' }, 400);
  }

  // Read binary body
  const body = await c.req.arrayBuffer();
  if (body.byteLength === 0 || body.byteLength > CHUNK_BYTES) {
    return c.json({ error: 'Chunk size must be 1-' + CHUNK_BYTES + ' bytes' }, 400);
  }

  // Verify hash matches content
  const computedBuffer = await crypto.subtle.digest('SHA-256', body);
  const computedHash = Array.from(new Uint8Array(computedBuffer))
    .map((b) => b.toString(16).padStart(2, '0')).join('');

  if (computedHash !== hash) {
    return c.json({ error: 'Chunk content does not match hash' }, 400);
  }

  // Store chunk in R2
  const r2Key = `chunks/${account.uuid}/${upload.avatar_id}/${hash}`;
  await c.env.AVATAR_BUCKET.put(r2Key, body, {
    httpMetadata: { contentType: 'application/octet-stream' },
    customMetadata: { avatarId: upload.avatar_id, ownerUuid: account.uuid },
  });

  // Record chunk in D1
  await c.env.DB.prepare(
    `INSERT INTO avatar_chunks (hash, avatar_id, owner_uuid, size, created_at)
     VALUES (?, ?, ?, ?, ?) ON CONFLICT(hash, avatar_id, owner_uuid) DO NOTHING`
  ).bind(hash, upload.avatar_id, account.uuid, body.byteLength, now).run();

  // Update received chunks list
  const receivedChunks: string[] = JSON.parse(upload.received_chunks);
  if (!receivedChunks.includes(hash)) {
    receivedChunks.push(hash);
    await c.env.DB.prepare(
      'UPDATE uploads SET received_chunks = ? WHERE upload_id = ?'
    ).bind(JSON.stringify(receivedChunks), uploadId).run();
  }

  return c.json({ ok: true });
});

// POST /v1/uploads/:uploadId/complete — Finalize upload
uploads.post('/uploads/:uploadId/complete', requireAuth, async (c) => {
  const account = c.get('account');
  const uploadId = c.req.param('uploadId')!;

  const now = Date.now();
  const upload = await c.env.DB.prepare(
    'SELECT * FROM uploads WHERE upload_id = ? AND owner_uuid = ? AND expires_at > ?'
  ).bind(uploadId, account.uuid, now).first<UploadRow>();

  if (!upload) return c.json({ error: 'Upload not found or expired' }, 404);

  // Verify all chunks received
  const expected: string[] = JSON.parse(upload.expected_chunks);
  const received: string[] = JSON.parse(upload.received_chunks);
  const missing = expected.filter((h) => !received.includes(h));
  if (missing.length > 0) {
    return c.json(
      { error: 'Missing chunks: ' + missing.length, missing_chunks: missing },
      400
    );
  }

  // Parse manifest for avatar metadata
  const manifest = JSON.parse(upload.manifest);

  // Upsert avatar record
  await c.env.DB.prepare(
    `INSERT INTO avatars
     (id, owner_uuid, name, version, description, total_size, manifest, created_at, updated_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
     ON CONFLICT(id, owner_uuid) DO UPDATE SET
       name = excluded.name,
       version = excluded.version,
       description = excluded.description,
       total_size = excluded.total_size,
       manifest = excluded.manifest,
       updated_at = excluded.updated_at`
  ).bind(
    upload.avatar_id, account.uuid,
    manifest.name || upload.avatar_id,
    manifest.version || '1.0.0',
    manifest.description || '',
    manifest.total_size || 0,
    upload.manifest, now, now
  ).run();

  // Delete upload record
  await c.env.DB.prepare('DELETE FROM uploads WHERE upload_id = ?')
    .bind(uploadId).run();

  return c.json({ avatar_id: upload.avatar_id, status: 'complete' });
});

// GET /v1/chunks/:hash — Download a chunk by SHA-256 hash
uploads.get('/chunks/:hash', requireAuth, async (c) => {
  const account = c.get('account');
  const hash = c.req.param('hash')!;
  const avatarId = c.req.query('avatar') || '';

  if (!SAFE_HASH.test(hash)) return c.json({ error: 'Invalid chunk hash' }, 400);
  if (!SAFE_ID.test(avatarId)) return c.json({ error: 'Invalid Avatar id' }, 400);

  // Verify ownership
  const chunkRow = await c.env.DB.prepare(
    'SELECT size FROM avatar_chunks WHERE hash = ? AND avatar_id = ? AND owner_uuid = ?'
  ).bind(hash, avatarId, account.uuid).first<{ size: number }>();

  if (!chunkRow) return c.json({ error: 'Chunk not found' }, 404);

  // Fetch from R2
  const r2Key = `chunks/${account.uuid}/${avatarId}/${hash}`;
  const object = await c.env.AVATAR_BUCKET.get(r2Key);
  if (!object) return c.json({ error: 'Chunk data not found in storage' }, 404);

  return new Response(object.body, {
    status: 200,
    headers: {
      'Content-Type': 'application/octet-stream',
      'Content-Length': String(chunkRow.size),
      'Cache-Control': 'private, immutable, max-age=86400',
    },
  });
});

export default uploads;
