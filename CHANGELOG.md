## Shyne Creator v2.12.7

### Modular Blockbench Renderer & Bilingual Documentation

- Split the oversized avatar renderer into focused `BbModelVanillaPose`, `BbModelPoseResolver`, `BbModelRigResolver`, `BbModelGeometryRenderer` and loader-specific `BbModelFirstPersonRenderer` modules.
- `BbModelEntityRenderer` is now an orchestration layer (319 lines Fabric / 323 lines NeoForge) that submits the avatar layer, publishes bone matrices and preserves its public first-person hook.
- Kept Fabric and NeoForge behavior in parity while retaining the shared Blockbench geometry path for avatar and native item presentation rendering.
- Added a permanent project rule: every new or modified explanatory comment, Javadoc, API note and Lua documentation must state the same intent in both English and Thai before release.

## Shyne Creator v2.12.6

### Creator 3D Item Presentation

- Added optional `presentation` to `item.schema.json`: a creator can bind a synced Blockbench model to an item without making a resource pack item model.
- Creator item models now render through Minecraft's native item pipeline in first/third person, GUI/inventory, ground and fixed display contexts, with per-item scale, offset and rotation.
- Added `BbModelItemRenderer` as the dedicated item-presentation module; `BbModelEntityRenderer` remains responsible for avatar rigs, attachment layers and first-person arms.
- Added the Aether Focus Blockbench presentation example and documented the complete package workflow.

## Shyne Creator v2.12.5

### Aether Power & Item Showcase

- Added `aether-showcase-pack`: an installable gameplay example with three mana-powered skills, three right-click creator items, and matching native PNG icons.
- The pack includes server-side Lua effects for a projectile, ward feedback, and three-bolt Starfall, so it exercises the full Power Deck → SkillExecutor → gameplay pack flow.
- Added the showcase to the README, Gameplay API, Asset Package guide, and Docs site examples.

## Shyne Creator v2.12.4

### Creator-Owned Script Canvas UI

- Added `ui.canvas` (UI API 1.2): a blank, native full-screen input surface for Avatar Lua with no forced Shyne frame, controls, theme or schema.
- Creator scripts can draw Canvas-only text, rectangles, outlines, sprites, items, blocks and lines, then define their own transparent button hitboxes and click callbacks.
- Added Canvas lifecycle (`open`, `close`, Escape behavior, pause option, `on_open`, `on_close`) and isolated canvas render surfaces so a custom screen never leaks into the normal HUD.
- Extended Custom Render API to 1.4 and added the complete `script-canvas-ui-avatar` example and documentation.

## Shyne Creator v2.12.3

### API Documentation, Contracts & Power UI

- Added `API_CONTRACTS_TH.md` as the public index for Avatar, Lua, Render, Rig, Gameplay, Asset Package and Cloud contracts.
- Updated Standard, Lua, Render, Rig, Gameplay, Creator SDK, Cloud, Public Share and complete API reference documentation to the 2.12.3 compatibility scope.
- Expanded Gameplay API documentation with server authority, skill/item/weapon schemas, item-use hook data, registry commands and native PNG icon behavior.
- Published Power & Asset Package 1.0 and the complete API reference in the Docs site navigation.
- Mana UI now stays hidden until an active Avatar has a selected skill that spends mana; removed the misleading fallback `100 / 100` from the Action Deck, HUD and ability wheel.

## Shyne Creator v2.12.0

### 🌐 Cloudflare R2 Ecosystem & Cloud Sync (Live Web API Backend)
- **Live Official Cloud Backend Service**: Deployed and fully operational at `https://shyne-avatar-cloud.jirayut-wh.workers.dev` via Cloudflare Workers global edge.
- **Cloudflare R2 Object Storage**: Fast, resilient, globally distributed chunk storage (`shyne-avatars`) for avatar files, binary chunks, and public share ZIPs with zero egress fees.
- **Cloudflare D1 Serverless Database**: Real-time SQLite metadata store (`shyne-cloud-db`) managing user accounts, sessions, avatar manifests, permissions, and upload states.
- **Secure Mojang Authentication**: Cryptographic joinServer/hasJoined challenge-response verification against Mojang Session Servers, featuring dual-check (client direct check + edge fallback) to bypass datacenter IP restrictions.
- **Chunked Content-Addressed Uploads**: Avatars up to 64 MiB split into 512 KiB chunks with SHA-256 integrity verification before entering R2 storage.
- **Public Avatar Share & Discover**: One-click in-game avatar sharing, public gallery search, remote permission negotiation, and dynamic revocation.
- **100% Out-of-the-Box Client Integration**: Pre-configured default endpoint in both Fabric and NeoForge client builds.

## Shyne Creator v2.11.0

### 🌟 New Features & Enhancements
- **Unified G-key Action Wheel**: Avatar actions, Avatar Manager, outfit selection, settings, and reload now share one radial UI with a 3D player preview. The wheel opens before an Avatar is selected; its preview can be toggled in Settings.
- **Server Admin Policy & Anti-Cheat System (`/shyne policy`, `/shyne antihack`)**:
  - **100% UNLIMITED & Open Freedom by Default**:
    - Removed all numerical caps out of the box (`max_damage`, `max_reach`, `max_projectiles`, `max_summons`, `max_casts_per_sec` default to `UNLIMITED`).
    - Full block damage, fire spread, PvP skills, friendly fire, and custom flight enabled by default.
    - Anti-Cheat subsystem is **Disabled by default (`false`)** and punishment defaults to `LOG_ONLY` to allow boundless creativity without intrusive action cancellations.
  - **Strict Input Validation & Sanitization**:
    - Setting custom limits via `/shyne policy set <key> <val>` now strictly validates inputs: only positive numbers (`> 0`) or keywords (`unlimited`, `none`, `off`, `-1`, `0`) are accepted.
    - Arbitrary negative numbers (such as `-1999`) are rejected with clear error guidance.
    - Status outputs and command feedback consistently display `UNLIMITED` instead of internal sentinel numbers.
  - **Smart In-Game Tab Completion**:
    - Contextual auto-suggestions when typing `/shyne policy set <key> <TAB>` (suggests `unlimited`, `true/false`, or punishment modes).
  - **Live Runtime Hot-Reload**:
    - Admin updates take effect instantly and persist cleanly to `config/shyne-creator/server_policy.json`.
- **Web Audio Stream API (`sound.stream` & `sounds:playStream`)**:
  - Non-blocking asynchronous streaming of external web audio (MP3/OGG) directly into the client.
  - Full control handle: `play()`, `pause()`, `stop(fade)`, `setVolume(v)`, `setPitch(p)`, `setPos(x, y, z)`.
  - Real-time audio-reactive metrics: `getLevel()`, `getPeak()`, `isBeat()`, `isPlaying()`, `isBuffering()`.
  - Securely managed by client permission system and server policy (`audio_streams`).
- **100% Figura Real-World Parity Suite**:
  - **Tier 1 - Client, Renderer, Raycast & Dynamic Textures (`61_figura_client_renderer.lua`)**:
    - `renderer`: Full control of camera and shadow radius with `setShadowRadius()`, `getShadowRadius()`, `setCameraPivot()`, `getCameraPivot()`, `setCameraPos()`, `getCameraPos()`, `setCameraRot()`, `getCameraRot()`, `setFOV()`, `getFOV()`, and all fluent aliases (`cameraPos`, `fov`, etc.).
    - `client`: `isFirstPerson()`, `getFPS()`, `isPaused()`, `isSingleplayer()`, `getMousePos()`, `getScaledWindowSize()`, `getViewer()`.
    - `raycast`: `raycast:block(start, end)`, `raycast:entity(start, end)`, `raycast:raycast(...)` with swept hitboxes.
    - `textures`: Dynamic runtime procedural canvas with `textures:newTexture()`, `getPixel()`, `setPixel()`, and `apply()`.
  - **Tier 2 - Model Lighting, Translucency & Animation Scrubbing**:
    - Part light override: `part:setLight(block, sky)`, `part:getLight()`, `part:clearLight()`.
    - Custom render types: `part:setRenderType("TRANSLUCENT" | "CUTOUT" | "SOLID")`, `part:getRenderType()` applied across third-person and first-person rendering.
    - Animation playback control: `anim:time(sec)`, `anim:setTime(sec)`, `anim:getTime()`, `anim:pause()`, `anim:resume()`, `anim:isPaused()`.
    - 3D Billboard tasks: `part:newText(id)` and `part:newSprite(id)`.
  - **Tier 3 - Item Stacks & Environmental World Queries (`62_figura_items_world.lua`)**:
    - `ItemStackProxy`: `getId()`, `getID()`, `getType()`, `getName()`, `getCount()`, `getDamage()`, `getMaxDamage()`, `hasGlint()`, `isEmpty()`, armor trims, and materials.
    - `player`: `getItem(slot)` (slots 1-6 or slot names), `getHeldItem(offhand)`.
    - `world`: `getBlockState(pos)`, `getBlockLight(pos)`, `getSkyLight(pos)`, `getLight(pos)`, `getRedstonePower(pos)`, `getBiome(pos)`, `getDayTime()`, `isRaining()`, `isThundering()`.
  - **Modular Action Wheel Subsystem (`63_figura_action_wheel.lua`)**:
    - Independent Action Wheel module supporting pages, titles, items, RGB / hover colors, toggle switches, and left/right click callbacks.
- **Granular Vanilla Model Replacement (`shyne.replace_vanilla` & `21_vanilla_model.lua`)**:
  - Replace or hide specific vanilla player parts without affecting other limbs (e.g. `shyne.replace_vanilla("arms")`, `shyne.replace_vanilla({ right_arm = true, left_arm = false })`, or `vanilla_model.RIGHT_ARM:hide()`).
  - Supports boolean, string, comma-separated tokens, arrays, and key-value tables.
- **Strict Codebase Covenant & Architectural Decomposition (`RULES.md`)**:
  - Enforced Single Responsibility Principle with all Java and Lua files strictly under 350-385 lines.
  - Decomposed `AvatarModelBridge` -> `AvatarModelHelper` and `AvatarWorldBridge` -> `AvatarProbeHelper`.
  - Decomposed Lua runtime into clean SRP submodules (`20_avatar_world`, `21_vanilla_model`, `60_figura_compat`, `61_figura_client_renderer`, `62_figura_items_world`, `63_figura_action_wheel`).
  - 100% Loader Parity guaranteed between Fabric and NeoForge.

## Shyne Creator v2.10.4-alpha-26.3

### 🌟 New Features & Enhancements
- **Direct `.zip` Avatar Support**:
  - Drop compressed `.zip` avatar packages directly into `.minecraft/shyne-mods/avatars/`.
  - Shyne automatically unpacks, caches, validates, and loads them into the Avatar Manager.
- **Modular Avatar Scripting (`require`)**:
  - Full modular programming support via safe sandboxed `require(...)`.
  - Easily organize large avatars into subfolders (`skills/`, `ui/`, `weapons/`, `config.lua`) with circular dependency protection and recursive hot-reload.
- **Player Physics & Velocity API**:
  - Added `player:setVelocity(vx, vy, vz)` and `player:addVelocity(vx, vy, vz)` in both Shyne and Figura compatibility layers.
  - Enables physical Super Jumps, Anti-Gravity Hover/Levitation, and 3D Flight Dashing.
- **World Entities & Teammates Detection**:
  - Added `world.getPlayers(radius)` and `world.getEntities(radius)` to find nearby players, monsters, distances, and HP for co-op skills and targeted combat.
- **Real-Time Speed & Death Detection**:
  - Added `player:isAlive()` and instant speed queries (m/s) for sprint trails, stamina mechanics, and death/respawn handling.
- **Signature Weapon & Modular Showcases**:
  - Added `modular-signature-weapon-avatar` (packaged both as directory and `.zip`).
  - Added `custom-skill-hud-avatar` demonstrating custom keybinds and responsive 2D mana/energy HUDs.

## Shyne Creator v2.10.3-alpha-26.3

### 🌟 New Features & Enhancements
- **8-Slot Radial Action Wheel GUI**:
  - Open circular 8-slot Action Wheel anytime with Keybind `B` (configurable in Controls).
  - Supports mouse radial selection, quick numbers `1-8`, mouse wheel scroll for page switching, and visual toggle badges.
  - Fully compatible with both Figura `action_wheel` API and Shyne native `ui.action` API.
- **Custom Sound Player**:
  - Play custom `.ogg` sounds directly from the avatar's `sounds/` folder via `sounds:playSound("name")` without needing a resource pack.
- **First-Person Custom Arm**:
  - Dynamic 3D Blockbench model arm rendering in first-person camera mode.
- **Procedural Bone Physics**:
  - Enhanced spring and sway physics controller for ears, tails, and hair with real-time toggle support.
- **Modularized Avatar Runtime**:
  - Split runtime into dedicated SRP components (`bridge/`, `runtime/`, `sound/`) for maximum stability and performance.
- **Showcase Avatar**:
  - Added full working example `action-wheel-showcase-avatar` in `tools/examples/`.
