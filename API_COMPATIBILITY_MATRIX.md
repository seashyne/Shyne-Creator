# Shyne Creator API Compatibility Matrix

This document outlines the exact compatibility and support tiers of the **Shyne Creator MultiLoader** (Fabric + NeoForge on Minecraft 26.3 / Java 25) compared to Figura and Blockbench.

## Support Tiers

- **🟢 Full**: Fully supported and identical to Figura standard.
- **🟡 Restricted**: Supported, but requires user-approved permission in avatar.json.
- **🔵 Partial**: Supported with specific parameters or client-side sandbox boundaries.
- **🔴 Unsupported**: Blocked or unmapped for sandbox security or multiplayer integrity.

---

## P1 Events

| API / Function | Signature | Support Tier | Required Permission | Description | Notes |
|---|---|---|---|---|---|
| `events.TICK` | `events.TICK:register(fn)` | 🟢 Full | — | Client tick event (20Hz) | Runs every client tick |
| `events.RENDER` | `events.RENDER:register(fn(delta, ctx))` | 🟢 Full | — | Frame render event | Passes partial tick and render context string |
| `events.POST_RENDER` | `events.POST_RENDER:register(fn(delta, ctx))` | 🟢 Full | — | Post entity render event | Runs after entity meshes are rendered |
| `events.WORLD_RENDER` | `events.WORLD_RENDER:register(fn(delta))` | 🟢 Full | — | World stage render hook | Runs during world rendering phase |
| `events.MOUSE_PRESS` | `events.MOUSE_PRESS:register(fn(button, action, mods))` | 🟢 Full | — | Mouse button press/release | Fires on left/right/middle click |
| `events.MOUSE_SCROLL` | `events.MOUSE_SCROLL:register(fn(dx, dy))` | 🟢 Full | — | Mouse wheel scroll event | Smooth mouse scroll delta |
| `events.KEY_PRESS` | `events.KEY_PRESS:register(fn(key, action, mods))` | 🟢 Full | — | Keyboard key events | Fires for GLFW key codes |
| `events.CHAR_TYPED` | `events.CHAR_TYPED:register(fn(char, mods))` | 🟢 Full | — | Character typed event | Fires on character unicode entry |
| `events.ITEM_USE` | `events.ITEM_USE:register(fn(item, hand))` | 🟢 Full | — | Player item use hook | Fires on eating, bow pulling, weapon swing |
| `events.CHAT_RECEIVE` | `events.CHAT_RECEIVE:register(fn(message))` | 🟢 Full | — | Chat message incoming event | Fires on system and player chat messages |
| `events.ENTITY_DAMAGE` | `events.ENTITY_DAMAGE:register(fn(amount, src, fatal))` | 🟢 Full | — | Entity damage taken event | Fires when player receives damage |
| `events.PROJECTILE_HIT` | `events.PROJECTILE_HIT:register(fn(projectile, hitType))` | 🟢 Full | — | Projectile collision event | Fires when projectile impacts entity or block |
| `events.TOTEM_POP` | `events.TOTEM_POP:register(fn(hand))` | 🟢 Full | — | Totem of Undying activation | Fires on life-saving totem pops |

## P2 Model & Render Contexts

| API / Function | Signature | Support Tier | Required Permission | Description | Notes |
|---|---|---|---|---|---|
| `models.<part>` | `models.<part>` | 🟢 Full | — | Hierarchy indexing | Direct dot indexing of model groups and cubes |
| `part:setVisible` | `part:setVisible(boolean)` | 🟢 Full | — | Toggle part visibility | Live hide/show bone or cube |
| `part:setRot` | `part:setRot(x, y, z)` | 🟢 Full | — | Bone rotation in degrees | Live rotation transformation |
| `part:setPos` | `part:setPos(x, y, z)` | 🟢 Full | — | Bone translation in units | Live position offset |
| `part:setScale` | `part:setScale(x, y, z)` | 🟢 Full | — | Bone scale factor | Independent 3-axis scaling |
| `part:setColor` | `part:setColor(r, g, b, [a])` | 🟢 Full | — | Color tinting | Multiplies vertex colors |
| `part:setMaterial` | `part:setMaterial(materialName)` | 🟢 Full | — | Material shader binding | Dynamic Texture and shader binding |
| `part:setPrimaryTexture` | `part:setPrimaryTexture(textureId)` | 🟢 Full | — | Texture swap | Binds dynamic or static texture |
| `dynamic_texture` | `dynamic_texture:bindMaterial(mat, tex)` | 🟢 Full | — | Dynamic texture binding | Live programmatic canvas texture |
| `PORTRAIT context` | `context == 'PORTRAIT'` | 🟢 Full | — | GUI portrait rendering | Invoked when rendering in inventory/HUD portrait |
| `SKULL context` | `context == 'SKULL'` | 🟢 Full | — | Skull block rendering | Invoked when rendering as placed or held player skull |
| `HELD_ITEM context` | `context == 'HELD_ITEM'` | 🟢 Full | — | Held item rendering | Invoked when rendering avatar item geometry |

## P3 Nameplate 2.0

| API / Function | Signature | Support Tier | Required Permission | Description | Notes |
|---|---|---|---|---|---|
| `nameplate.ENTITY` | `nameplate.ENTITY:setText(str)` | 🟢 Full | — | In-world overhead nameplate | Overhead floating player nameplate |
| `nameplate.CHAT` | `nameplate.CHAT:setText(str)` | 🟡 Restricted | `chat_nameplate` | Chat message prefix/nameplate | Requires user approval for chat prefix |
| `nameplate.LIST` | `nameplate.LIST:setText(str)` | 🟡 Restricted | `tab_list_nameplate` | Tab player list nameplate | Requires user approval for tab list |
| `nameplate:setBadge` | `nameplate:setBadge(icon, color)` | 🟢 Full | — | Nameplate badge prefix | Icon and hex color badge |
| `nameplate:setColor` | `nameplate:setColor(r, g, b)` | 🟢 Full | — | Nameplate text color | RGB text coloring |
| `nameplate:setBackground` | `nameplate:setBackground(color)` | 🟢 Full | — | Nameplate background plate | RGBA background fill |
| `nameplate:setOutline` | `nameplate:setOutline(boolean)` | 🟢 Full | — | Nameplate text outline | Renders dark outer text outline |
| `nameplate:setShadow` | `nameplate:setShadow(boolean)` | 🟢 Full | — | Nameplate drop shadow | Toggles font shadow offset |

## P4 Safe Creator Data & JSON

| API / Function | Signature | Support Tier | Required Permission | Description | Notes |
|---|---|---|---|---|---|
| `json.encode` | `json.encode(table)` | 🟢 Full | — | Safe JSON serializer | Converts Lua tables to JSON string |
| `json.decode` | `json.decode(string)` | 🟢 Full | — | Safe JSON deserializer | Parses JSON string into Lua table |
| `data:set` | `data:set(key, value)` | 🟡 Restricted | `data` | Persistent avatar data storage | Stores state scoped to avatar id |
| `data:get` | `data:get(key, [default])` | 🟡 Restricted | `data` | Retrieve persistent data | Reads previously saved state |
| `data:save` | `data:save()` | 🟡 Restricted | `data` | Commit storage to disk | Atomically saves storage to JSON file |
| `data:load` | `data:load()` | 🟡 Restricted | `data` | Reload storage from disk | Reloads persistent storage |
| `resources:has` | `resources:has(path)` | 🟢 Full | — | Safe asset existence check | Checks file within avatar sandbox |
| `resources:read` | `resources:read(path)` | 🟢 Full | — | Safe asset text reader | Reads text file within avatar sandbox |
| `io (raw filesystem)` | `io.open, io.read` | 🔴 Unsupported | — | Raw filesystem I/O | Blocked for security; use safe data/resources |
| `os (system execution)` | `os.execute, os.getenv` | 🔴 Unsupported | — | System process execution | Blocked for security |

## P5 Permissions & Quotas

| API / Function | Signature | Support Tier | Required Permission | Description | Notes |
|---|---|---|---|---|---|
| `avatar:hasPermission` | `avatar:hasPermission(perm)` | 🟢 Full | — | Query granted permission | Checks if user approved permission |
| `AvatarPermissionScreen` | `In-game GUI` | 🟢 Full | — | Granular permissions screen | User approval UI with toggleable permissions |
| `Dynamic Texture Quota` | `512x512 edge, 16 MiB max` | 🟢 Full | — | Texture budget enforcement | Rejects oversized allocations gracefully |
| `Particle Quota` | `64 particles/sec max` | 🟢 Full | — | Particle rate limit | Drops particles beyond rate budget |
| `Sound Quota` | `16 sounds/sec max` | 🟢 Full | — | Sound rate limit | Drops sounds beyond rate budget |
| `Render Task Quota` | `128 tasks max` | 🟢 Full | — | Render tasks budget | Prevents infinite render loops |
| `AvatarProfiler` | `profiler:getMetrics()` | 🟢 Full | — | Performance profiler | Detailed timing breakdown of Lua & rendering |

## P6 Network API & Typed Pings

| API / Function | Signature | Support Tier | Required Permission | Description | Notes |
|---|---|---|---|---|---|
| `pings:define` | `pings:define(name, schema, fn)` | 🟢 Full | — | Typed network pings | Automatic type coercion and bounds checking |
| `pings.<name>` | `pings.<name>(...args)` | 🟢 Full | — | Invoke network ping | Sends synchronized ping to clients |
| `network.send` | `network.send(channel, payload)` | 🟡 Restricted | `network` | Managed packet channel | Sends message over server-approved channel |
| `network.on` | `network.on(channel, fn(payload))` | 🟡 Restricted | `network` | Listen to packet channel | Receives messages from packet channel |
| `network.allow_channel` | `network.allow_channel(channel)` | 🟡 Restricted | `network` | Whitelist custom channel | Allows channel for communication |
| `raw server_packets` | `server_packets.raw` | 🔵 Partial | `network` | Raw server packet access | Routed safely through Shyne channel |

## P7 Creator Workflow

| API / Function | Signature | Support Tier | Required Permission | Description | Notes |
|---|---|---|---|---|---|
| `Line-precise Hot Reload` | `AvatarFileWatcher` | 🟢 Full | — | Auto-reloads on file save | Reports exact filename and line number on error |
| `AvatarImportReport` | `AvatarImporter.analyze()` | 🟢 Full | — | Blockbench/Figura import report | Metrics for cubes, bones, textures, and easing |
| `AvatarScriptLinter` | `AvatarScriptLinter.lint()` | 🟢 Full | — | Automated API and permission linter | Detects syntax errors, blocked calls, missing perms |
| `AvatarTemplateManager` | `AvatarTemplateManager.scaffold()` | 🟢 Full | — | Starter project templates | Minimal, Figura Compat, and Action Wheel templates |

## Action Wheel

| API / Function | Signature | Support Tier | Required Permission | Description | Notes |
|---|---|---|---|---|---|
| `action_wheel.newPage` | `action_wheel.newPage()` | 🟢 Full | — | Create action wheel page | Multi-page radial action menu |
| `action_wheel.setPage` | `action_wheel.setPage(page)` | 🟢 Full | — | Set active action page | Displays page on radial wheel |
| `page:newAction` | `page:newAction()` | 🟢 Full | — | Add action to page | Action button with icon, title, and callback |

## Player & World Properties

| API / Function | Signature | Support Tier | Required Permission | Description | Notes |
|---|---|---|---|---|---|
| `player:getName` | `player:getName()` | 🟢 Full | — | Player username | Returns player name |
| `player:getPos` | `player:getPos()` | 🟢 Full | — | Player world coordinates | Returns vector3 position |
| `player:getRot` | `player:getRot()` | 🟢 Full | — | Player pitch & yaw | Returns vector2 rotation |
| `player:getHealth` | `player:getHealth()` | 🟢 Full | — | Player health points | Returns current health |
| `player:isSneaking` | `player:isSneaking()` | 🟢 Full | — | Crouch state | Checks if player is sneaking |
| `world.getTime` | `world.getTime()` | 🟢 Full | — | Day/night time | Read-only world time |
| `world.setBlock` | `world.setBlock(pos, block)` | 🔴 Unsupported | — | Modify world block | Blocked; client avatars cannot modify blocks |
