# Security policy

## Multiplayer trust model

Shyne treats the server as authoritative for skills, damage, cooldowns, profiles, equipment, projectiles, summons, and world changes. Client Lua may control local presentation, but the server must validate every state-changing request.

Avatar synchronization currently enforces:

- exact mod-version and protocol-version negotiation before synchronization;
- a five-second handshake timeout for missing clients;
- one snapshot per packet and rate limiting;
- bounded JSON, texture, model, part, and synchronized-variable sizes;
- PNG signature, IHDR dimensions, pixel budget, and SHA-256 verification before decode;
- bounded nested Lua/synchronized values with cycle, depth, node, string, and sparse-index limits;
- revision acknowledgements before local dirty state or full-model retry state is cleared;
- server-owned player identity and remote model identifiers;
- capability checks before custom payloads are sent.

Texture data is sent with a full snapshot until its revision is acknowledged. Later updates are state-only deltas, and the server retains the latest validated full snapshot for newly joined players. Peer snapshot JSON is capped at 2 MiB; decoded PNG dimensions are capped at 4096 per side and 16,777,216 total pixels per model.

## Reporting

Do not publish exploit details in a public issue. Contact the repository owner with reproduction steps, affected version, and relevant logs.
