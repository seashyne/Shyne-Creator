-- ==============================================================================
-- Shyne Rig Core Coordinator (rig/rig_core.lua)
-- Manages controller registration, execution lifecycle, and internal event hooks
-- for Shyne Native Rig API 1.3.
-- ==============================================================================

local rig_core = {
  _controllers = {}
}

--- Registers internal tick and avatar unload event listeners for rig controllers.
--- Internal handlers run before user script callbacks so scripts receive stable physics state.
function rig_core.init_event_hooks()
  events._internal = events._internal or {}

  -- Internal Tick: update all registered controllers safely
  events._internal.tick = events._internal.tick or {}
  table.insert(events._internal.tick, function()
    for _, controller in ipairs(rig_core._controllers) do
      local ok, message = pcall(function() controller:update() end)
      if not ok then
        _shyne_report_error("rig", "controller", tostring(message))
      end
    end
  end)

  -- Internal Unload: clear all registered controllers to prevent memory leaks
  events._internal.avatar_unload = events._internal.avatar_unload or {}
  table.insert(events._internal.avatar_unload, function()
    rig_core._controllers = {}
  end)
end

return rig_core
