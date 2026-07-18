# Security policy

## Multiplayer trust model

Shyne treats the server as authoritative for skills, damage, cooldowns, profiles, equipment, projectiles, summons, and world changes. Client Lua may control local presentation, but the server must validate every state-changing request.

Avatar synchronization currently enforces:

- exact mod-version and protocol-version negotiation before synchronization;
- a five-second handshake timeout for missing clients;
- one snapshot per packet and rate limiting;
- bounded JSON, texture, model, part, and synchronized-variable sizes;
- PNG signature and SHA-256 verification;
- server-owned player identity and remote model identifiers;
- capability checks before custom payloads are sent.

Texture data is sent once with a full snapshot. Later updates are state-only deltas, and the server retains the latest full snapshot for newly joined players.

## Reporting

Do not publish exploit details in a public issue. Contact the repository owner with reproduction steps, affected version, and relevant logs.
