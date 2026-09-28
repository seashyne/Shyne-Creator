import { Hono } from 'hono';
import { cors } from 'hono/cors';
import type { Env, AuthAccount } from './types';
import auth from './routes/auth';
import avatars from './routes/avatars';
import uploads from './routes/uploads';
import shares from './routes/shares';
import releases from './routes/releases';

type Variables = { account: AuthAccount };
const app = new Hono<{ Bindings: Env; Variables: Variables }>();

// Global CORS — allows Minecraft mod HTTP client and any web UI
app.use(
  '*',
  cors({
    origin: '*',
    allowHeaders: [
      'Content-Type',
      'Authorization',
      'Accept',
      'X-Shyne-Creator-Version',
      'X-Shyne-Permissions',
    ],
    allowMethods: ['GET', 'POST', 'PUT', 'DELETE', 'OPTIONS'],
    maxAge: 86400,
  })
);

// Health check
app.get('/', (c) =>
  c.json({ service: 'Shyne Cloud', version: '1.0.0', status: 'ok' })
);
app.get('/health', (c) => c.json({ status: 'ok' }));

// Mount route groups
app.route('/v1/auth', auth);
app.route('/v1', avatars);
app.route('/v1', uploads);
app.route('/v1', shares);
app.route('/', releases);

// Global error handler
app.onError((err, c) => {
  console.error('[ShyneCloud Error]', err.message, err.stack);
  const status = 'status' in err ? (err as { status: number }).status : 500;
  return c.json(
    { error: err.message || 'Internal server error' },
    status >= 400 && status < 600 ? (status as 400) : 500
  );
});

// 404 fallback
app.notFound((c) => c.json({ error: 'Not found' }, 404));

export default app;
