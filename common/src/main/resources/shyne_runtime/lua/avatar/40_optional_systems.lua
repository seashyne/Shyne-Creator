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
-- SCRIPT CANVAS
-- ------------------------------------------------------------------------------
-- A canvas is intentionally blank: Lua draws every pixel through render.* and
-- supplies the invisible hit areas that should receive mouse input. This keeps
-- Creator UI independent from Shyne's built-in menus.
local function canvas_copy(value)
  local result = {}
  for key, item in pairs(value or {}) do result[key] = item end
  return result
end

--- Creates a full-screen interactive canvas owned and styled entirely by this Avatar.
--- @param options table { id, pause, backdrop, close_on_escape, on_open, on_close }
--- @return table Canvas handle
function ui.canvas(options)
  options = options or {}
  local id = tostring(options.id or ("canvas_" .. tostring(math.random(1000000)))):lower():gsub("[^%w_.-]", "_"):sub(1, 64)
  local surface = "canvas." .. id
  local ok = _shyne_ui_canvas_define and _shyne_ui_canvas_define(
    id, bool(options.pause), options.backdrop or 0x00000000,
    options.close_on_escape ~= false, options.on_open, options.on_close
  )
  if not ok then return nil end

  local canvas = { id = id, surface = surface, _tasks = {} }

  function canvas:open()
    return _shyne_ui_canvas_open and _shyne_ui_canvas_open(self.id) or false
  end

  function canvas:close()
    return _shyne_ui_canvas_close and _shyne_ui_canvas_close(self.id) or false
  end

  --- Adds or replaces a clickable rectangle. Drawing its visual is still the Creator's job.
  function canvas:button(button)
    button = button or {}
    local button_id = tostring(button.id or ("button_" .. tostring(math.random(1000000))))
    return _shyne_ui_canvas_button and _shyne_ui_canvas_button(
      self.id, button_id, tonumber(button.x) or 0, tonumber(button.y) or 0,
      tonumber(button.width or button.w) or 1, tonumber(button.height or button.h) or 1,
      button.on_click or button.on_use
    ) or false
  end

  function canvas:clear_buttons()
    return _shyne_ui_canvas_clear_buttons and _shyne_ui_canvas_clear_buttons(self.id) or false
  end

  --- Draws a task onto this canvas only; it never appears in the normal HUD.
  function canvas:draw(kind, task_id, draw_options)
    local draw = render and render[kind]
    if type(draw) ~= "function" then return false end
    local local_id = tostring(task_id or kind)
    local options_copy = canvas_copy(draw_options)
    options_copy.surface = self.surface
    local qualified_id = "canvas." .. self.id .. "." .. local_id
    local result = draw(qualified_id, options_copy)
    if result then self._tasks[local_id] = qualified_id end
    return result
  end

  function canvas:text(task_id, draw_options) return self:draw("text", task_id, draw_options) end
  function canvas:rect(task_id, draw_options) return self:draw("rect", task_id, draw_options) end
  function canvas:outline(task_id, draw_options) return self:draw("outline", task_id, draw_options) end
  function canvas:sprite(task_id, draw_options) return self:draw("sprite", task_id, draw_options) end
  function canvas:item(task_id, draw_options) return self:draw("item", task_id, draw_options) end
  function canvas:block(task_id, draw_options) return self:draw("block", task_id, draw_options) end
  function canvas:line(task_id, draw_options) return self:draw("line", task_id, draw_options) end

  function canvas:update(task_id, patch)
    local qualified_id = self._tasks[tostring(task_id)]
    if qualified_id == nil or render == nil then return false end
    patch = canvas_copy(patch)
    patch.surface = self.surface
    return render.update(qualified_id, patch)
  end

  function canvas:remove(task_id)
    local local_id = tostring(task_id)
    local qualified_id = self._tasks[local_id]
    if qualified_id == nil or render == nil then return false end
    self._tasks[local_id] = nil
    return render.remove(qualified_id)
  end

  function canvas:clear()
    for _, qualified_id in pairs(self._tasks) do render.remove(qualified_id) end
    self._tasks = {}
  end

  return canvas
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
