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
  if options.replace_vanilla ~= nil then
    shyne.replace_vanilla(options.replace_vanilla)
  elseif options.hide_vanilla ~= nil then
    shyne.replace_vanilla(options.hide_vanilla)
  end
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
shyne.stream = sound.stream

-- ------------------------------------------------------------------------------
-- DECLARATIVE UI ANIMATION & FX API (ui.fx / fx)
-- High-level declarative APIs for floating damage numbers, smooth bars,
-- typewriter dialogs, toasts, pulse, and screen shakes. Zero boilerplate!
-- ------------------------------------------------------------------------------
fx = {
  _damages = {},
  _typewriters = {},
  _bars = {},
  _toasts = {},
  _next_id = 0
}

--- Triggers a callback whenever an audio stream detects a musical beat.
---@param stream table Stream handle returned by sound.stream()
---@param callback function Callback function(stream)
function fx.beat(stream, callback)
  if not stream or type(callback) ~= "function" then return end
  events.on("tick", function()
    if stream:isBeat() then
      callback(stream)
    end
  end)
end

function fx.damage(amount, pos, options)
  options = options or {}
  pos = pos or (player and player.getPos and player:getPos()) or { x = 0, y = 0, z = 0 }
  fx._next_id = fx._next_id + 1
  local id = "dmg_fx_" .. fx._next_id
  local is_crit = options.crit and true or false
  local txt = options.text
  if not txt then
    if is_crit then
      txt = Color.red + Color.bold + Color.skull + " CRIT! -" + amount
    else
      txt = Color.yellow + "-" + amount
    end
  end

  local lifetime = options.lifetime or 30
  local entry = {
    id = id,
    text = txt,
    pos = { x = pos.x + (math.random() - 0.5) * 0.4, y = pos.y + 1.2, z = pos.z + (math.random() - 0.5) * 0.4 },
    vel_y = options.vel_y or 0.03,
    lifetime = lifetime,
    max_lifetime = lifetime,
    scale = options.scale or (is_crit and 1.0 or 0.8)
  }
  table.insert(fx._damages, entry)
  return id
end

function fx.typewriter(id, text, options)
  options = options or {}
  local state = fx._typewriters[id]
  if not state or state.full_text ~= text then
    state = { full_text = text, chars = 0, completed = false }
    fx._typewriters[id] = state
  end

  if state.chars < #text then
    state.chars = math.min(#text, state.chars + (options.speed or 1))
    if options.sound ~= false and sounds and sounds.playSound and player and player.getPos then
      sounds:playSound(options.sound_id or "minecraft:block.stone_button.click_on", player:getPos(), 0.25, 1.8)
    end
  else
    state.completed = true
  end

  local visible = string.sub(text, 1, math.floor(state.chars))
  if render and render.text then
    render.text(id, {
      text = visible,
      x = options.x or 100,
      y = options.y or 80,
      color = options.color or 0xFFFFFFFF,
      scale = options.scale or 1.0,
      shadow = options.shadow ~= false,
      z_index = options.z_index or 50
    })
  end
  return state.completed, visible
end

function fx.bar(id, options)
  options = options or {}
  local x = options.x or 50
  local y = options.y or 20
  local width = options.width or 120
  local height = options.height or 7
  local current = math.max(0, options.current or 0)
  local max = math.max(1, options.max or 100)
  local z = options.z_index or 40

  local bar_state = fx._bars[id]
  if not bar_state then
    bar_state = { ghost = current }
    fx._bars[id] = bar_state
  end

  -- Smooth Lerp ghost bar
  if bar_state.ghost > current then
    bar_state.ghost = bar_state.ghost - (bar_state.ghost - current) * 0.08
  else
    bar_state.ghost = current
  end

  if not render or not render.rect then return end

  -- 1. Background
  render.rect(id .. "_bg", {
    x = x - 1, y = y - 1,
    width = width + 2, height = height + 2,
    color = options.bg_color or 0xCC10141E,
    z_index = z
  })

  -- 2. Ghost Bar
  if options.ghost ~= false and bar_state.ghost > current then
    local g_ratio = math.max(0, math.min(1, bar_state.ghost / max))
    render.rect(id .. "_ghost", {
      x = x, y = y,
      width = math.floor(width * g_ratio), height = height,
      color = options.ghost_color or 0xFFFF9900,
      z_index = z + 1
    })
  else
    if render.remove then render.remove(id .. "_ghost") end
  end

  -- 3. Current Fill
  local fill_ratio = math.max(0, math.min(1, current / max))
  render.rect(id .. "_fill", {
    x = x, y = y,
    width = math.floor(width * fill_ratio), height = height,
    color = options.color or 0xFFCC2222,
    z_index = z + 2
  })

  -- 4. Text Label
  if options.text and render.text then
    local txt = tostring(options.text)
    render.text(id .. "_txt", {
      text = txt,
      x = x + (width / 2) - (#txt * 2.2),
      y = y - 10,
      color = options.text_color or 0xFFFFFFFF,
      scale = 0.85, shadow = true,
      z_index = z + 3
    })
  else
    if render.remove then render.remove(id .. "_txt") end
  end
end

function fx.pulse(options)
  options = options or {}
  local min = options.min or 0.92
  local max = options.max or 1.08
  local speed = options.speed or 2
  local tick = options.time or ((client and client.getTick and client:getTick()) or 0)
  return min + (math.sin(tick * 0.1 * speed) * 0.5 + 0.5) * (max - min)
end

function fx.shake(intensity)
  local amt = intensity or 2
  return math.random(-amt, amt), math.random(-amt, amt)
end

function fx.toast(id, text, options)
  options = options or {}
  local state = fx._toasts[id]
  local duration = options.duration or 60
  if not state then
    state = { time = 0, duration = duration }
    fx._toasts[id] = state
    if options.sound ~= false and sounds and sounds.playSound and player and player.getPos then
      sounds:playSound("minecraft:entity.experience_orb.pickup", player:getPos(), 0.8, 1.2)
    end
  end

  state.time = state.time + 1
  if state.time > duration then
    if render and render.remove then
      render.remove(id .. "_toast_bg")
      render.remove(id .. "_toast_txt")
    end
    fx._toasts[id] = nil
    return true
  end

  local screen = render and render.screen and render.screen()
  if not screen or not screen.ready then return false end

  local target_y = 20
  local current_y = target_y
  if state.time < 10 then
    current_y = -20 + (target_y - (-20)) * (state.time / 10)
  elseif state.time > duration - 10 then
    local remaining = duration - state.time
    current_y = -20 + (target_y - (-20)) * (remaining / 10)
  end

  local tw = math.max(120, #text * 6 + 20)
  local tx = math.floor((screen.width - tw) / 2)

  render.rect(id .. "_toast_bg", {
    x = tx, y = math.floor(current_y),
    width = tw, height = 18,
    color = options.bg_color or 0xDD1B2234,
    z_index = 80
  })

  render.text(id .. "_toast_txt", {
    text = text,
    x = tx + (tw / 2) - (#text * 2.5),
    y = math.floor(current_y) + 4,
    color = options.text_color or 0xFFFFFFFF,
    scale = 0.9, shadow = true,
    z_index = 81
  })
  return false
end

function fx._tick()
  for i = #fx._damages, 1, -1 do
    local d = fx._damages[i]
    d.pos.y = d.pos.y + d.vel_y
    d.lifetime = d.lifetime - 1
    local alpha = math.max(0, d.lifetime / d.max_lifetime)

    if render and render.text then
      render.text(d.id, {
        text = d.text,
        pos = d.pos,
        world = true,
        billboard = true,
        opacity = alpha,
        scale = d.scale
      })
    end

    if d.lifetime <= 0 then
      if render and render.remove then render.remove(d.id) end
      table.remove(fx._damages, i)
    end
  end
end

events.on("tick", fx._tick)

ui.fx = fx
shyne.fx = fx

-- Concise globals for fast scripting
part, anim, on = shyne.part, shyne.anim, shyne.on
