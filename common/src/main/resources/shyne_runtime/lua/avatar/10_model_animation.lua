-- Standard 2.0 capability negotiation is available before the Avatar script runs.
shyne = { api = {}, result = {}, permissions = {} }
shyne.api.version = SHYNE_API_VERSION or "2.0"
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
storage = {}
function storage.get(key, fallback)
  local value = _avatar_local_get(tostring(key or ""))
  return value == nil and fallback or value
end
function storage.set(key, value)
  _avatar_local_set(tostring(key or ""), value)
  return value
end
function storage.delete(key) _avatar_local_set(tostring(key or ""), nil) end
-- Kept temporarily for 2.7.36 scripts created before the public API was finalized.
state.local_value = storage.get
state["local"] = storage.get
state.save = storage.set
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
      if key == "setVisible" then return function(self, value) return self:visible(value) end end
      if key == "show" then return function(self) return self:visible(true) end end
      if key == "hide" then return function(self) return self:visible(false) end end
      if key == "rotate" or key == "rotation" or key == "rot" then return function(self, x, y, z) if x == nil then return vector.new(_avatar_part_read(path, "rotation")) end x, y, z = coordinates(x, y, z, 0); _avatar_part_mutate(path, "rot", x, y, z) return self end end
      if key == "setRot" then return function(self, x, y, z) return self:rot(x, y, z) end end
      if key == "rot_add" or key == "add_rot" or key == "add_rotation" then return function(self, x, y, z) if x == nil then return vector.new(_avatar_part_read(path, "rotation_add")) end x, y, z = coordinates(x, y, z, 0); _avatar_part_mutate(path, "rot_add", x, y, z) return self end end
      if key == "getRot" or key == "getTrueRot" or key == "getAnimRot" then return function(self) return self:rot() end end
      if key == "move" or key == "position" or key == "pos" then return function(self, x, y, z) if x == nil then return vector.new(_avatar_part_read(path, "position")) end x, y, z = coordinates(x, y, z, 0); _avatar_part_mutate(path, "pos", x, y, z) return self end end
      if key == "setPos" then return function(self, x, y, z) return self:pos(x, y, z) end end
      if key == "getPos" or key == "getTruePos" or key == "getAnimPos" then return function(self) return self:pos() end end
      if key == "scale" then return function(self, x, y, z) if x == nil then return vector.new(_avatar_part_read(path, "scale")) end x, y, z = coordinates(x, y, z, 1); _avatar_part_mutate(path, "scale", x, y, z) return self end end
      if key == "setScale" then return function(self, x, y, z) return self:scale(x, y, z) end end
      if key == "getScale" or key == "getTrueScale" then return function(self) return self:scale() end end
      if key == "color" then return function(self, r, g, b) if r == nil then return vector.new(_avatar_part_read(path, "color")) end r, g, b = coordinates(r, g, b, 1); _avatar_part_mutate(path, "color", r, g, b) return self end end
      if key == "opacity" then return function(self, value) if value == nil then return _avatar_part_read(path, "opacity") end _avatar_part_mutate(path, "opacity", value) return self end end
      if key == "emissive" then return function(self, value) if value == nil then return _avatar_part_read(path, "emissive") end _avatar_part_mutate(path, "emissive", bool(value)) return self end end
      if key == "reset" then return function(self) _avatar_part_mutate(path, "reset") return self end end
      if key == "name" then return function() return _avatar_part_info(path).name end end
      if key == "role" then return function() return _avatar_part_info(path).role end end
      if key == "tags" then return function() return _avatar_part_info(path).tags or {} end end
      if key == "parent" then return function() local p = _avatar_part_info(path).parent; return p == "" and nil or model.part(p) end end
      if key == "children" then return function() local result = {}; for _, p in ipairs(_avatar_part_info(path).children or {}) do table.insert(result, model.part(p)) end return result end end
      if key == "world_position" then return function() return vector.new(_avatar_part_info(path).world_position) end end
      if key == "world_rotation" then return function() return vector.new(_avatar_part_info(path).world_rotation) end end
      if key == "world_scale" then return function() return vector.new(_avatar_part_info(path).world_scale) end end
      if key == "world_matrix" then return function() return matrix4.copy(_avatar_part_info(path).world_matrix) end end
      if key == "world_transform" then return function()
        local info = _avatar_part_info(path)
        return {
          position = vector.new(info.world_position), rotation = vector.new(info.world_rotation),
          scale = vector.new(info.world_scale), matrix = matrix4.copy(info.world_matrix),
          context = info.render_context, exact = info.transform_exact == true
        }
      end end
      if key == "transform_exact" then return function() return _avatar_part_info(path).transform_exact == true end end
      if key == "vanilla_parent" then return function(self, value, mode) if value == nil then return _avatar_part_read(path, "vanilla_parent") end _avatar_part_mutate(path, "vanilla_parent", tostring(value), tostring(mode or "full")); return self end end
      if key == "vanilla_parent_mode" then return function() return _avatar_part_read(path, "vanilla_parent_mode") end end
      if key == "clear_vanilla_parent" or key == "detach_from_vanilla" then return function(self) _avatar_part_mutate(path, "vanilla_parent_clear"); return self end end
      if key == "attach_to_vanilla" then return function(self, value, mode) return self:vanilla_parent(value, mode) end end
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
local function model_find(kind, query)
  local result = {}
  for _, path in ipairs(_avatar_model_find(kind, tostring(query or ""))) do table.insert(result, model.part(path)) end
  return result
end
function model.roles(role) return model_find("role", role) end
function model.role(role) return model.roles(role)[1] end
function model.tag(tag) return model_find("tag", tag) end
function model.parts_with_tag(tag) return model.tag(tag) end
local animation_proxy = {}
animation_proxy.__index = animation_proxy
local function stop_marker_tracker(animation)
  animation._play_generation = (animation._play_generation or 0) + 1
  if animation._marker_handler ~= nil then
    events.off("tick", animation._marker_handler)
    animation._marker_handler = nil
  end
end
function animation_proxy:play()
  stop_marker_tracker(self)
  _avatar_anim_play_ex(self.name, self.speed_value or 1, self.weight_value or 1, self.priority_value or 0, self.loop_value, self.fade_in_value or 0, self.fade_out_value or 0, self.mask_value or {}, self.additive_value or false, self.transition_value or 0)
  if self._markers and #self._markers > 0 then
    local generation = self._play_generation
    local fired, last_time, handler = {}, 0, nil
    handler = function()
      if self._play_generation ~= generation then events.off("tick", handler); return end
      local info = self:state()
      local current_time = tonumber(info.time) or 0
      if info.looping and current_time + 0.000001 < last_time then
        local completed = { time = tonumber(info.length) or self:length(), length = tonumber(info.length) or self:length(), looping = true, playing = true, weight = info.weight, priority = info.priority }
        for index, marker in ipairs(self._markers) do
          if not fired[index] and marker.time <= completed.time then marker.callback(self, completed) end
        end
        fired = {}
      end
      for index, marker in ipairs(self._markers) do
        if not fired[index] and current_time >= marker.time then
          fired[index] = true
          marker.callback(self, info)
        end
      end
      last_time = current_time
      if info.playing == false then
        events.off("tick", handler)
        if self._marker_handler == handler then self._marker_handler = nil end
      end
    end
    self._marker_handler = handler
    events.on("tick", handler)
  end
  return self
end
function animation_proxy:stop() stop_marker_tracker(self); _avatar_anim_stop(self.name) return self end
function animation_proxy:restart() _avatar_anim_stop(self.name); return self:play() end
function animation_proxy:playing() return _avatar_anim_playing(self.name) end
function animation_proxy:state() return _avatar_anim_info(self.name) end
function animation_proxy:time() return self:state().time or 0 end
function animation_proxy:length() local value = self:state().length or 0; return value > 0 and value or _avatar_anim_length(self.name) end
function animation_proxy:looping() return self:state().looping or false end
function animation_proxy:on_keyframe(time, callback)
  if type(callback) ~= "function" then error("animation keyframe callback must be a function", 2) end
  self._markers = self._markers or {}
  table.insert(self._markers, { time = math.max(0, tonumber(time) or 0), callback = callback })
  return self
end
function animation_proxy:on_complete(callback)
  return self:on_keyframe(self:length(), callback)
end
function animation_proxy:isPlaying() return self:playing() end
function animation_proxy:set_playing(value) if value then return self:play() end return self:stop() end
function animation_proxy:speed(value) self.speed_value = math.max(0.01, math.min(8, value or 1)); return self end
function animation_proxy:setSpeed(value) return self:speed(value) end
function animation_proxy:weight(value) self.weight_value = math.max(0, math.min(1, value or 1)); return self end
function animation_proxy:setBlend(value) return self:weight(value) end
function animation_proxy:priority(value) self.priority_value = math.floor(value or 0); return self end
function animation_proxy:setPriority(value) return self:priority(value) end
function animation_proxy:loop(value) self.loop_value = value and true or false; return self end
function animation_proxy:setLoop(value) return self:loop(value) end
function animation_proxy:fade_in(ticks) self.fade_in_value = math.max(0, math.floor(ticks or 0)); return self end
function animation_proxy:fade_out(ticks) self.fade_out_value = math.max(0, math.floor(ticks or 0)); return self end
-- Cross-fades competing non-additive layers at the same priority.
function animation_proxy:transition(ticks) self.transition_value = math.max(0, math.floor(ticks or 0)); return self end
function animation_proxy:setBlendTime(ticks) return self:transition(ticks) end
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
