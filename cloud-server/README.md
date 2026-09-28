# Shyne Cloud Backend Server ☁️

Official backend Web API server for the **Shyne Creator** Minecraft mod (Fabric & NeoForge). Built on **Cloudflare Workers** with **Cloudflare R2** for object storage and **Cloudflare D1** (Serverless SQLite) for metadata.

---

## 🏗️ Architecture

```
┌───────────────────────────────────────────────┐
│     Minecraft Client (Fabric / NeoForge)      │
│           ShyneCloudClient.java               │
└───────────────────────┬───────────────────────┘
                        │ HTTPS (JSON / Octet-Stream / ZIP)
                        ▼
┌───────────────────────────────────────────────┐
│          Cloudflare Worker (Hono)             │
│        E:\...\cloud-server\src\index.ts        │
├───────────────────────┬───────────────────────┤
│    Cloudflare D1      │    Cloudflare R2      │
│  (Relational SQLite)  │   (Blob Storage)      │
│                       │                       │
│  - accounts           │  - chunks/            │
│  - sessions           │    {uuid}/{id}/{hash} │
│  - auth_challenges    │  - shares/            │
│  - avatars            │    {shareId}/         │
│  - uploads            │    package.zip        │
│  - avatar_chunks      │                       │
└───────────────────────┴───────────────────────┘
```

---

## 🚀 Features

- **Mojang Authentication**: Cryptographic challenge/verify flow using Mojang Session Server (`hasJoined`), with dual-check support (client residential IP bypass + server fallback).
- **Chunked Avatar Upload**: Large avatars (up to 64 MiB) split into 512 KiB chunks with SHA-256 integrity verification before entering R2 storage.
- **Deduplicated Storage**: Chunk-based content-addressed storage in Cloudflare R2.
- **Public Share Ecosystem**: One-click avatar sharing with permission negotiation and remote revocation.
- **Public Discovery**: Searchable avatar gallery with pagination.
- **Ultra-Fast & Serverless**: Sub-millisecond cold starts, zero idle costs, global edge routing via Cloudflare's network.

---

## 📋 API Endpoints

| Method | Endpoint | Auth | Description |
|---|---|---|---|
| `GET` | `/health` | No | Health check |
| `POST` | `/v1/auth/challenges` | No | Request Mojang auth challenge |
| `POST` | `/v1/auth/verify` | No | Verify Mojang auth & obtain session token |
| `DELETE` | `/v1/auth/session` | Yes | Invalidate session |
| `GET` | `/v1/me/avatars` | Yes | List user's backed-up avatars |
| `GET` | `/v1/avatars/:avatarId` | Yes | Get avatar detail & manifest |
| `POST` | `/v1/avatars` | Yes | Initiate chunked avatar upload |
| `PUT` | `/v1/uploads/:uploadId/chunks/:hash` | Yes | Upload 512 KiB chunk (SHA-256 validated) |
| `POST` | `/v1/uploads/:uploadId/complete` | Yes | Finalize upload & save avatar |
| `GET` | `/v1/chunks/:hash?avatar=:avatarId` | Yes | Download chunk from R2 |
| `PUT` | `/v1/avatars/:avatarId/publication` | Yes | Publish avatar as Public Share |
| `DELETE` | `/v1/avatars/:avatarId/publication` | Yes | Revoke public share |
| `GET` | `/v1/shares/:shareId` | No | Get public share metadata |
| `GET` | `/v1/shares/:shareId/package` | Yes | Download public share ZIP |
| `GET` | `/v1/discover` | No | Browse public avatars gallery |

---

## 🛠️ Quick Start (Local Development)

### 1. Prerequisites
- Node.js 20+
- Cloudflare Wrangler CLI (installed via devDependencies)

### 2. Install Dependencies
```bash
npm install
```

### 3. Initialize Local D1 Database
```bash
npm run db:init:local
```

### 4. Run Development Server
```bash
npm run dev
```
The server will start at `http://localhost:8787`.

### 5. Run Test Suite
```bash
npm test
npm run typecheck
```

---

## 🌐 Production Deployment

### 1. Login to Cloudflare
```bash
npx wrangler login
```

### 2. Create R2 Bucket
```bash
npx wrangler r2 bucket create shyne-avatars
```

### 3. Create D1 Database
```bash
npx wrangler d1 create shyne-cloud-db
```
Copy the generated `database_id` and replace `REPLACE_WITH_ACTUAL_ID` in `wrangler.toml`:
```toml
[[d1_databases]]
binding = "DB"
database_name = "shyne-cloud-db"
database_id = "<your-database-id-here>"
```

### 4. Apply Schema to Production Database
```bash
npm run db:init
```

### 5. Deploy Worker
```bash
npm run deploy
```

---

## ⚙️ Client Mod Configuration

In Minecraft, open `.minecraft/config/shyne-creator/client.json` or configure in the mod settings screen:
```json
{
  "cloudEnabled": true,
  "cloudEndpoint": "https://shyne-avatar-cloud.jirayut-wh.workers.dev"
}
```
For local testing, use:
```json
{
  "cloudEnabled": true,
  "cloudEndpoint": "http://localhost:8787"
}
```
*(Note: Loopback `http://localhost` is allowed by `ShyneCloudClient.java`; all other endpoints require `https://`)*
