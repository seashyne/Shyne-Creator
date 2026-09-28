import test, { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import app from '../src/index';
import type { Env } from '../src/types';

// In-memory mock for D1 database
class MockD1PreparedStatement {
  private sql: string;
  private params: unknown[] = [];
  private db: MockD1Database;

  constructor(sql: string, db: MockD1Database) {
    this.sql = sql;
    this.db = db;
  }

  bind(...params: unknown[]) {
    this.params = params;
    return this;
  }

  async run() {
    return { success: true, meta: {} };
  }

  async first<T = Record<string, unknown>>(): Promise<T | null> {
    if (this.sql.includes('auth_challenges')) {
      const challengeId = this.params[0] as string;
      const c = this.db.challenges.get(challengeId);
      return (c as T) || null;
    }
    if (this.sql.includes('sessions')) {
      const token = this.params[0] as string;
      const s = this.db.sessions.get(token);
      return (s as T) || null;
    }
    if (this.sql.includes('FROM avatars')) {
      return null;
    }
    return null;
  }

  async all<T = Record<string, unknown>>(): Promise<{ results: T[] }> {
    if (this.sql.includes('FROM avatars')) {
      return { results: [] };
    }
    return { results: [] };
  }
}

class MockD1Database {
  challenges = new Map<string, any>();
  sessions = new Map<string, any>();
  accounts = new Map<string, any>();
  avatars = new Map<string, any>();
  uploads = new Map<string, any>();
  chunks = new Map<string, any>();

  prepare(sql: string) {
    return new MockD1PreparedStatement(sql, this);
  }
}

// In-memory mock for R2 bucket
class MockR2Bucket {
  storage = new Map<string, { body: ArrayBuffer; metadata?: any }>();

  async put(key: string, value: any, options?: any) {
    let buf: ArrayBuffer;
    if (value instanceof ArrayBuffer) {
      buf = value;
    } else if (typeof value === 'string') {
      buf = new TextEncoder().encode(value).buffer as ArrayBuffer;
    } else {
      buf = new ArrayBuffer(0);
    }
    this.storage.set(key, { body: buf, metadata: options?.customMetadata });
    return { key, size: buf.byteLength } as any;
  }

  async get(key: string) {
    const item = this.storage.get(key);
    if (!item) return null;
    return {
      body: item.body,
      size: item.body.byteLength,
      customMetadata: item.metadata,
    } as any;
  }

  async delete(key: string) {
    this.storage.delete(key);
  }
}

function createTestEnv(): Env {
  return {
    DB: new MockD1Database() as unknown as D1Database,
    AVATAR_BUCKET: new MockR2Bucket() as unknown as R2Bucket,
    MOJANG_SESSION_URL: 'https://sessionserver.mojang.com/session/minecraft/hasJoined',
  };
}

describe('Shyne Cloud Server API Tests', () => {
  it('GET / returns service info and status ok', async () => {
    const env = createTestEnv();
    const res = await app.request('/', {}, env);
    assert.equal(res.status, 200);
    const body = await res.json() as any;
    assert.equal(body.service, 'Shyne Cloud');
    assert.equal(body.status, 'ok');
  });

  it('GET /health returns 200 ok', async () => {
    const env = createTestEnv();
    const res = await app.request('/health', {}, env);
    assert.equal(res.status, 200);
    const body = await res.json() as any;
    assert.equal(body.status, 'ok');
  });

  it('OPTIONS has CORS headers configured for mod client', async () => {
    const env = createTestEnv();
    const res = await app.request('/v1/me/avatars', { method: 'OPTIONS' }, env);
    assert.equal(res.headers.get('access-control-allow-origin'), '*');
  });

  it('POST /v1/auth/challenges creates challenge with server_id and challenge_id', async () => {
    const env = createTestEnv();
    const res = await app.request('/v1/auth/challenges', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'PlayerOne' }),
    }, env);

    assert.equal(res.status, 200);
    const body = await res.json() as any;
    assert.ok(body.challenge_id);
    assert.ok(body.server_id);
    assert.equal(typeof body.challenge_id, 'string');
    assert.equal(typeof body.server_id, 'string');
    assert.equal(body.server_id.length, 40); // 20 bytes in hex
  });

  it('POST /v1/auth/challenges rejects invalid usernames', async () => {
    const env = createTestEnv();
    const res = await app.request('/v1/auth/challenges', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: '' }),
    }, env);

    assert.equal(res.status, 400);
    const body = await res.json() as any;
    assert.equal(body.error, 'Invalid username');
  });

  it('GET /v1/me/avatars returns 401 when unauthenticated', async () => {
    const env = createTestEnv();
    const res = await app.request('/v1/me/avatars', {}, env);
    assert.equal(res.status, 401);
  });

  it('GET /v1/discover returns empty page for empty database', async () => {
    const env = createTestEnv();
    const res = await app.request('/v1/discover', {}, env);
    assert.equal(res.status, 200);
    const body = await res.json() as any;
    assert.deepEqual(body.items, []);
    assert.equal(body.next_offset, null);
  });

  it('GET /v1/shares/:shareId returns 404 for unknown share', async () => {
    const env = createTestEnv();
    const res = await app.request('/v1/shares/00000000-0000-0000-0000-000000000000', {}, env);
    assert.equal(res.status, 404);
  });

  it('GET /v1/shares/:shareId rejects invalid share id format', async () => {
    const env = createTestEnv();
    const res = await app.request('/v1/shares/invalid-share!', {}, env);
    assert.equal(res.status, 400);
  });

  it('GET /unknown-route returns 404 Not Found', async () => {
    const env = createTestEnv();
    const res = await app.request('/unknown-route', {}, env);
    assert.equal(res.status, 404);
  });
});
