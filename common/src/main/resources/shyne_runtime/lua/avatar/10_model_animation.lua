-- ==============================================================================
-- Shyne Avatar Runtime: Model & Animation Bridge (avatar/10_model_animation.lua)
-- Manages Blockbench model bones, transforms, procedural physics, and layered animations.
-- ==============================================================================

local function bool(value) return value and true or false end

local function coordinates(x, y, z, default)
  if type(x) == "table" then return x.x or x[1] or default, x.y or x[2] or default, x.z or x[3] or default end
  return x or default, y or x or default, z or x or default
end

---@class PartProxy
---@field path string Full bone path in hierarchy (e.g. "model.Head.Ears")
local function part_proxy(path)
  local proxy = { path = path }
  return setmetatable(proxy, {
    __index = function(_, key)
      -- Visibility
      if key == "visible" then return function(self, value) if value == nil then return _avatar_part_read(path, "visible") end _avatar_part_mutate(path, "visible", bool(value)) return self end end
      if key == "setVisible" then return function(self, value) return self:visible(value) end end
      if key == "show" then return function(self) return self:visible(true) end end
      if key == "hide" then return function(self) return self:visible(false) end end

      -- Rotation (Base Layer)
      if key == "rotate" or key == "rotation" or key == "rot" then return function(self, x, y, z) if x == nil then return vector.new(_avatar_part_read(path, "rotation")) end x, y, z = coordinates(x, y, z, 0); _avatar_part_mutate(path, "rot", x, y, z) return self end end
      if key == "setRot" then return function(self, x, y, z) return self:rot(x, y, z) end end
      if key == "getRot" or key == "getTrueRot" or key == "getAnimRot" then return function(self) return self:rot() end end

      -- Additive Rotation (Layered on top of Blockbench animations / Figura setOffsetRot)
      if key == "rot_add" or key == "add_rot" or key == "add_rotation" or key == "setOffsetRot" then
        return function(self, x, y, z)
          if x == nil then
            local r = _avatar_part_read(path, "rotation_add")
            return (vectors and vectors.vec3) and vectors.vec3(r) or vector.new(r)
          end
          x, y, z = coordinates(x, y, z, 0)
          _avatar_part_mutate(path, "rot_add", x, y, z)
          return self
        end
      end
      if key == "getOffsetRot" then
        return function(self)
          local r = _avatar_part_read(path, "rotation_add")
          return (vectors and vectors.vec3) and vectors.vec3(r) or vector.new(r)
        end
      end

      -- Position
      if key == "move" or key == "position" or key == "pos" or key == "setOffsetPos" then
        return function(self, x, y, z)
          if x == nil then
            local p = _avatar_part_read(path, "position")
            return (vectors and vectors.vec3) and vectors.vec3(p) or vector.new(p)
          end
          x, y, z = coordinates(x, y, z, 0)
          _avatar_part_mutate(path, "pos", x, y, z)
          return self
        end
      end
      if key == "setPos" then return function(self, x, y, z) return self:pos(x, y, z) end end
      if key == "getPos" or key == "getTruePos" or key == "getAnimPos" or key == "getOffsetPos" then
        return function(self)
          local p = _avatar_part_read(path, "position")
          return (vectors and vectors.vec3) and vectors.vec3(p) or vector.new(p)
        end
      end

      -- Scale
      if key == "scale" or key == "setOffsetScale" then
        return function(self, x, y, z)
          if x == nil then
            local s = _avatar_part_read(path, "scale")
            return (vectors and vectors.vec3) and vectors.vec3(s) or vector.new(s)
          end
          x, y, z = coordinates(x, y, z, 1)
          _avatar_part_mutate(path, "scale", x, y, z)
          return self
        end
      end
      if key == "setScale" then return function(self, x, y, z) return self:scale(x, y, z) end end
      if key == "getScale" or key == "getTrueScale" or key == "getOffsetScale" then
        return function(self)
          local s = _avatar_part_read(path, "scale")
          return (vectors and vectors.vec3) and vectors.vec3(s) or vector.new(s)
        end
      end

      -- Appearance & Shading
      if key == "color" then return function(self, r, g, b) if r == nil then return vector.new(_avatar_part_read(path, "color")) end r, g, b = coordinates(r, g, b, 1); _avatar_part_mutate(path, "color", r, g, b) return self end end
      if key == "setColor" then return function(self, r, g, b) return self:color(r, g, b) end end
      if key == "getColor" then return function(self) return self:color() end end
      if key == "opacity" then return function(self, value) if value == nil then return _avatar_part_read(path, "opacity") end _avatar_part_mutate(path, "opacity", value) return self end end
      if key == "setOpacity" then return function(self, value) return self:opacity(value) end end
      if key == "getOpacity" then return function(self) return self:opacity() end end
      if key == "emissive" then return function(self, value) if value == nil then return _avatar_part_read(path, "emissive") end _avatar_part_mutate(path, "emissive", bool(value)) return self end end
      if key == "setEmissive" then return function(self, value) return self:emissive(value) end end
      if key == "isEmissive" then return function(self) return self:emissive() end end
      if key == "light" or key == "setLight" then
        return function(self, block, sky)
          if block == nil then return _avatar_part_read(path, "light") end
          _avatar_part_mutate(path, "light", block, sky or block)
          return self
        end
      end
      if key == "getLight" then return function(self) return self:light() end end
      if key == "render_type" or key == "setRenderType" then
        return function(self, rtype)
          if rtype == nil then return _avatar_part_read(path, "render_type") end
          _avatar_part_mutate(path, "render_type", tostring(rtype))
          return self
        end
      end
      if key == "getRenderType" then return function(self) return self:render_type() end end
      if key == "reset" then return function(self) _avatar_part_mutate(path, "reset") return self end end

      -- 3D Text & Sprite Attachments (Figura Parity)
      if key == "newText" then
        return function(self, text_id)
          local tid = tostring(text_id or ("txt_" .. path:gsub("[^%w_]", "_")))
          local proxy = {
            _text = "", _pos = { x = 0, y = 0, z = 0 }, _scale = 1, _color = { 1, 1, 1 }, _visible = true, _billboard = true
          }
          function proxy:_sync()
            if not proxy._visible or proxy._text == "" then
              if render and render.remove then render.remove(tid) end
              return
            end
            if render and render.text then
              render.text(tid, proxy._text, {
                attach = path,
                offset = proxy._pos,
                scale = proxy._scale,
                color = proxy._color,
                billboard = proxy._billboard,
                world = true
              })
            end
          end
          function proxy:setText(t) proxy._text = tostring(t or ""); proxy:_sync(); return proxy end
          function proxy:text(t) return proxy:setText(t) end
          function proxy:setPos(x, y, z) proxy._pos = { x = x or 0, y = y or 0, z = z or 0 }; proxy:_sync(); return proxy end
          function proxy:setScale(s) proxy._scale = tonumber(s) or 1; proxy:_sync(); return proxy end
          function proxy:setColor(r, g, b) proxy._color = { r or 1, g or 1, b or 1 }; proxy:_sync(); return proxy end
          function proxy:setVisible(v) proxy._visible = bool(v); proxy:_sync(); return proxy end
          function proxy:setBillboard(b) proxy._billboard = bool(b); proxy:_sync(); return proxy end
          function proxy:remove() if render and render.remove then render.remove(tid) end end
          return proxy
        end
      end

      if key == "newSprite" then
        return function(self, sprite_id)
          local sid = tostring(sprite_id or ("spr_" .. path:gsub("[^%w_]", "_")))
          local proxy = {
            _tex = "", _pos = { x = 0, y = 0, z = 0 }, _scale = 1, _color = { 1, 1, 1 }, _visible = true, _billboard = true
          }
          function proxy:_sync()
            if not proxy._visible or proxy._tex == "" then
              if render and render.remove then render.remove(sid) end
              return
            end
            if render and render.sprite then
              render.sprite(sid, proxy._tex, {
                attach = path,
                offset = proxy._pos,
                scale = proxy._scale,
                color = proxy._color,
                billboard = proxy._billboard,
                world = true
              })
            end
          end
          function proxy:setTexture(t)
            if type(t) == "table" then
              t = t.id or t._name or (type(t.id) == "function" and t:id()) or (type(t.name) == "function" and t:name()) or tostring(t)
            end
            proxy._tex = tostring(t or "")
            proxy:_sync()
            return proxy
          end
          function proxy:texture(t) return proxy:setTexture(t) end
          function proxy:setPos(x, y, z) proxy._pos = { x = x or 0, y = y or 0, z = z or 0 }; proxy:_sync(); return proxy end
          function proxy:setScale(s) proxy._scale = tonumber(s) or 1; proxy:_sync(); return proxy end
          function proxy:setColor(r, g, b) proxy._color = { r or 1, g or 1, b or 1 }; proxy:_sync(); return proxy end
          function proxy:setVisible(v) proxy._visible = bool(v); proxy:_sync(); return proxy end
          function proxy:setBillboard(b) proxy._billboard = bool(b); proxy:_sync(); return proxy end
          function proxy:remove() if render and render.remove then render.remove(sid) end end
          return proxy
        end
      end

      -- Metadata & Hierarchy
      if key == "name" then return function() return _avatar_part_info(path).name end end
      if key == "role" then return function() return _avatar_part_info(path).role end end
      if key == "tags" then return function() return _avatar_part_info(path).tags or {} end end
      if key == "parent" then return function() local p = _avatar_part_info(path).parent; return p == "" and nil or model.part(p) end end
      if key == "children" then return function() local result = {}; for _, p in ipairs(_avatar_part_info(path).children or {}) do table.insert(result, model.part(p)) end return result end end

      -- World Space Transforms
      if key == "world_position" then return function() return vector.new(_avatar_part_info(path).world_position) end end
      if key == "world_rotation" then return function() return vector.new(_avatar_part_info(path).world_rotation) end end
      if key == "world_scale" then return function() return vector.new(_avatar_part_info(path).world_scale) end end
      if key == "world_matrix" then return function() return matrix4.copy(_avatar_part_info(path).world_matrix) end end
      if key == "world_transform" then return function()
        local info = _avatar_part_info(path)
        return {
          position = vector.new(info.world_position),
          rotation = vector.new(info.world_rotation),
          scale = vector.new(info.world_scale),
          matrix = matrix4.copy(info.world_matrix),
          context = info.render_context,
          exact = info.transform_exact == true
        }
      end end
      if key == "transform_exact" then return function() return _avatar_part_info(path).transform_exact == true end end

      -- Vanilla Limb Attachments
      if key == "vanilla_parent" then return function(self, value, mode) if value == nil then return _avatar_part_read(path, "vanilla_parent") end _avatar_part_mutate(path, "vanilla_parent", tostring(value), tostring(mode or "full")); return self end end
      if key == "vanilla_parent_mode" then return function() return _avatar_part_read(path, "vanilla_parent_mode") end end
      if key == "clear_vanilla_parent" or key == "detach_from_vanilla" then return function(self) _avatar_part_mutate(path, "vanilla_parent_clear"); return self end end
      if key == "attach_to_vanilla" then return function(self, value, mode) return self:vanilla_parent(value, mode) end end

      -- Easy Bone Physics Integration
      if key == "physics" or key == "setPhysics" then return function(self, options) _avatar_bone_physics_set(path, options == nil and true or options); return self end end
      if key == "getPhysics" then return function(self) return _avatar_bone_physics_get(path) end end

      -- Nested child path access: model.Body.Arm
      return part_proxy(path .. "." .. tostring(key))
    end
  })
end

-- ------------------------------------------------------------------------------
-- MODEL ROOT & BONE QUERIES
-- ------------------------------------------------------------------------------
model = { animation = {} }

--- Resolves a part proxy from path. Prefix "model." is automatically applied.
---@param path string|nil Bone path (e.g. "Head", "model.Head.Hat")
---@return PartProxy
function model.part(path)
  path = tostring(path or "root")
  if path ~= "model" and string.sub(path, 1, 6) ~= "model." then path = "model." .. path end
  return part_proxy(path)
end

local function model_find(kind, query)
  local result = {}
  for _, path in ipairs(_avatar_model_find(kind, tostring(query or ""))) do
    table.insert(result, model.part(path))
  end
  return result
end

function model.roles(role) return model_find("role", role) end
function model.role(role) return model.roles(role)[1] end
function model.tag(tag) return model_find("tag", tag) end
function model.parts_with_tag(tag) return model.tag(tag) end

-- ------------------------------------------------------------------------------
-- ANIMATION PROXY (Layering, Speed, Blending, Transitions)
-- ------------------------------------------------------------------------------
---@class AnimationProxy
local animation_proxy = {}
animation_proxy.__index = animation_proxy

local function stop_marker_tracker(animation)
  animation._play_generation = (animation._play_generation or 0) + 1
  if animation._marker_handler ~= nil then
    events.off("tick", animation._marker_handler)
    animation._marker_handler = nil
  end
end

--- Starts playing this animation layer.
---@return AnimationProxy
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

function animation_proxy:stop() stop_marker_tracker(self); _avatar_anim_stop(self.name); return self end
function animation_proxy:restart() _avatar_anim_stop(self.name); return self:play() end
function animation_proxy:playing() return _avatar_anim_playing(self.name) end
function animation_proxy:isPlaying() return self:playing() end
function animation_proxy:state() return _avatar_anim_info(self.name) end
function animation_proxy:time(sec)
  if sec == nil then
    return _avatar_anim_time and _avatar_anim_time(self.name) or (self:state().time or 0)
  end
  if _avatar_anim_time then _avatar_anim_time(self.name, tonumber(sec) or 0) end
  return self
end
function animation_proxy:setTime(sec) return self:time(sec) end
function animation_proxy:getTime() return self:time() end
function animation_proxy:pause()
  if _avatar_anim_pause then _avatar_anim_pause(self.name, true) end
  return self
end
function animation_proxy:resume()
  if _avatar_anim_pause then _avatar_anim_pause(self.name, false) end
  return self
end
function animation_proxy:isPaused()
  local s = self:state()
  return s and s.paused == true
end
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
function animation_proxy:transition(ticks) self.transition_value = math.max(0, math.floor(ticks or 0)); return self end
function animation_proxy:setBlendTime(ticks) return self:transition(ticks) end
function animation_proxy:mask(parts)
  if type(parts) == "string" then parts = { parts } end
  if type(parts) ~= "table" then error("animation mask requires a part-name table", 2) end
  self.mask_value = parts
  return self
end
function animation_proxy:additive(value) self.additive_value = value == nil or bool(value); return self end

function model.animation.get(name)
  return setmetatable({ name = tostring(name), speed_value = 1, weight_value = 1, priority_value = 0, mask_value = {} }, animation_proxy)
end

function model.animation.exists(name) return _avatar_anim_exists(tostring(name or "")) end
function model.animation.play(name) return model.animation.get(name):play() end
function model.animation.stop(name) return model.animation.get(name):stop() end
function model.animation.parameter(name, value)
  if value == nil then return _avatar_anim_parameter("get", tostring(name or "")) end
  _avatar_anim_parameter("set", tostring(name or ""), tonumber(value) or 0)
  return value
end
function model.animation.clear_parameter(name)
  _avatar_anim_parameter("clear", tostring(name or ""))
  return model.animation
end

setmetatable(model, { __index = function(_, key) return part_proxy("model." .. tostring(key)) end })

-- ------------------------------------------------------------------------------
-- CONVENIENCE GLOBALS: models & animations
-- ------------------------------------------------------------------------------
models = setmetatable({}, {
  __index = function(_, key)
    if key == "firstPersonHand" or key == "setFirstPersonHand" or key == "firstPersonArm" or key == "setFirstPersonArm" then
      return function(_, enabled)
        if enabled == nil and type(_) == "boolean" then enabled = _ end
        _avatar_camera_set("first_person_arm", bool(enabled))
      end
    end
    if key == "setPrimaryTexture" then
      return function(a1, a2, a3)
        local target, tex
        if type(a1) == "string" then
          target, tex = a1, a2
        else
          target, tex = a2, a3
        end
        if textures and textures.bindToModel then
          return textures:bindToModel(tex, target)
        end
        return a1
      end
    end
    return model.part(tostring(key))
  end
})

local animations_methods = {
  getPlaying = function(self)
    local list = {}
    if _avatar_anim_playing_list then
      local names = _avatar_anim_playing_list()
      for i = 1, #names do
        local name = names[i]
        table.insert(list, model.animation.get(name))
      end
    end
    return list
  end,
  stopAll = function(self)
    if _avatar_anim_stop_all then
      _avatar_anim_stop_all()
    end
    return self
  end
}

animations = setmetatable(animations_methods, {
  __index = function(t, key)
    if animations_methods[key] then return animations_methods[key] end
    local anim_name = tostring(key)
    if model.animation.exists(anim_name) then return model.animation.get(anim_name) end
    return setmetatable({}, {
      __index = function(_, sub_key) return model.animation.get(tostring(sub_key)) end
    })
  end
})
