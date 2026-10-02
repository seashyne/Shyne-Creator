import { Hono } from 'hono';
import type { Env, AuthAccount } from '../types';

type Variables = { account: AuthAccount };
const releases = new Hono<{ Bindings: Env; Variables: Variables }>();

const LATEST_RELEASE = {
  version: '2.12.0',
  minecraft: '26.3',
  title: 'Shyne Creator v2.12.0 — Figura Compatibility APIs & Cloudflare R2 Sync',
  published_at: new Date().toISOString(),
  files: {
    fabric: {
      filename: 'shyne-creator-fabric-2.12.0.jar',
      loader: 'Fabric',
      size: 3805320,
      sha256: '7f2dde84c8f1cb18f10912e91ad6b9cb64b82e06412d425937f1cd7ced3dd122',
      download_url: 'https://shyne-avatar-cloud.jirayut-wh.workers.dev/releases/shyne-creator-fabric-2.12.0.jar',
    },
    neoforge: {
      filename: 'shyne-creator-neoforge-2.12.0.jar',
      loader: 'NeoForge',
      size: 3784987,
      sha256: 'a8dbbb7cbb6330c9f249f72a584a99aa339ba7eaa6ead1a4db6b78f5929d2e42',
      download_url: 'https://shyne-avatar-cloud.jirayut-wh.workers.dev/releases/shyne-creator-neoforge-2.12.0.jar',
    },
    kit: {
      filename: 'Shyne-Creator-Kit-2.12.0.zip',
      type: 'Creator Tools & Documentation',
      download_url: 'https://shyne-avatar-cloud.jirayut-wh.workers.dev/releases/Shyne-Creator-Kit-2.12.0.zip',
    },
  },
};

// GET /v1/releases/latest or /releases/latest — release metadata and direct links
releases.get('/latest', (c) => c.json(LATEST_RELEASE));
releases.get('/v1/releases/latest', (c) => c.json(LATEST_RELEASE));

// GET /releases/:filename — Direct file download from Cloudflare R2
releases.get('/releases/:filename', async (c) => {
  const filename = c.req.param('filename')!;
  // Sanitize filename
  if (!/^[a-zA-Z0-9_.-]+$/.test(filename)) {
    return c.json({ error: 'Invalid filename' }, 400);
  }

  const r2Key = `releases/${filename}`;
  const object = await c.env.AVATAR_BUCKET.get(r2Key);
  if (!object) {
    return c.json({ error: `File not found: ${filename}` }, 404);
  }

  const contentType = filename.endsWith('.jar')
    ? 'application/java-archive'
    : filename.endsWith('.zip')
    ? 'application/zip'
    : 'application/octet-stream';

  return new Response(object.body, {
    status: 200,
    headers: {
      'Content-Type': contentType,
      'Content-Disposition': `attachment; filename="${filename}"`,
      'Content-Length': String(object.size),
      'Cache-Control': 'public, max-age=86400, immutable',
    },
  });
});

export default releases;
