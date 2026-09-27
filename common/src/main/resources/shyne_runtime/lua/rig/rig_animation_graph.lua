-- ==============================================================================
-- Shyne Rig Animation Graph Module (rig/rig_animation_graph.lua)
-- Provides a declarative state machine over the layered Blockbench animation runtime.
-- Evaluates conditional triggers (`when`), priority hierarchies, and smooth
-- cross-fade transitions between competing movement and action states.
-- ==============================================================================

local rig_animation_graph = {}

--- Constructs a state-driven animation controller over Blockbench animations.
---@param definition table Configuration with { states, order, default, transition, fade_in, fade_out }
---@param rig_registry table The shared rig._controllers table
---@return table Animation graph controller instance with :select(), :update(), :play(name), :stop()
function rig_animation_graph.create(definition, rig_registry)
  definition = definition or {}
  local graph = {
    states = definition.states or {},
    active = nil,
    enabled = true
  }

  --- Evaluates whether a state's conditional trigger is currently met.
  local function matches(state)
    if type(state.when) ~= "function" then return state.when == true end
    -- Wrap in pcall: a broken optional condition must not crash or prevent idle from playing
    local ok, value = pcall(state.when)
    return ok and value and true or false
  end

  --- Evaluates all registered states and selects the highest-priority matching state.
  function graph:select()
    -- 1. Check explicit evaluation order if provided
    for _, name in ipairs(definition.order or {}) do
      local state = self.states[name]
      if state and matches(state) then return name, state end
    end

    -- 2. Without explicit order, highest numeric priority wins
    local selected_name, selected_state, selected_priority = nil, nil, -math.huge
    for name, state in pairs(self.states) do
      if matches(state) then
        local priority = tonumber(state.priority) or 0
        if selected_state == nil or priority > selected_priority or (priority == selected_priority and tostring(name) < tostring(selected_name)) then
          selected_name, selected_state, selected_priority = name, state, priority
        end
      end
    end
    if selected_state ~= nil then return selected_name, selected_state end

    -- 3. Fallback to default state (defaults to "idle")
    local fallback = definition.default or "idle"
    return fallback, self.states[fallback]
  end

  --- Updates active state and transitions animation layers smoothly if state has changed.
  function graph:update()
    if not self.enabled then return end
    local name, state = self:select()
    if not state or name == self.active then return end

    -- Stop previously active animation
    if self.active and self.states[self.active] then
      model.animation.get(self.states[self.active].animation or self.active):stop()
    end

    self.active = name
    local transition = state.transition or definition.transition or 5
    model.animation.get(state.animation or name)
      :loop(state.loop ~= false)
      :weight(state.weight or 1)
      :priority(state.priority or 0)
      :fade_in(state.fade_in or definition.fade_in or transition)
      :fade_out(state.fade_out or definition.fade_out or transition)
      :transition(transition)
      :play()
  end

  --- Forces immediate playback of a specific state name.
  ---@param name string Name of the state to switch to
  function graph:play(name)
    self.active = nil
    definition.default = name
    self:update()
    return self
  end

  --- Stops the animation graph and halts any playing state animation.
  function graph:stop()
    if self.active and self.states[self.active] then
      model.animation.get(self.states[self.active].animation or self.active):stop()
    end
    self.enabled = false
    return self
  end

  if rig_registry ~= nil then
    table.insert(rig_registry, graph)
  end
  return graph
end

return rig_animation_graph
