-- ==============================================================================
-- SquAPI Compatibility: Core Coordinator (compat/squapi/squapi_core.lua)
-- Orchestrates the 100% native SquAPI compatibility layer on Shyne Core.
-- Provides public globals `squapi` and `SquAPI`, controller tracking, and event hooks.
-- ==============================================================================

squapi = squapi or {
  autoFunctionUpdates = true,
  eyeScale = 1,
  smoothHeadOffset = vector.zero(),
  torsoOffset = vector.zero(),
  doBlink = true,
  wagStrength = 1,
  doBounce = false,
  cancelHeadMovement = false,
  floatPointEnabled = true,
  _controllers = {}
}

SquAPI = squapi

--- Resolves a part proxy or list of part proxies from path or object.
---@param value any Part path string, part proxy, or array of parts
---@return table Array of part proxies
function squapi._parts(value)
  if type(value) == "string" then return { model.part(value) } end
  if type(value) ~= "table" then return { value } end
  local result = {}
  for _, item in ipairs(value) do
    if type(item) == "string" then table.insert(result, model.part(item)) else table.insert(result, item) end
  end
  return result
end

--- Tracks a controller in a specific collection and the global squapi update list.
---@param collection table|nil Target category collection (e.g. squapi.tails, squapi.ears)
---@param controller table Controller instance
---@return table Controller instance
function squapi._track(collection, controller)
  if collection ~= nil then table.insert(collection, controller) end
  table.insert(squapi._controllers, controller)
  return controller
end

--- Wraps a raw controller table with standard enable/disable/start/stop/zero methods.
---@param controller table
---@return table Wrapped controller
function squapi._controller(controller)
  controller.enabled = controller.enabled ~= false
  function controller:enable() self.enabled = true; return self end
  function controller:disable() self.enabled = false; return self end
  function controller:toggle() self.enabled = not self.enabled; return self end
  function controller:setEnabled(value) self.enabled = value == true; return self end
  function controller:start() return self:enable() end
  function controller:stop() return self:disable() end
  function controller:zero() if self.reset ~= nil then return self:reset() end return self end
  function controller:render() return self end
  return controller
end

-- Hook internal events for automated updates
events._internal = events._internal or {}

-- Tick hook: executes when autoFunctionUpdates is enabled
events._internal.tick = events._internal.tick or {}
table.insert(events._internal.tick, function()
  if not squapi.autoFunctionUpdates then return end
  for _, controller in ipairs(squapi._controllers) do
    local ok, message = pcall(function() controller:tick() end)
    if not ok then _shyne_report_error("squapi", "controller", tostring(message)) end
  end
end)

-- Unload hook: cleans up all active controllers
events._internal.avatar_unload = events._internal.avatar_unload or {}
table.insert(events._internal.avatar_unload, function()
  squapi._controllers = {}
end)
