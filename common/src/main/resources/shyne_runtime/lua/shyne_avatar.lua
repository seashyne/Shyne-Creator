-- Shyne Lua API Standard 1.1 (Avatar runtime)
-- New scripts use normal globals: minecraft, model, avatar, state, events, ui, vector.

local function bool(value) return value and true or false end
local function read(key) return _shyne_read(key) end
local vector_methods = {}
local vector_mt = {
  __index = vector_methods,
  __add = function(a, b) return vector.add(a, b) end,
  __sub = function(a, b) return vector.sub(a, b) end,
  __mul = function(a, b) return vector.mul(a, b) end,
  __div = function(a, b) return vector.div(a, b) end,
  __unm = function(a) return vector.mul(a, -1) end
}
local function vec3(x, y, z)
  local value = { x = x or 0, y = y or 0, z = z or 0 }
  value[1], value[2], value[3] = value.x, value.y, value.z
  return setmetatable(value, vector_mt)
end

vector = {}
function vector.new(x, y, z)
  if type(x) == "table" then return vec3(x.x or x[1], x.y or x[2], x.z or x[3]) end
  return vec3(x, y, z)
end
function vector.zero() return vec3(0, 0, 0) end
local function vector_components(value)
  if type(value) == "number" then return value, value, value end
  value = value or {}
  return value.x or value[1] or 0, value.y or value[2] or 0, value.z or value[3] or 0
end
function vector.add(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return vec3(ax + bx, ay + by, az + bz)
end
function vector.sub(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return vec3(ax - bx, ay - by, az - bz)
end
function vector.mul(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return vec3(ax * bx, ay * by, az * bz)
end
function vector.div(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  if bx == 0 or by == 0 or bz == 0 then error("vector division by zero", 2) end
  return vec3(ax / bx, ay / by, az / bz)
end
function vector.dot(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return ax * bx + ay * by + az * bz
end
function vector.cross(a, b)
  local ax, ay, az = vector_components(a); local bx, by, bz = vector_components(b)
  return vec3(ay * bz - az * by, az * bx - ax * bz, ax * by - ay * bx)
end
function vector.length(value)
  local x, y, z = vector_components(value)
  return math.sqrt(x * x + y * y + z * z)
end
function vector.normalize(value)
  local length = vector.length(value)
  if length == 0 then return vector.zero() end
  return vector.div(value, length)
end
function vector.distance(a, b) return vector.length(vector.sub(a, b)) end
function vector.lerp(a, b, amount)
  amount = math.max(0, math.min(1, tonumber(amount) or 0))
  return vector.add(a, vector.mul(vector.sub(b, a), amount))
end
function vector.clamp(value, minimum, maximum)
  local x, y, z = vector_components(value)
  local min_x, min_y, min_z = vector_components(minimum)
  local max_x, max_y, max_z = vector_components(maximum)
  return vec3(math.max(min_x, math.min(max_x, x)), math.max(min_y, math.min(max_y, y)), math.max(min_z, math.min(max_z, z)))
end
function vector_methods:add(value) return vector.add(self, value) end
function vector_methods:sub(value) return vector.sub(self, value) end
function vector_methods:mul(value) return vector.mul(self, value) end
function vector_methods:div(value) return vector.div(self, value) end
function vector_methods:dot(value) return vector.dot(self, value) end
function vector_methods:cross(value) return vector.cross(self, value) end
function vector_methods:length() return vector.length(self) end
function vector_methods:normalize() return vector.normalize(self) end
function vector_methods:distance(value) return vector.distance(self, value) end
function vector_methods:lerp(value, amount) return vector.lerp(self, value, amount) end

-- Standard 1.1 capability negotiation is available before the Avatar script runs.
shyne = { api = {}, result = {}, permissions = {} }
shyne.api.version = SHYNE_API_VERSION or "1.1"
shyne.api.automatic = SHYNE_API_AUTOMATIC and true or false
shyne.api.modules = _shyne_api_modules()
function shyne.api.supports(module, requirement)
  return _shyne_api_supports(tostring(module or ""), tostring(requirement or "*"))
end
function shyne.api.require(module, requirement)
  if not shyne.api.supports(module, requirement) then
    error("unsupported Shyne API requirement: " .. tostring(module) .. " " .. tostring(requirement or "*"), 2)
  end
  return true
end

result = shyne.result
function result.ok(value) return { ok = true, value = value } end
function result.error(code, message, details)
  return { ok = false, error = { code = tostring(code or "runtime_error"), message = tostring(message or ""), details = details } }
end
function result.try(callback, ...)
  if type(callback) ~= "function" then return result.error("invalid_callback", "result.try requires a function") end
  local ok, value = pcall(callback, ...)
  if ok then return result.ok(value) end
  return result.error("lua_error", tostring(value))
end

permissions = shyne.permissions
function permissions.has(name) return _shyne_permission_allowed(tostring(name or "")) end
function permissions.requested(name) return _shyne_permission_requested(tostring(name or "")) end
function permissions.list() return _shyne_permissions() end
function permissions.require(name)
  if not permissions.has(name) then error("Shyne permission not granted: " .. tostring(name), 2) end
  return true
end

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
function animation_proxy:play() _avatar_anim_play_ex(self.name, self.speed_value or 1, self.weight_value or 1, self.priority_value or 0, self.loop_value, self.fade_in_value or 0, self.fade_out_value or 0, self.mask_value or {}, self.additive_value or false, self.transition_value or 0) return self end
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
-- Cross-fades competing non-additive layers at the same priority.
function animation_proxy:transition(ticks) self.transition_value = math.max(0, math.floor(ticks or 0)); return self end
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
function model.animation.parameter(name, value)
  if value == nil then return _avatar_anim_parameter("get", tostring(name or "")) end
  _avatar_anim_parameter("set", tostring(name or ""), tonumber(value) or 0)
  return value
end
function model.animation.clear_parameter(name) _avatar_anim_parameter("clear", tostring(name or "")); return model.animation end
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

render = { api_version = "1.1", _tasks = {}, _groups = {}, _collections = {} }

-- Render options are retained in Lua so a task can be updated without rebuilding
-- every field. Only the resolved, permission-checked task crosses into Java.
local function render_copy(value)
  local result = {}
  for key, item in pairs(value or {}) do result[key] = item end
  return result
end
local function render_merge(target, patch)
  local result = render_copy(target)
  for key, value in pairs(patch or {}) do result[key] = value end
  return result
end
local function render_number(value, fallback)
  value = tonumber(value)
  if value == nil then return fallback end
  return value
end
local function render_group_options(options)
  local result = render_copy(options)
  local position = result.position or result.pos or result.from or {}
  local destination = result.to or result.destination or {}
  local x = render_number(position.x or result.x, 0)
  local y = render_number(position.y or result.y, 0)
  local z = render_number(position.z or result.z, 0)
  local x2 = render_number(destination.x or result.x2, 0)
  local y2 = render_number(destination.y or result.y2, 0)
  local z2 = render_number(destination.z or result.z2, 0)
  local width = render_number(result.width, 1)
  local height = render_number(result.height, 16)
  local scale = render_number(result.scale, 1)
  local opacity = render_number(result.opacity, 1)
  local z_index = render_number(result.z_index or result.layer, 0)
  local group_id = result.group or result.parent_group
  local visited = {}
  local depth = 0

  -- Groups are resolved from child to parent with a depth/cycle guard.
  while group_id ~= nil and render._groups[group_id] ~= nil and depth < 16 and not visited[group_id] do
    visited[group_id] = true
    depth = depth + 1
    local group = render._groups[group_id]
    local group_position = group.position or group.pos or {}
    local gx = render_number(group_position.x or group.x, 0)
    local gy = render_number(group_position.y or group.y, 0)
    local gz = render_number(group_position.z or group.z, 0)
    local group_scale = render_number(group.scale, 1)
    local sx = render_number(group.scale_x, group_scale)
    local sy = render_number(group.scale_y, group_scale)
    local sz = render_number(group.scale_z, group_scale)
    x, y, z = gx + x * sx, gy + y * sy, gz + z * sz
    x2, y2, z2 = gx + x2 * sx, gy + y2 * sy, gz + z2 * sz
    width, height = width * math.abs(sx), height * math.abs(sy)
    scale = scale * math.max(math.abs(sx), math.abs(sy))
    opacity = opacity * render_number(group.opacity, 1)
    z_index = z_index + render_number(group.z_index or group.layer, 0)
    if group.visible == false then result.visible = false end
    group_id = group.group or group.parent_group
  end

  result.position, result.pos, result.from = nil, nil, nil
  result.to, result.destination = nil, nil
  result.x, result.y, result.z = x, y, z
  result.x2, result.y2, result.z2 = x2, y2, z2
  result.width, result.height, result.scale = width, height, scale
  result.opacity, result.z_index = opacity, z_index
  return result
end
local function render_push(id, kind, options)
  local resolved = render_group_options(options)
  local ok = _shyne_render_task(id, kind, resolved.world == true,
    resolved.text or resolved.content or "", resolved.texture or resolved.item or resolved.block or resolved.resource or "",
    resolved.x or 0, resolved.y or 0, resolved.z or 0,
    resolved.x2 or 0, resolved.y2 or 0, resolved.z2 or 0,
    resolved.width or 1, resolved.height or 16, resolved.thickness or resolved.scale or 1,
    resolved.color or 0xFFFFFFFF, resolved.shadow == true, resolved.visible ~= false,
    resolved.max_distance or 128, resolved.z_index or 0, resolved.opacity or 1)
  if ok then return id end
  return false
end
local function render_task(id, kind, options)
  local stored = render_copy(options)
  render._tasks[id] = { kind = kind, options = stored }
  local result = render_push(id, kind, stored)
  if result == false then render._tasks[id] = nil end
  return result
end
local function render_refresh_all()
  for id, task in pairs(render._tasks) do render_push(id, task.kind, task.options) end
end

function render.text(id, options) return render_task(id, "text", options) end
function render.item(id, options) return render_task(id, "item", options) end
function render.block(id, options) return render_task(id, "block", options) end
function render.sprite(id, options) return render_task(id, "sprite", options) end
function render.line(id, options) return render_task(id, "line", options) end
function render.rect(id, options) return render_task(id, "rect", options) end
function render.outline(id, options) return render_task(id, "outline", options) end
function render.world(id, options)
  local world_options = render_copy(options)
  world_options.world = true
  return render_task(id, world_options.type or world_options.kind or "text", world_options)
end

-- A polyline is a managed collection of safe line tasks. It uses the same task
-- and point budgets as ordinary render.line calls.
function render.polyline(id, options)
  options = options or {}
  render.remove(id)
  local points = options.points or {}
  local children = {}
  for index = 1, #points - 1 do
    local child_id = tostring(id) .. ".segment_" .. tostring(index)
    local child = render_copy(options)
    child.points = nil
    child.from, child.to = points[index], points[index + 1]
    if render.line(child_id, child) == false then
      for _, created in ipairs(children) do render.remove(created) end
      return false
    end
    table.insert(children, child_id)
  end
  render._collections[id] = children
  return id
end

function render.update(id, patch)
  local task = render._tasks[id]
  if task == nil then return false end
  task.options = render_merge(task.options, patch)
  return render_push(id, task.kind, task.options)
end
function render.show(id) return render.update(id, { visible = true }) end
function render.hide(id) return render.update(id, { visible = false }) end
function render.task(id)
  local handle = { id = id }
  function handle:update(patch) return render.update(self.id, patch) end
  function handle:show() return render.show(self.id) end
  function handle:hide() return render.hide(self.id) end
  function handle:remove() return render.remove(self.id) end
  return handle
end

function render.group(id, options)
  render._groups[id] = render_copy(options)
  render_refresh_all()
  local handle = { id = id }
  function handle:update(patch)
    render._groups[self.id] = render_merge(render._groups[self.id], patch)
    render_refresh_all()
    return self
  end
  function handle:show() return self:update({ visible = true }) end
  function handle:hide() return self:update({ visible = false }) end
  function handle:remove()
    render._groups[self.id] = nil
    render_refresh_all()
  end
  return handle
end

function render.remove(id)
  local children = render._collections[id]
  if children ~= nil then
    render._collections[id] = nil
    for _, child_id in ipairs(children) do render.remove(child_id) end
    return true
  end
  render._tasks[id] = nil
  return _shyne_render_remove(id)
end
function render.clear()
  render._tasks, render._groups, render._collections = {}, {}, {}
  return _shyne_render_clear()
end
function render.screen() return _shyne_render_screen() end
function render.stats() return _shyne_render_stats() end
function render.on_frame(callback) return events.on("render", callback) end

events = { _handlers = {}, _internal = {} }
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
function events.once(name, callback)
  if type(callback) ~= "function" then error("events.once requires a function", 2) end
  local wrapper
  wrapper = function(payload)
    events.off(name, wrapper)
    return callback(payload)
  end
  return events.on(name, wrapper)
end
function events.clear(name)
  if name == nil then events._handlers = {} else events._handlers[event_name(name)] = nil end
end
function events._dispatch(name, payload)
  name = event_name(name)
  payload = payload or { type = name }
  for index, callback in ipairs(events._internal[name] or {}) do
    local ok, message = pcall(callback, payload)
    if not ok then _shyne_report_error("event_internal", name .. "#" .. tostring(index), tostring(message)) end
  end
  -- Snapshot the handler list so callbacks can safely subscribe/unsubscribe while dispatching.
  local handlers = {}
  for index, callback in ipairs(events._handlers[name] or {}) do handlers[index] = callback end
  for index, callback in ipairs(handlers) do
    local ok, message = pcall(callback, payload)
    if not ok then _shyne_report_error("event", name .. "#" .. tostring(index), tostring(message)) end
  end
end

task = { _next_id = 0, _entries = {}, limit = 128 }
local function task_add(delay, interval, callback, repeating)
  if type(callback) ~= "function" then error("task callback must be a function", 3) end
  if task.pending() >= task.limit then error("task limit reached (" .. tostring(task.limit) .. ")", 3) end
  task._next_id = task._next_id + 1
  local id = task._next_id
  task._entries[id] = {
    remaining = math.max(0, math.floor(tonumber(delay) or 0)),
    interval = math.max(1, math.floor(tonumber(interval) or 1)),
    callback = callback,
    repeating = repeating
  }
  return id
end
function task.after(ticks, callback) return task_add(ticks, 1, callback, false) end
function task.every(ticks, callback, options)
  options = options or {}
  local interval = math.max(1, math.floor(tonumber(ticks) or 1))
  return task_add(options.immediate and 0 or (options.delay or interval), interval, callback, true)
end
function task.cancel(id)
  local existed = task._entries[id] ~= nil
  task._entries[id] = nil
  return existed
end
function task.clear() task._entries = {} end
function task.pending()
  local count = 0
  for _ in pairs(task._entries) do count = count + 1 end
  return count
end
events._internal.tick = events._internal.tick or {}
table.insert(events._internal.tick, function(payload)
  local due = {}
  for id, entry in pairs(task._entries) do
    entry.remaining = entry.remaining - 1
    if entry.remaining <= 0 then table.insert(due, id) end
  end
  table.sort(due)
  for _, id in ipairs(due) do
    local entry = task._entries[id]
    if entry ~= nil then
      local ok, keep = pcall(entry.callback, payload, id)
      if not ok then
        _shyne_report_error("task", tostring(id), tostring(keep))
        task._entries[id] = nil
      elseif entry.repeating and keep ~= false then
        entry.remaining = entry.interval
      else
        task._entries[id] = nil
      end
    end
  end
end)
events._internal.avatar_unload = events._internal.avatar_unload or {}
table.insert(events._internal.avatar_unload, function() task.clear() end)

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

-- The shyne namespace is optional: concise globals and namespaced access are equivalent.
shyne.vector, shyne.state, shyne.model, shyne.avatar = vector, state, model, avatar
shyne.minecraft, shyne.events, shyne.task, shyne.ui = minecraft, events, task, ui
shyne.emote, shyne.diagnostics, shyne.profiler = emote, diagnostics, profiler
