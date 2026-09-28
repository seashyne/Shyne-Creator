import { Context, Next } from 'hono';
import type { Env, AuthAccount, SessionRow } from './types';

type Variables = { account: AuthAccount };

export async function requireAuth(
  c: Context<{ Bindings: Env; Variables: Variables }>,
  next: Next
) {
  const header = c.req.header('Authorization');
  if (!header || !header.startsWith('Bearer ')) {
    return c.json({ error: 'Authentication required' }, 401);
  }
  const token = header.slice(7);
  if (!token || token.length < 16) {
    return c.json({ error: 'Invalid token' }, 401);
  }
  const now = Date.now();
  const row = await c.env.DB.prepare(
    `SELECT s.account_uuid, s.expires_at, a.username
     FROM sessions s JOIN accounts a ON a.uuid = s.account_uuid
     WHERE s.token = ?`
  )
    .bind(token)
    .first<SessionRow & { username: string }>();
  if (!row || row.expires_at <= now) {
    return c.json({ error: 'Session expired; sign in again' }, 401);
  }
  c.set('account', { uuid: row.account_uuid, username: row.username });
  await next();
}
