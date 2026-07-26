-- Optional systems live in small bundled modules. The Java runtime loads the
-- native rig module after this core API has established its model/event hooks.

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
  _avatar_action_add(id, options.title or id, options.description or "", options.page or "main", bool(options.local_only), options.close == nil or bool(options.close), callback, options.icon or "spark", options.on_secondary or options.on_right_click)
  return id
end
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
