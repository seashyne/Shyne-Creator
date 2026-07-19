# Shyne Secure Public Share (.sc v2)

Public Share is separate from Private Cloud Backup. Local development and private restore continue to use normal Avatar folders. Only a published copy becomes a `.sc` container.

## Security model

- Avatar files are ZIP-compressed, then encrypted with a random AES-256-GCM data key.
- The `.sc` header and ciphertext are signed with the Shyne Cloud Ed25519 key.
- The data key is wrapped by a Cloud-only master key and is never stored inside the `.sc` file.
- Opening Public Share requires Minecraft sign-in and a signed, account-bound 15-minute lease.
- Unpublishing deletes the public package and blocks new leases. An already active Avatar stops when its current lease expires.
- Public Share Lua receives only capabilities declared in `avatar.json` and approved by the user for the exact package hash.
- `particle`, `sound`, and `world_render` are low-risk capabilities. `camera`, `microphone`, `command`, and `hud_render` are dangerous and default to denied.
- Updating the package hash invalidates the previous permission decision and requires a new review.
- The lease data key is wrapped for the current device with an ephemeral X25519 key agreement; the response does not contain a directly usable plaintext key.
- Public Avatar files are decrypted into the in-memory runtime filesystem. Only encrypted `.sc` packages may remain in the disk cache.
- Render tasks are bounded to 128 tasks and 4096 line points per frame; distant/off-screen world tasks are culled.

This system prevents offline editing and casual redistribution through the official client, detects tampering, and gives creators revocation control. It cannot make client-side content impossible to capture: a sufficiently modified client can inspect data after it has legitimately received a decryption key.

## API

- `GET /v1/discover` — browse published metadata.
- `PUT /v1/avatars/{avatarId}/publication` — publish an authenticated ZIP payload as `.sc`.
- `DELETE /v1/avatars/{avatarId}/publication` — revoke and remove the public package.
- `GET /v1/shares/{shareId}` — read public metadata.
- `POST /v1/shares/{shareId}/lease` — obtain a signed short-lived lease and a device-wrapped data key.
- `GET /v1/shares/{shareId}/package` — download the encrypted `.sc` package after sign-in.

The status response advertises `public_permissions_v2` and `public_avatar_permissions`. The current permission contract is `particle`, `sound`, `camera`, `microphone`, `command`, `hud_render`, and `world_render`.

Required Worker secrets are `SC_MASTER_KEY_B64` and `SC_ACTIVE_SIGNING_KEY_PKCS8_B64`. Public signing keys are configured through `SC_ACTIVE_KEY_ID` and `SC_PUBLIC_KEYS_JSON` so old `.sc` signatures remain verifiable during key rotation.
