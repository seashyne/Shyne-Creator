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
