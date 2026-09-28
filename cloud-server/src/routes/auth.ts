import { Hono } from 'hono';
import type { Env, AuthAccount, ChallengeRow } from '../types';
import { CHALLENGE_TTL_MS, SESSION_TTL_MS } from '../types';
import { requireAuth } from '../middleware';

type Variables = { account: AuthAccount };
const auth = new Hono<{ Bindings: Env; Variables: Variables }>();

// POST /v1/auth/challenges
auth.post('/challenges', async (c) => {
  const body = await c.req.json<{ username?: string }>();
  const username = body?.username?.trim();
  if (!username || username.length < 1 || username.length > 16) {
    return c.json({ error: 'Invalid username' }, 400);
  }

  const challengeId = crypto.randomUUID();
  const serverId = generateServerId();
  const now = Date.now();
  const expiresAt = now + CHALLENGE_TTL_MS;

  // Clean up expired challenges
  await c.env.DB.prepare(
    'DELETE FROM auth_challenges WHERE expires_at < ?'
  )
    .bind(now)
    .run();

  await c.env.DB.prepare(
    `INSERT INTO auth_challenges
     (challenge_id, server_id, username, created_at, expires_at)
     VALUES (?, ?, ?, ?, ?)`
  )
    .bind(challengeId, serverId, username, now, expiresAt)
    .run();

  return c.json({ challenge_id: challengeId, server_id: serverId });
});

// POST /v1/auth/verify
auth.post('/verify', async (c) => {
  const body = await c.req.json<{
    challenge_id?: string;
    client_mojang_response?: {
      id?: string;
      name?: string;
      properties?: Array<{
        name: string;
        value: string;
        signature?: string;
      }>;
    };
  }>();

  const challengeId = body?.challenge_id;
  if (!challengeId) return c.json({ error: 'Missing challenge_id' }, 400);

  const now = Date.now();
  const challenge = await c.env.DB.prepare(
    'SELECT * FROM auth_challenges WHERE challenge_id = ? AND expires_at > ?'
  )
    .bind(challengeId, now)
    .first<ChallengeRow>();

  if (!challenge) {
    return c.json({ error: 'Challenge expired or invalid' }, 400);
  }

  // Delete used challenge immediately
  await c.env.DB.prepare(
    'DELETE FROM auth_challenges WHERE challenge_id = ?'
  )
    .bind(challengeId)
    .run();

  // Try client-provided Mojang response first, then server-side
  let playerUuid: string | null = null;
  let playerName: string | null = null;

  const clientResp = body?.client_mojang_response;
  if (clientResp?.id && clientResp?.name) {
    if (
      clientResp.name.toLowerCase() !== challenge.username.toLowerCase()
    ) {
      return c.json(
        { error: 'Username mismatch in Mojang response' },
        403
      );
    }
    playerUuid = formatUuid(clientResp.id);
    playerName = clientResp.name;
  }

  // Fallback: server-side Mojang verification
  if (!playerUuid) {
    try {
      const mojangUrl = `${c.env.MOJANG_SESSION_URL}?username=${encodeURIComponent(challenge.username)}&serverId=${encodeURIComponent(challenge.server_id)}`;
      const mojangResp = await fetch(mojangUrl, {
        headers: { Accept: 'application/json' },
        signal: AbortSignal.timeout(6000),
      });
      if (mojangResp.status === 200) {
        const mojang = (await mojangResp.json()) as {
          id?: string;
          name?: string;
        };
        if (mojang?.id && mojang?.name) {
          playerUuid = formatUuid(mojang.id);
          playerName = mojang.name;
        }
      }
    } catch {
      // Mojang verification failed — client_mojang_response is required
    }
  }

  if (!playerUuid || !playerName) {
    return c.json(
      { error: 'Minecraft account verification failed' },
      403
    );
  }

  // Upsert account
  await c.env.DB.prepare(
    `INSERT INTO accounts (uuid, username, created_at, last_login_at)
     VALUES (?, ?, ?, ?)
     ON CONFLICT(uuid) DO UPDATE SET
       username = excluded.username,
       last_login_at = excluded.last_login_at`
  )
    .bind(playerUuid, playerName, now, now)
    .run();

  // Create session token
  const token = generateToken();
  const expiresAt = now + SESSION_TTL_MS;
  await c.env.DB.prepare(
    `INSERT INTO sessions (token, account_uuid, created_at, expires_at)
     VALUES (?, ?, ?, ?)`
  )
    .bind(token, playerUuid, now, expiresAt)
    .run();

  // Clean up old sessions for this account (keep last 5)
  await c.env.DB.prepare(
    `DELETE FROM sessions WHERE account_uuid = ? AND token NOT IN
     (SELECT token FROM sessions WHERE account_uuid = ?
      ORDER BY created_at DESC LIMIT 5)`
  )
    .bind(playerUuid, playerUuid)
    .run();

  return c.json({
    token,
    expires_at: expiresAt,
    account: { uuid: playerUuid, username: playerName },
  });
});

// DELETE /v1/auth/session
auth.delete('/session', requireAuth, async (c) => {
  const header = c.req.header('Authorization');
  const token = header!.slice(7);
  await c.env.DB.prepare('DELETE FROM sessions WHERE token = ?')
    .bind(token)
    .run();
  return c.json({ ok: true });
});

function generateServerId(): string {
  const bytes = new Uint8Array(20);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join(
    ''
  );
}

function generateToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join(
    ''
  );
}

function formatUuid(raw: string): string {
  const hex = raw.replace(/-/g, '').toLowerCase();
  if (hex.length !== 32) return raw;
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

export default auth;
