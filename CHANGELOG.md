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
