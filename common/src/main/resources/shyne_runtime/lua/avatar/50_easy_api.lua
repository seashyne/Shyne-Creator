-- ==============================================================================
-- Shyne Avatar Runtime: Easy Declarative API (avatar/50_easy_api.lua)
-- Provides high-level declarative syntax for quick avatar authoring.
-- Exposes global helpers `part`, `anim`, `on`, and `shyne.setup({...})`.
-- ==============================================================================

local function copy_options(source)
  local target = {}
  for key, value in pairs(source or {}) do target[key] = value end
  return target
end

local function bool(value) return value and true or false end

--- Resolves a model part proxy. Equivalent to model.part(path).
---@param path string
---@return PartProxy
function shyne.part(path) return model.part(path) end

--- Plays or configures an animation on the Blockbench model.
---@param name string Animation name
---@param options boolean|table True to play immediately, or options table
---@return AnimationProxy
function shyne.anim(name, options)
  local animation = model.animation.get(name)
  if options == true then animation:play(); return animation end
  options = options or {}
  if options.speed ~= nil then animation:speed(options.speed) end
  if options.weight ~= nil then animation:weight(options.weight) end
  if options.priority ~= nil then animation:priority(options.priority) end
  if options.loop ~= nil then animation:loop(options.loop) end
  if options.fade_in ~= nil then animation:fade_in(options.fade_in) end
  if options.fade_out ~= nil then animation:fade_out(options.fade_out) end
  if options.transition ~= nil then animation:transition(options.transition) end
  if options.mask ~= nil then animation:mask(options.mask) end
  if options.additive ~= nil then animation:additive(options.additive) end
  if options.on_complete ~= nil then animation:on_complete(options.on_complete) end
  if options.play or options.autoplay then animation:play() end
  return animation
end

--- Subscribes to an event. Equivalent to events.on(name, callback).
---@param name string
---@param callback function
---@return function
function shyne.on(name, callback) return events.on(name, callback) end

--- Subscribes a one-shot event handler. Equivalent to events.once(name, callback).
---@param name string
---@param callback function
---@return function
function shyne.once(name, callback) return events.once(name, callback) end

--- Schedules a callback after delay ticks. Equivalent to task.after(ticks, callback).
---@param ticks number
---@param callback function
---@return number
function shyne.after(ticks, callback) return task.after(ticks, callback) end

--- Schedules a repeating callback. Equivalent to task.every(ticks, callback, options).
---@param ticks number
---@param callback function
---@param options table|nil
---@return number
function shyne.every(ticks, callback, options) return task.every(ticks, callback, options) end

--- Registers a UI palette action.
function shyne.action(id, title, callback, options)
  if type(id) == "table" then return ui.action(id) end
  local value = copy_options(options)
  value.id, value.title, value.on_use = id, title or id, callback
  return ui.action(value)
end

--- Registers a UI palette toggle action.
function shyne.toggle(id, title, default, callback, options)
  if type(id) == "table" then return ui.toggle(id) end
  local value = copy_options(options)
  value.id, value.title, value.default, value.on_toggle = id, title or id, bool(default), callback
  return ui.toggle(value)
end

local function configure_part(path, options)
  local value = model.part(path)
  options = options or {}
  if options.visible ~= nil then value:visible(options.visible) end
  if options.rotation ~= nil or options.rot ~= nil then value:rot(options.rotation or options.rot) end
  if options.position ~= nil or options.pos ~= nil then value:pos(options.position or options.pos) end
  if options.scale ~= nil then value:scale(options.scale) end
  if options.color ~= nil then value:color(options.color) end
  if options.opacity ~= nil then value:opacity(options.opacity) end
  if options.emissive ~= nil then value:emissive(options.emissive) end
  if options.physics ~= nil then value:setPhysics(options.physics) end
  if options.vanilla_parent ~= nil then value:vanilla_parent(options.vanilla_parent, options.parent_mode) end
  return value
end

--- Declarative avatar initialization in a single structured configuration call.
---@param options table { parts, animations, events, actions, toggles, hide_vanilla, camera, nameplate }
---@return table Configured objects
function shyne.setup(options)
  options = options or {}
  local result = { parts = {}, animations = {}, events = {}, actions = {} }
  if options.hide_vanilla ~= nil then avatar.hide_vanilla(options.hide_vanilla) end
  if options.camera ~= nil then avatar.camera.configure(options.camera) end
  if options.nameplate ~= nil then avatar.nameplate.configure(options.nameplate) end
  for path, config in pairs(options.parts or {}) do result.parts[path] = configure_part(path, config) end
  for name, config in pairs(options.animations or {}) do result.animations[name] = shyne.anim(name, config) end
  for name, callback in pairs(options.events or {}) do result.events[name] = events.on(name, callback) end
  for _, config in ipairs(options.actions or {}) do table.insert(result.actions, ui.action(config)) end
  for _, config in ipairs(options.toggles or {}) do table.insert(result.actions, ui.toggle(config)) end
  return result
end

-- Export core namespaces to shyne table
shyne.vector, shyne.state, shyne.storage, shyne.model, shyne.avatar = vector, state, storage, model, avatar
shyne.minecraft, shyne.events, shyne.task, shyne.ui = minecraft, events, task, ui
shyne.emote, shyne.diagnostics, shyne.profiler = emote, diagnostics, profiler
shyne.sounds, shyne.sound = sounds, sound

-- Concise globals for fast scripting
part, anim, on = shyne.part, shyne.anim, shyne.on
