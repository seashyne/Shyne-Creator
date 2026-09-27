-- ==============================================================================
-- Shyne Avatar Runtime: UI Actions & Systems (avatar/40_optional_systems.lua)
-- Manages radial wheel actions, toggle controls, custom emotes, and performance diagnostics.
-- ==============================================================================

local function bool(value) return value and true or false end

-- ------------------------------------------------------------------------------
-- UI PALETTE ACTIONS & TOGGLES
-- ------------------------------------------------------------------------------
ui = {}

--- Registers an interactive action item on the Shyne Avatar Palette wheel.
---@param options table Action configuration { id, title, description, page, icon, on_use, close }
---@return string Action ID
function ui.action(options)
  options = options or {}
  local id = options.id or ("action_" .. tostring(math.random(1000000)))
  local is_toggle = options.toggle or options.on_toggle ~= nil
  local toggled = bool(options.default)
  local callback = options.on_use or options.run or function() end
  if is_toggle then
    callback = function()
      toggled = not toggled
      if options.on_toggle then options.on_toggle(toggled) elseif options.on_use then options.on_use(toggled) end
    end
  end
  local should_close = not is_toggle and (options.close == nil or bool(options.close))
  _avatar_action_add(
    id, options.title or id, options.description or "", options.page or "main",
    bool(options.local_only), should_close, callback,
    options.icon or "spark", options.on_secondary or options.on_right_click,
    is_toggle, toggled, options.color, options.hover_color
  )
  return id
end

--- Registers a persistent boolean toggle action backed by avatar local storage.
---@param options table Toggle configuration { id, title, default, on_toggle, state_key }
---@return string Action ID
function ui.toggle(options)
  options = options or {}
  local key = options.state_key or ("action." .. tostring(options.id or options.title or "toggle"))
  local initial = storage.get(key, options.default and true or false)
  local callback = options.on_toggle or options.on_use or function() end
  options.default = initial
  options.toggle = true
  options.on_toggle = function(value)
    storage.set(key, value)
    callback(value)
  end
  return ui.action(options)
end

--- Returns a page builder for grouping actions on a specific palette sub-menu page.
---@param id string Page identifier
---@return table Page handle
function ui.page(id)
  local page = { id = id or "main" }
  function page:action(options) options = options or {}; options.page = self.id; return ui.action(options) end
  return page
end

-- ------------------------------------------------------------------------------
-- EMOTES
-- ------------------------------------------------------------------------------
emote = {}

--- Registers a custom emote animation accessible via the palette or emote wheel.
---@param id string Emote identifier
---@param options table { animation, title, description, loop, close }
function emote.register(id, options)
  options = options or {}
  _avatar_emote_register(id, options.animation or id, options.title or id, options.description or "", options.page or "emotes", bool(options.loop), bool(options.local_only), options.close == nil or bool(options.close))
end

--- Triggers playback of a registered emote by ID.
---@param id string
---@return boolean
function emote.play(id) return _avatar_emote_play(id) end

function emote.bind(trigger, id) _avatar_graph_bind(trigger, id) end
function emote.trigger(trigger) return _avatar_graph_trigger(trigger) end

-- ------------------------------------------------------------------------------
-- DIAGNOSTICS & PROFILER
-- ------------------------------------------------------------------------------
diagnostics = {}
function diagnostics.snapshot() return _shyne_diagnostics() end

profiler = {}
function profiler.snapshot() return _shyne_profiler_snapshot() end
