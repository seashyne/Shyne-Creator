## Shyne Creator v2.14.1

### Model Parity & Vanilla Visibility Fixes (Figura Compatibility)

- **Fixed Vanilla Player Overlap Bug**: Resolved an issue where calling `vanilla_model.ARMOR:setVisible(false)`, `CAPE`, or `ELYTRA` erroneously re-enabled `vanilla_model.PLAYER = true`. `vanilla_proxy:visible` now only modifies the target layer without touching base player visibility.
- **Separated Player Body Rig from Independent Layers**: Added `VanillaVisibilityKeys.isPlayerBodyPart` to decouple body parts (`HEAD`, `BODY`, `ARMS`, `LEGS`) from independent layers (`ARMOR`, `CAPE`, `ELYTRA`, `HELMET_ITEM`, `HELD_ITEMS`). Hiding `PLAYER` only suppresses the player's physical body parts, matching Figura standard behavior.
- **Added `HELMET_ITEM` Alias**: Normalized `HELMET_ITEM` and `helmet-item` to `HEAD_ITEM` in `VanillaVisibilityKeys` and Lua runtime `PART_GROUPS`.
- **Special Figura Bone Defaults (`Skull` & `Portrait`)**: Bones named `Skull` and `Portrait` (case-insensitive) are now automatically hidden by default in normal entity rendering, preventing secondary skull models/hair duplicates from rendering at the player's feet.
- **First-Person Subtree Detection**: Enhanced first-person bone filtering to recognize `fpModel`, `firstpersonmodel`, and `RightArmFirstP` hierarchies in third-person view, and fixed `renderer:isFirstPerson()` default state.
- แก้ไขปัญหาโมเดล Vanilla ซ้อนทับเมื่อมีการสั่งซ่อนเกราะ, ผ้าคลุม หรือปีกในสคริปต์
- แยกชิ้นส่วนร่างกายของโมเดลผู้เล่นออกจากเลเยอร์อิสระ (เกราะ, ปีก, ผ้าคลุม, ไอเทม) ให้ตรงกับมาตรฐาน Figura
- ซ่อนกระดูกพิเศษ `Skull` และ `Portrait` โดยอัตโนมัติบนโมเดลผู้เล่น แก้ปัญหาชิ้นส่วนหัวสำรองโผล่ซ้อนอยู่ที่เท้า
- รองรับคีย์ `vanilla_model.HELMET_ITEM` และปรับปรุงการตรวจจับกรุ๊ปโมเดลบุคคลที่หนึ่ง `fpModel`

## Shyne Creator v2.14.0

### Dual-Mode In-Memory & Physical Texture Architecture (Figura Compatibility)

- Added native In-Memory texture loading directly from Base64 data embedded inside `.bbmodel` files without requiring disk extraction.
- Completely resolves `texture_outside_pack` / `texture_missing` errors when loading models created in Blockbench or Figura avatars with external machine file paths (e.g., OneDrive / absolute paths).
- Implemented Dual-Mode priority: physical files placed in the avatar pack take precedence for live editing and hot-reloading, while models with embedded Base64 run seamlessly in RAM with zero disk footprints or folder pollution.
- Integrated in-memory texture validation into `AvatarValidator` (`validatePngBytes`) and dynamic texture registration into Minecraft's `TextureManager` via `BbModelTextures`.
- Supported multiplayer synchronization for in-memory embedded textures in `ShyneNetworkValidator`.
- รองรับการโหลด Texture แบบ In-Memory โดยตรงจาก Base64 ที่ฝังใน `.bbmodel` โดยไม่ต้องแตกไฟล์ลงดิสก์ แก้ปัญหาโมเดลจาก Figura หรือ Blockbench ที่อ้างอิง path ภายนอกเครื่อง (เช่น OneDrive)
- สถาปัตยกรรม Dual-Mode: ไฟล์จริงในโฟลเดอร์มีสิทธิ์สูงกว่าเพื่อรองรับ Hot-reloading และการแก้ไขสด ขณะที่โมเดลเสมือนสามารถทำงานใน RAM ได้ 100%

### Refactoring & Modular Architecture

- Refactored `BbModelParser.java` from ~680 lines down to ~180 lines, delegating domain responsibilities to specialized components:
  - `BbTextureResolver`: Base64 embedded decoding, relative path safety, canvas sizing, and standalone extraction utilities.
  - `BbGeometryParser`: Cubes, faces UV mapping, and free polygon meshes.
  - `BbAnimationParser`: Keyframes, animation channels, math expressions, and Blockbench v5 axis migration.
  - `BbModelJsonHelper`: JSON vector and numerical coercion utilities.
- ปรับโครงสร้างแยกโมดูล `BbModelParser.java` ให้สั้น กระชับ และอ่านง่ายตามหลัก Single Responsibility

## Shyne Creator v2.13.0


### P1 — Event Parity (Fabric & NeoForge)

- Added real client input events for avatar Lua: keyboard press/release/repeat, character input, mouse press/release, and scroll via shared `AvatarRawInputMixin`.
- Added item use lifecycle events (`events.USE_ITEM` / `events.ITEM_USE`) tracking mainhand/offhand usage states.
- Added chat message receive hooks (`events.CHAT_RECEIVE_MESSAGE` / `events.CHAT_RECEIVE`) via `AvatarChatMixin`.
- Added living entity combat hooks: damage reception (`events.DAMAGE` / `events.ENTITY_DAMAGE`), projectile collision (`events.ARROW_HIT`), and totem resurrection (`events.TOTEM` / `events.TOTEM_POP`) via `AvatarLivingEntityMixin`.
- เพิ่ม event input จาก client จริงให้ Avatar Lua: กด/ปล่อย/กดซ้ำคีย์บอร์ด, พิมพ์ตัวอักษร, กด/ปล่อยเมาส์ และเลื่อนเมาส์ โดย common mixin ทำให้ Fabric กับ NeoForge ทำงานเหมือนกัน
- เพิ่ม event การใช้งานไอเทม (`events.USE_ITEM` / `events.ITEM_USE`) ตรวจจับทั้งมือหลักและมือรอง
- เพิ่ม event การรับข้อความแชท (`events.CHAT_RECEIVE_MESSAGE` / `events.CHAT_RECEIVE`) ผ่าน `AvatarChatMixin`
- เพิ่ม event การต่อสู้ของสิ่งมีชีวิต: การรับดาเมจ (`events.DAMAGE` / `events.ENTITY_DAMAGE`), ลูกธนู/กระสุนพุ่งชน (`events.ARROW_HIT`) และโทเท็มแตก (`events.TOTEM` / `events.TOTEM_POP`) ผ่าน `AvatarLivingEntityMixin`

### P2 — Model Presentation & Dynamic Texture Materials

- Textures created via `textures:newTexture()` can call `:bindToModel(target)` (or `textures:bindToModel(texture, target)`) to replace local Blockbench materials at runtime.
- Added `AvatarRenderContext` with specialized presentation modes:
  - `GUI_PORTRAIT`: Renders avatar character bust portraits in GUIs and radial menus.
  - `SKULL`: Replaces player skull blocks and inventory items with custom avatar head geometry via `AvatarSkullPresentationMixin`.
  - `HELD_ITEM`: Renders custom geometry when models or parts are held in hand via `AvatarPresentationContextMixin`.
- texture ที่สร้างจาก `textures:newTexture()` เรียก `:bindToModel(target)` (หรือ `textures:bindToModel(texture, target)`) เพื่อแทน material Blockbench ของ Avatar ในเครื่องระหว่างรันได้
- เพิ่ม `AvatarRenderContext` รองรับบริบทการเรนเดอร์พิเศษ:
  - `GUI_PORTRAIT`: เรนเดอร์ภาพพอร์ตเทรตตัวละครในหน้าต่าง GUI และ Action Wheel
  - `SKULL`: นำหัวอวตารไปแสดงแทนบล็อกและไอเทม Player Skull ผ่าน `AvatarSkullPresentationMixin`
  - `HELD_ITEM`: เรนเดอร์เรขาคณิตโมเดลเมื่อถือไอเทมในมือผ่าน `AvatarPresentationContextMixin`

### P3 — Nameplate 2.0

- Added full Figura-style Nameplate facade: `nameplate.ENTITY`, `nameplate.CHAT`, and `nameplate.LIST`.
- Supported custom text styling (`:setText`), badges with custom hex color (`:setBadge`), and visibility control (`:setVisible`).
- Integrated styling into vanilla player entity overhead nameplates, chat sender prefixes, and player tab-list headers via `AvatarNameplateMixin`.
- Enforced permission gating using `AvatarPermission.NAMEPLATE`, `CHAT_NAMEPLATE`, and `TAB_LIST_NAMEPLATE`. Network protocol updated to `19`.
- เพิ่ม Facade ป้ายชื่อแบบ Figura ครบทั้ง 3 จุด: `nameplate.ENTITY`, `nameplate.CHAT`, และ `nameplate.LIST`
- รองรับการปรับแต่งข้อความ (`:setText`), ติดเข็มกลัดไอคอนพร้อมสี Hex (`:setBadge`) และเปิด/ปิดการมองเห็น (`:setVisible`)
- เชื่อมต่อการแสดงผลเข้ากับป้ายชื่อเหนือหัวตัวละคร, ชื่อในช่องแชท และรายชื่อผู้เล่น Tab List ผ่าน `AvatarNameplateMixin`
- ควบคุมความปลอดภัยด้วยสิทธิ์ `AvatarPermission.NAMEPLATE`, `CHAT_NAMEPLATE`, และ `TAB_LIST_NAMEPLATE` โดยปรับ Network Protocol เป็น `19`

### P4 — Safe Creator Data & Resource Storage

- Added safe, sandbox-scoped persistent data storage via `data:set(key, val)`, `data:get(key, default)`, and `data:save()`.
- Avatars write strictly to dedicated directories (`.shyne-data/avatars/<id>/`) with path containment, avoiding raw filesystem exposure.
- Integrated safe JSON encode/decode utilities and sandboxed asset/resource lookup preventing path traversal vulnerabilities.
- เพิ่มระบบจัดเก็บข้อมูลแบบ Persistent ในขอบเขตแซนด์บ็อกซ์ผ่าน `data:set(key, val)`, `data:get(key, default)` และ `data:save()`
- ข้อมูลถูกบันทึกแยกโฟลเดอร์ตาม Avatar ID อย่างปลอดภัย (`.shyne-data/avatars/<id>/`) ไม่เปิดให้เข้าถึง filesystem ดิบ
- มีระบบประมวลผล JSON ที่ปลอดภัย และระบบค้นหา Asset/Resource แบบจำกัดขอบเขต ป้องกันช่องโหว่ path traversal

### P5 — Permissions & Resource Budgets

- Introduced `AvatarPermissionScreen`: An interactive UI allowing users to inspect, approve, or revoke granular permissions per avatar.
- Added `AvatarQuotaManager` enforcing strict resource budgets:
  - Texture Quota: Limits dynamic texture counts and uncompressed memory footprint.
  - Render Task Quota: Enforces maximum draw calls and task queue limits.
  - Particle & Sound Rate Limits: Caps burst spawning and audio playback to prevent lag griefing.
- Upgraded `AvatarProfilerScreen` to display realtime quota consumption, instruction counts, and performance warnings.
- เพิ่มหน้าจอ `AvatarPermissionScreen`: UI ให้ผู้ใช้ตรวจสอบ อนุมัติ หรือระงับสิทธิ์การทำงานแต่ละด้านของอวตารได้ละเอียด
- เพิ่ม `AvatarQuotaManager` ควบคุมโควตาการใช้งานทรัพยากร:
  - โควตา Texture: จำกัดจำนวนและหน่วยความจำ texture
  - โควตา Render Task: จำกัดคิวคำสั่งวาดต่อวินาที
  - ควบคุม Particle & Sound: จำกัดอัตราการ spawn และเล่นเสียง ป้องกัน lag griefing
- อัปเกรด `AvatarProfilerScreen` แสดงผลการใช้โควตาแบบเรียลไทม์พร้อมการแจ้งเตือนเมื่อใช้งานเกินงบ

### P6 — Network API & Managed Packet Channels

- Enhanced `pings` API with typed payload schemas: `pings:define(id, {type1, type2, ...}, callback)` with automated schema validation.
- Added `AvatarChannelBridge` allowing avatars to exchange structured payloads through designated Shyne channels (`avatar:main`, `avatar:state`, `avatar:action`, `avatar:ping`).
- Gated networking behind server permission policies and bandwidth rate limiters, preventing arbitrary unmanaged socket or packet access.
- ยกระดับ `pings` รองรับ Typed Payload: `pings:define(id, {type1, type2, ...}, callback)` พร้อมการตรวจสอบประเภทข้อมูลอัตโนมัติ
- เพิ่ม `AvatarChannelBridge` ให้สคริปต์ส่งข้อมูลผ่านช่องทาง Shyne Packet Channel ที่กำหนด (`avatar:main`, `avatar:state`, `avatar:action`, `avatar:ping`)
- ป้องกันความปลอดภัยด้วย Server Policy และ Rate Limiter แทนการเปิดการเข้าถึง network packet แบบไร้ขอบเขต

### P7 — Creator Workflow Tools

- **Line-Precise Hot Reload**: Added `AvatarScriptErrorParser` and enhanced `AvatarFileWatcher` to broadcast instant chat messages and UI warnings with the exact file and line number on syntax/runtime failure.
- **Script & Permission Linter**: Added `AvatarScriptLinter` to preemptively detect compile errors, blocked sandboxed libraries (`io.*`, `os.*`, `package.*`), deprecated Figura APIs, and undeclared permissions.
- **Import Report**: Added `AvatarImporter` and `AvatarImportReport` to inspect `.bbmodel` structure, animations, and scripts, issuing compatibility grades (`A` to `D`) and actionable migration advice.
- **Project Starter Templates**: Added `AvatarTemplateManager` providing instant scaffold templates: `minimal`, `figura_compat`, and `action_wheel`.
- **Line-Precise Hot Reload**: เพิ่ม `AvatarScriptErrorParser` และอัปเกรด `AvatarFileWatcher` ให้แจ้งข้อผิดพลาดตรงบรรทัดในช่องแชทและ UI ทันทีที่บันทึกไฟล์สคริปต์
- **Script & Permission Linter**: เพิ่ม `AvatarScriptLinter` สแกน syntax error, ฟังก์ชันที่ถูกบล็อก (`io.*`, `os.*`), API เก่าของ Figura และสิทธิ์ที่ยังไม่ได้ประกาศล่วงหน้า
- **Import Report**: เพิ่ม `AvatarImporter` และ `AvatarImportReport` วิเคราะห์ไฟล์ `.bbmodel` และสคริปต์ ให้เกรดความเข้ากันได้ (`A` ถึง `D`) พร้อมคำแนะนำการแปลงอวตาร
- **Starter Templates**: เพิ่ม `AvatarTemplateManager` สร้างโครงสร้างโปรเจกต์พร้อมใช้ทันที: `minimal`, `figura_compat` และ `action_wheel`

### P8 — Polish & Compatibility Test Suite

- Published comprehensive `API_COMPATIBILITY_MATRIX.md` and `AvatarCompatibilityMatrix` cataloging all Figura and Shyne standard APIs with tier classifications (`FULL`, `RESTRICTED`, `PARTIAL`, `UNSUPPORTED`).
- Created `RealAvatarCompatibilityTest` executing end-to-end template generation, linter verification, script runtime execution, nameplates, persistent data, and typed ping synchronization.
- Verified 100% Loader Parity across Fabric Loom and NeoForge ModDev on Minecraft 26.3 with 178 passing test suites.
- จัดทำเอกสาร `API_COMPATIBILITY_MATRIX.md` และคลาส `AvatarCompatibilityMatrix` แสดงระดับความเข้ากันได้ของทุก API (`FULL`, `RESTRICTED`, `PARTIAL`, `UNSUPPORTED`)
- สร้างชุดทดสอบ `RealAvatarCompatibilityTest` ทดสอบกระบวนการทั้งหมดกับโมเดลและสคริปต์อวตารจริง
- ตรวจสอบความเท่าเทียม 100% ระหว่าง Fabric และ NeoForge บน Minecraft 26.3 โดยผ่านการทดสอบทั้ง 178 tests ครบถ้วน

### P9 — 100% API Compatibility & Full Figura Parity (0 Unsupported)

- Added `world.setBlock` and `world.setTime` with `AvatarPermission.WORLD_EDIT` gating.
- Added `host:sendChat` with anti-spam client rate limiting.
- Added `renderer:setPostShader` for vanilla post-processing shader effects.
- Added sandboxed `os` (`os.time`, `os.date`, `os.clock`, `os.getenv`) and sandboxed `io` (file write/read confined strictly to the avatar directory).
- Reorganized project documentation into structured `docs/` directories (`docs/api/`, `docs/guides/`, `docs/standards/`, `docs/architecture/`, `docs/legal/`).
- Verified 100% Loader Parity across Fabric Loom and NeoForge ModDev on Minecraft 26.3 with 180 passing test suites.
- ปลดล็อก API ทั้งหมดสู่ความเข้ากันได้ 100% (0 Unsupported): รองรับ `world.setBlock`, `world.setTime` ผ่านสิทธิ์ `world_edit`
- เพิ่ม `host:sendChat` พร้อมระบบป้องกันสแปมข้อความ
- เพิ่ม `renderer:setPostShader` เรียกใช้ Post-effect shader ของ Minecraft
- เพิ่มโมดูลแซนด์บ็อกซ์ `os` (`time`, `date`, `clock`, `getenv`) และ `io` (เขียน/อ่านไฟล์จำกัดเฉพาะโฟลเดอร์อวตาร)
- จัดระเบียบเอกสารทั้งหมดลงโฟลเดอร์ `docs/` เป็นหมวดหมู่ชัดเจน
- ผ่านการทดสอบ 180 test suites ครบทั้ง Fabric และ NeoForge บน Minecraft 26.3

## Shyne Creator v2.12.9

### Figura-Style Keybinds That Reach Native Input

- Corrected the Figura keybind facade to call Shyne's native input bridge, so Figura-style bindings no longer stop at an absent bridge name.
- Added `setKey/key`, `getKey`, `getKeyName`, `getID`, `isDefault`, `reset`, `setEnabled/enabled`, `isEnabled`, `setGUI/gui`, `isGuiEnabled`, `getKeybinds`, `getVanillaKey`, and `fromVanilla`.
- Native input now accepts named Minecraft keys, supports opt-in GUI input, persists remaps, and preserves loader-neutral behavior on Fabric and NeoForge.

## Shyne Creator v2.12.8

### Native Dynamic Textures & Honest Figura Compatibility

- `textures:newTexture()` now creates an actual Minecraft GPU `DynamicTexture`; `setPixel()`, `fill()` and `apply()` update its native image instead of only retaining a Lua table.
- Runtime texture IDs returned by `texture:id()` can be supplied to HUD Canvas and world `render.sprite` tasks. They are client-local, require visual permission, and are released whenever the Avatar runtime unloads.
- Documented the Figura bridge as **Tier 1 compatibility**, rather than an inaccurate 100% parity claim. The API reference now distinguishes implemented facades from unsupported Figura systems.

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
- **Early Figura-facing Compatibility Suite**:
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
- **Strict Codebase Covenant & Architectural Decomposition**:
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
