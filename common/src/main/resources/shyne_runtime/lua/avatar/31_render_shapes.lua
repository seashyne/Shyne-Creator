-- ==============================================================================
-- Shyne Avatar Runtime: Render Shapes & Collections (avatar/31_render_shapes.lua)
-- Manages polyline collections, render groups, task handles, screen metrics, and cleanup.
-- ==============================================================================

local render_copy = render._copy or function(v) local r = {}; for k, i in pairs(v or {}) do r[k] = i end; return r end
local render_merge = render._merge or function(t, p) local r = render_copy(t); for k, v in pairs(p or {}) do r[k] = v end; return r end
local render_push = render._push
local render_refresh_all = render._refresh_all or function() end

--- A polyline is a managed collection of connected line segments.
---@param id string Unique polyline ID
---@param options table Polyline options { points = { Vector3, ... }, color, width, etc. }
---@return string|boolean ID if created successfully, false otherwise
function render.polyline(id, options)
  options = options or {}
  render.remove(id)
  local points = options.points or {}
  local children = {}
  for index = 1, #points - 1 do
    local child_id = tostring(id) .. ".segment_" .. tostring(index)
    local child = render_copy(options)
    child.points = nil
    child.from, child.to = points[index], points[index + 1]
    if render.line(child_id, child) == false then
      for _, created in ipairs(children) do render.remove(created) end
      return false
    end
    table.insert(children, child_id)
  end
  render._collections[id] = children
  return id
end

--- Updates an existing rendering task with a patch table.
---@param id string
---@param patch table
---@return boolean
function render.update(id, patch)
  local task = render._tasks[id]
  if task == nil then return false end
  task.options = render_merge(task.options, patch)
  return render_push(id, task.kind, task.options)
end

function render.show(id) return render.update(id, { visible = true }) end
function render.hide(id) return render.update(id, { visible = false }) end

--- Returns an object handle for an existing render task to control visibility and updates.
---@param id string
---@return table Task handle
function render.task(id)
  local handle = { id = id }
  function handle:update(patch) return render.update(self.id, patch) end
  function handle:show() return render.show(self.id) end
  function handle:hide() return render.hide(self.id) end
  function handle:remove() return render.remove(self.id) end
  return handle
end

--- Creates a hierarchical rendering group for cascading position, scale, and opacity transforms.
---@param id string Group ID
---@param options table Group options { position, scale, opacity, z_index, visible, group }
---@return table Group handle
function render.group(id, options)
  render._groups[id] = render_copy(options)
  render_refresh_all()
  local handle = { id = id }
  function handle:update(patch)
    render._groups[self.id] = render_merge(render._groups[self.id], patch)
    render_refresh_all()
    return self
  end
  function handle:show() return self:update({ visible = true }) end
  function handle:hide() return self:update({ visible = false }) end
  function handle:remove()
    render._groups[self.id] = nil
    render_refresh_all()
  end
  return handle
end

--- Removes a render task, polyline, or collection by its ID.
---@param id string
---@return boolean
function render.remove(id)
  local children = render._collections[id]
  if children ~= nil then
    render._collections[id] = nil
    for _, child_id in ipairs(children) do render.remove(child_id) end
    return true
  end
  render._tasks[id] = nil
  return _shyne_render_remove and _shyne_render_remove(id) or true
end

--- Clears all active render tasks, groups, and collections.
---@return boolean
function render.clear()
  render._tasks, render._groups, render._collections = {}, {}, {}
  return _shyne_render_clear and _shyne_render_clear() or true
end

--- Returns current screen dimensions and GUI scale: { width, height, gui_scale }.
---@return table
function render.screen()
  if _shyne_render_screen then return _shyne_render_screen() end
  return { width = 1920, height = 1080, gui_scale = 2, ready = true }
end

--- Returns render performance metrics and task budget stats.
---@return table
function render.stats()
  if _shyne_render_stats then return _shyne_render_stats() end
  return { tasks = 0, limit = 128 }
end

--- Registers a frame render callback (shortcut for events.on("render", callback)).
---@param callback function
---@return function
function render.on_frame(callback) return events.on("render", callback) end
