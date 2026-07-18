# Shyne Secure Public Share (.sc v2)

Public Share is separate from Private Cloud Backup. Local development and private restore continue to use normal Avatar folders. Only a published copy becomes a `.sc` container.

## Security model

- Avatar files are ZIP-compressed, then encrypted with a random AES-256-GCM data key.
- The `.sc` header and ciphertext are signed with the Shyne Cloud Ed25519 key.
- The data key is wrapped by a Cloud-only master key and is never stored inside the `.sc` file.
- Opening Public Share requires Minecraft sign-in and a signed, account-bound 15-minute lease.
- Unpublishing deletes the public package and blocks new leases. An already active Avatar stops when its current lease expires.
- Public Share Lua receives only capabilities declared in `avatar.json` and approved by the user for the exact package hash.
- `particle` and `sound` are low-risk capabilities; `camera`, `microphone`, and `command` are marked dangerous and default to denied.
- Updating the package hash invalidates the previous permission decision and requires a new review.
- Decrypted files exist only in `.shyne-cache/public-runtime` while used and are cleared on the next client start. Encrypted `.sc` files may remain cached.

This system prevents offline editing and casual redistribution through the official client, detects tampering, and gives creators revocation control. It cannot make client-side content impossible to capture: a sufficiently modified client can inspect data after it has legitimately received a decryption key.

## API

- `GET /v1/discover` — browse published metadata.
- `PUT /v1/avatars/{avatarId}/publication` — publish an authenticated ZIP payload as `.sc`.
- `DELETE /v1/avatars/{avatarId}/publication` — revoke and remove the public package.
- `GET /v1/shares/{shareId}` — read public metadata.
- `POST /v1/shares/{shareId}/lease` — obtain a signed short-lived lease and data key.
- `GET /v1/shares/{shareId}/package` — download the encrypted `.sc` package after sign-in.

Required Worker secrets are `SC_MASTER_KEY_B64` and `SC_SIGNING_KEY_PKCS8_B64`. Public key metadata is configured as `SC_KEY_ID` and `SC_SIGNING_PUBLIC_SPKI_B64`.
