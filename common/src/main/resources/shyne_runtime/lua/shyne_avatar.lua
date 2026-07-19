-- Shyne Lua API Standard 1.0 (Avatar runtime)
-- New scripts use normal globals: minecraft, model, avatar, state, events, ui, vector.

local function bool(value) return value and true or false end
local function read(key) return _shyne_read(key) end
local function vec3(x, y, z)
  local value = { x = x or 0, y = y or 0, z = z or 0 }
  value[1], value[2], value[3] = value.x, value.y, value.z
  return value
end

vector = {}
function vector.new(x, y, z)
  if type(x) == "table" then return vec3(x.x or x[1], x.y or x[2], x.z or x[3]) end
  return vec3(x, y, z)
end
function vector.zero() return vec3(0, 0, 0) end

state = {}
function state.get(key, fallback)
  local value = _avatar_state_get(key)
  if value == nil then return fallback end
  return value
end
function state.set(key, value) _avatar_state_set(key, value) return value end
function state.sync(key, value)
  if value ~= nil then _avatar_synced_set(key, value) end
  return _avatar_synced_get(key)
end
function state.remote(player_id, key) return _avatar_remote_synced_get(player_id, key) end
function state.schema(path) _avatar_schema_set(path or "") end
function state.validate(key, value) return _avatar_schema_validate(key, value) end

local function coordinates(x, y, z, default)
  if type(x) == "table" then return x.x or x[1] or default, x.y or x[2] or default, x.z or x[3] or default end
  return x or default, y or x or default, z or x or default
end

local function part_proxy(path)
  local proxy = { path = path }
  return setmetatable(proxy, {
    __index = function(_, key)
      if key == "visible" then return function(self, value) if value == nil then return _avatar_part_read(path, "visible") end _avatar_part_mutate(path, "visible", bool(value)) return self end end
      if key == "show" then return function(self) return self:visible(true) end end
      if key == "hide" then return function(self) return self:visible(false) end end
      if key == "rotate" or key == "rotation" or key == "rot" then return function(self, x, y, z) if x == nil then return vector.new(_avatar_part_read(path, "rotation")) end x, y, z = coordinates(x, y, z, 0); _avatar_part_mutate(path, "rot", x, y, z) return self end end
      if key == "move" or key == "position" or key == "pos" then return function(self, x, y, z) if x == nil then return vector.new(_avatar_part_read(path, "position")) end x, y, z = coordinates(x, y, z, 0); _avatar_part_mutate(path, "pos", x, y, z) return self end end
      if key == "scale" then return function(self, x, y, z) if x == nil then return vector.new(_avatar_part_read(path, "scale")) end x, y, z = coordinates(x, y, z, 1); _avatar_part_mutate(path, "scale", x, y, z) return self end end
      if key == "color" then return function(self, r, g, b) if r == nil then return vector.new(_avatar_part_read(path, "color")) end r, g, b = coordinates(r, g, b, 1); _avatar_part_mutate(path, "color", r, g, b) return self end end
      if key == "opacity" then return function(self, value) if value == nil then return _avatar_part_read(path, "opacity") end _avatar_part_mutate(path, "opacity", value) return self end end
      if key == "emissive" then return function(self, value) if value == nil then return _avatar_part_read(path, "emissive") end _avatar_part_mutate(path, "emissive", bool(value)) return self end end
      if key == "reset" then return function(self) _avatar_part_mutate(path, "reset") return self end end
      return part_proxy(path .. "." .. tostring(key))
    end
  })
end

model = { animation = {} }
function model.part(path)
  path = tostring(path or "root")
  if path ~= "model" and string.sub(path, 1, 6) ~= "model." then path = "model." .. path end
  return part_proxy(path)
end
local animation_proxy = {}
animation_proxy.__index = animation_proxy
function animation_proxy:play() _avatar_anim_play_ex(self.name, self.speed_value or 1, self.weight_value or 1, self.priority_value or 0, self.loop_value, self.fade_in_value or 0, self.fade_out_value or 0, self.mask_value or {}, self.additive_value or false) return self end
function animation_proxy:stop() _avatar_anim_stop(self.name) return self end
function animation_proxy:restart() _avatar_anim_stop(self.name); return self:play() end
function animation_proxy:playing() return _avatar_anim_playing(self.name) end
function animation_proxy:set_playing(value) if value then return self:play() end return self:stop() end
function animation_proxy:speed(value) self.speed_value = math.max(0.01, math.min(8, value or 1)); return self end
function animation_proxy:weight(value) self.weight_value = math.max(0, math.min(1, value or 1)); return self end
function animation_proxy:priority(value) self.priority_value = math.floor(value or 0); return self end
function animation_proxy:loop(value) self.loop_value = value and true or false; return self end
function animation_proxy:fade_in(ticks) self.fade_in_value = math.max(0, math.floor(ticks or 0)); return self end
function animation_proxy:fade_out(ticks) self.fade_out_value = math.max(0, math.floor(ticks or 0)); return self end
function animation_proxy:mask(parts)
  if type(parts) == "string" then parts = { parts } end
  if type(parts) ~= "table" then error("animation mask requires a part-name table", 2) end
  self.mask_value = parts
  return self
end
function animation_proxy:additive(value) self.additive_value = value == nil or bool(value); return self end
function model.animation.get(name) return setmetatable({ name = tostring(name), speed_value = 1, weight_value = 1, priority_value = 0, mask_value = {} }, animation_proxy) end
function model.animation.exists(name) return _avatar_anim_exists(tostring(name or "")) end
function model.animation.play(name) return model.animation.get(name):play() end
function model.animation.stop(name) return model.animation.get(name):stop() end
setmetatable(model, { __index = function(_, key) return part_proxy("model." .. tostring(key)) end })

local function vanilla_proxy(part)
  local proxy = { part = tostring(part or "PLAYER") }
  function proxy:visible(value) _avatar_vanilla_visible(self.part, bool(value)) return self end
  function proxy:show() return self:visible(true) end
  function proxy:hide() return self:visible(false) end
  return proxy
end

avatar = { camera = {}, texture = {}, network = {}, state = state }
function avatar.id() return SHYNE_AVATAR_ID end
function avatar.path() return SHYNE_AVATAR_PATH end
function avatar.vanilla(part) return vanilla_proxy(part) end
function avatar.hide_vanilla(value) _avatar_vanilla_visible("PLAYER", not bool(value)) end
function avatar.camera.configure(options)
  options = options or {}
  if options.local_only ~= nil then _avatar_camera_set("local_only", bool(options.local_only)) end
  if options.first_person_masking ~= nil then _avatar_camera_set("first_person_masking", bool(options.first_person_masking)) end
  if options.hide_head ~= nil then _avatar_camera_set("hide_head_in_first_person", bool(options.hide_head)) end
  if options.offset ~= nil then local v = vector.new(options.offset); _avatar_camera_set("offset", v.x, v.y, v.z) end
  if options.rotation ~= nil then local v = vector.new(options.rotation); _avatar_camera_set("rotation", v.x, v.y, v.z) end
end
avatar.nameplate = {}
function avatar.nameplate.configure(options) options = options or {}; _avatar_nameplate_set(options.text or "", options.visible == nil or bool(options.visible)) end
function avatar.texture.sync(mode) _avatar_texture_sync(mode or "manifest") end
function avatar.network.online(value)
  _avatar_sync_policy("remote_snapshot", "", bool(value))
  _avatar_sync_policy("remote_vars", "", bool(value))
end
function avatar.network.snapshot(value) _avatar_sync_policy("remote_snapshot", "", bool(value)) end
function avatar.network.variables(value) _avatar_sync_policy("remote_vars", "", bool(value)) end
function avatar.network.allow(key) _avatar_sync_policy("allow_var", key or "", true) end
function avatar.network.local_part(path, value) _avatar_sync_policy("local_only_part", path or "", value == nil or bool(value)) end
function avatar.network.local_vanilla(part, value) _avatar_sync_policy("local_only_vanilla", part or "", value == nil or bool(value)) end

minecraft = { player = {}, world = {}, client = {} }
local function qvec(key) return vector.new(read(key) or {}) end
function minecraft.player.loaded() return read("player.loaded") or false end
function minecraft.player.name() return read("player.name") or "Player" end
function minecraft.player.uuid() return read("player.uuid") or "" end
function minecraft.player.position() return qvec("player.pos") end
function minecraft.player.velocity() return qvec("player.velocity") end
function minecraft.player.rotation() return qvec("player.rot") end
function minecraft.player.look() return qvec("player.look") end
function minecraft.player.health() return read("player.health") or 0 end
function minecraft.player.max_health() return read("player.max_health") or 0 end
function minecraft.player.body_yaw() return read("player.body_yaw") or 0 end
function minecraft.player.pose() return read("player.pose") or "STANDING" end
for _, name in ipairs({ "in_water", "underwater", "in_lava", "wet", "on_ground", "crouching", "sprinting", "swimming", "fall_flying", "sleeping", "using_item", "vehicle" }) do
  minecraft.player[name] = function() return read("player." .. name) or false end
end
function minecraft.player.active_item_time() return read("player.active_item_time") or 0 end
function minecraft.player.active_hand() return read("player.active_hand") or "NONE" end
function minecraft.player.held_item(hand) return read(hand == "off" and "player.off_hand" or "player.main_hand") end
function minecraft.player.armor(slot)
  local names = { head = "player.armor_head", chest = "player.armor_chest", legs = "player.armor_legs", feet = "player.armor_feet" }
  return read(names[slot] or names.head)
end
function minecraft.world.loaded() return read("world.loaded") or false end
function minecraft.world.time() return read("world.time") or 0 end
function minecraft.world.day_time() return read("world.day_time") or 0 end
function minecraft.world.raining() return read("world.raining") or false end
function minecraft.world.light(position) position = vector.new(position or minecraft.player.position()); return read("world.light", position.x, position.y, position.z) or 0 end
function minecraft.world.block(position) position = vector.new(position or minecraft.player.position()); return read("world.block", position.x, position.y, position.z) or "minecraft:air" end
function minecraft.client.paused() return read("client.paused") or false end
function minecraft.client.singleplayer() return read("client.singleplayer") or false end
function minecraft.command(command) return _minecraft_shyne_command(command) end

microphone = {}
function microphone.available() return _microphone_available() end
function microphone.level() return _microphone_level() end
function microphone.speaking() return _microphone_speaking() end
function microphone.muted() return _microphone_muted() end

sound = {}
function sound.play(id, options)
  options = options or {}
  return _shyne_sound_play(id, options.volume or 1, options.pitch or 1)
end

particle = {}
function particle.spawn(id, position, options)
  position, options = vector.new(position or vector.zero()), options or {}
  local velocity = vector.new(options.velocity or vector.zero())
  return _shyne_particle_spawn(id, position.x, position.y, position.z, velocity.x, velocity.y, velocity.z)
end

input = { key = {
  unknown = -1, space = 32, apostrophe = 39, comma = 44, minus = 45, period = 46,
  slash = 47, zero = 48, one = 49, two = 50, three = 51, four = 52,
  five = 53, six = 54, seven = 55, eight = 56, nine = 57,
  a = 65, b = 66, c = 67, d = 68, e = 69, f = 70, g = 71, h = 72,
  i = 73, j = 74, k = 75, l = 76, m = 77, n = 78, o = 79, p = 80,
  q = 81, r = 82, s = 83, t = 84, u = 85, v = 86, w = 87, x = 88,
  y = 89, z = 90, escape = 256, enter = 257, tab = 258,
  backspace = 259, insert = 260, delete = 261, right = 262, left = 263,
  down = 264, up = 265, page_up = 266, page_down = 267, home = 268, ending = 269
}, mouse = { left = 0, right = 1, middle = 2, button_4 = 3, button_5 = 4 },
modifier = { shift = 1, control = 2, ctrl = 2, alt = 4, super = 8 } }
local function input_modifier_mask(value)
  if type(value) == "number" then return value end
  if type(value) == "string" then return input.modifier[string.lower(value)] or 0 end
  local mask = 0
  if type(value) == "table" then
    for _, name in ipairs(value) do mask = mask + (input.modifier[string.lower(tostring(name))] or 0) end
  end
  return mask
end
function input.bind(id, options)
  options = options or {}
  return _shyne_input_bind(id, options.title or id, options.key or input.key.unknown,
    options.type or "keyboard", input_modifier_mask(options.modifiers), options.on_press,
    options.on_release, options.on_hold, options["repeat"] == true,
    options.repeat_delay or 10, options.repeat_interval or 2)
end
function input.unbind(id) return _shyne_input_unbind(id) end
function input.is_down(id) return _shyne_input_is_down(id) end
function input.get_key(id) return _shyne_input_get_key(id) end
function input.set_key(id, key_name) return _shyne_input_set_key(id, key_name) end
function input.conflicts(id) return _shyne_input_conflicts(id) end

render = {}
local function render_task(id, kind, options)
  options = options or {}
  local position = options.position or options.pos or options.from or {}
  local destination = options.to or options.destination or {}
  local ok = _shyne_render_task(id, kind, options.world == true,
    options.text or options.content or "", options.texture or options.item or options.block or options.resource or "",
    position.x or options.x or 0, position.y or options.y or 0, position.z or options.z or 0,
    destination.x or options.x2 or 0, destination.y or options.y2 or 0, destination.z or options.z2 or 0,
    options.width or 1, options.height or 16, options.scale or 1,
    options.color or 0xFFFFFFFF, options.shadow == true, options.visible ~= false,
    options.max_distance or 128)
  if ok then return id end
  return false
end
function render.text(id, options) return render_task(id, "text", options) end
function render.item(id, options) return render_task(id, "item", options) end
function render.block(id, options) return render_task(id, "block", options) end
function render.sprite(id, options) return render_task(id, "sprite", options) end
function render.line(id, options) return render_task(id, "line", options) end
function render.world(id, options)
  options = options or {}
  options.world = true
  return render_task(id, options.type or options.kind or "text", options)
end
function render.remove(id) return _shyne_render_remove(id) end
function render.clear() return _shyne_render_clear() end

events = { _handlers = {} }
local function event_name(name) return string.lower(tostring(name or "")) end
function events.on(name, callback)
  if type(callback) ~= "function" then error("events.on requires a function", 2) end
  name = event_name(name)
  events._handlers[name] = events._handlers[name] or {}
  table.insert(events._handlers[name], callback)
  return callback
end
function events.off(name, callback)
  local handlers = events._handlers[event_name(name)] or {}
  for i = #handlers, 1, -1 do if handlers[i] == callback then table.remove(handlers, i) end end
end
function events._dispatch(name, payload)
  name = event_name(name)
  payload = payload or { type = name }
  for _, callback in ipairs(events._handlers[name] or {}) do callback(payload) end
end

ui = {}
function ui.action(options)
  options = options or {}
  local id = options.id or ("action_" .. tostring(math.random(1000000)))
  local toggled = bool(options.default)
  local callback = options.on_use or options.run or function() end
  if options.toggle or options.on_toggle then
    callback = function()
      toggled = not toggled
      if options.on_toggle then options.on_toggle(toggled) elseif options.on_use then options.on_use(toggled) end
    end
  end
  _avatar_action_add(id, options.title or id, options.description or "", options.page or "main", bool(options.local_only), options.close == nil or bool(options.close), callback, options.icon or "spark")
  return id
end
function ui.page(id)
  local page = { id = id or "main" }
  function page:action(options) options = options or {}; options.page = self.id; return ui.action(options) end
  return page
end

emote = {}
function emote.register(id, options)
  options = options or {}
  _avatar_emote_register(id, options.animation or id, options.title or id, options.description or "", options.page or "emotes", bool(options.loop), bool(options.local_only), options.close == nil or bool(options.close))
end
function emote.play(id) return _avatar_emote_play(id) end
function emote.bind(trigger, id) _avatar_graph_bind(trigger, id) end
function emote.trigger(trigger) return _avatar_graph_trigger(trigger) end

diagnostics = {}
function diagnostics.snapshot() return _shyne_diagnostics() end
profiler = {}
function profiler.snapshot() return _shyne_profiler_snapshot() end
