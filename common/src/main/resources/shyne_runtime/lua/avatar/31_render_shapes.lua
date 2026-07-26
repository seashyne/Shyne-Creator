-- A polyline is a managed collection of safe line tasks. It uses the same task
-- and point budgets as ordinary render.line calls.
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

function render.update(id, patch)
  local task = render._tasks[id]
  if task == nil then return false end
  task.options = render_merge(task.options, patch)
  return render_push(id, task.kind, task.options)
end
function render.show(id) return render.update(id, { visible = true }) end
function render.hide(id) return render.update(id, { visible = false }) end
function render.task(id)
  local handle = { id = id }
  function handle:update(patch) return render.update(self.id, patch) end
  function handle:show() return render.show(self.id) end
  function handle:hide() return render.hide(self.id) end
  function handle:remove() return render.remove(self.id) end
  return handle
end

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

function render.remove(id)
  local children = render._collections[id]
  if children ~= nil then
    render._collections[id] = nil
    for _, child_id in ipairs(children) do render.remove(child_id) end
    return true
  end
  render._tasks[id] = nil
  return _shyne_render_remove(id)
end
function render.clear()
  render._tasks, render._groups, render._collections = {}, {}, {}
  return _shyne_render_clear()
end
function render.screen() return _shyne_render_screen() end
function render.stats() return _shyne_render_stats() end
function render.on_frame(callback) return events.on("render", callback) end

events = {
  api_version = "2.0", _handlers = {}, _internal = {},
  context = {
    SHYNE_GUI = "SHYNE_GUI", MINECRAFT_GUI = "MINECRAFT_GUI", FIRST_PERSON = "FIRST_PERSON",
    RENDER = "RENDER", WORLD = "WORLD", OTHER = "OTHER"
  }
}
local function event_name(name) return string.lower(tostring(name or "")) end
function events.on(name, callback)
  if type(callback) ~= "function" then error("events.on requires a function", 2) end
  name = event_name(name)
  events._handlers[name] = events._handlers[name] or {}
  table.insert(events._handlers[name], callback)
  return callback
end
function events.off(name, callback)
  local handlers = events._handlers[event_name(name)] or {}
  for i = #handlers, 1, -1 do if handlers[i] == callback then table.remove(handlers, i) end end
end
function events.once(name, callback)
  if type(callback) ~= "function" then error("events.once requires a function", 2) end
  local wrapper
  wrapper = function(payload)
    events.off(name, wrapper)
    return callback(payload)
  end
  return events.on(name, wrapper)
end
function events.clear(name)
  if name == nil then events._handlers = {} else events._handlers[event_name(name)] = nil end
end
function events._dispatch(name, payload)
  name = event_name(name)
  payload = payload or { type = name }
  for index, callback in ipairs(events._internal[name] or {}) do
    local ok, message = pcall(callback, payload)
    if not ok then _shyne_report_error("event_internal", name .. "#" .. tostring(index), tostring(message)) end
  end
  -- Snapshot the handler list so callbacks can safely subscribe/unsubscribe while dispatching.
  local handlers = {}
  for index, callback in ipairs(events._handlers[name] or {}) do handlers[index] = callback end
  for index, callback in ipairs(handlers) do
    local ok, message = pcall(callback, payload)
    if not ok then _shyne_report_error("event", name .. "#" .. tostring(index), tostring(message)) end
  end
end

task = { _next_id = 0, _entries = {}, limit = 128 }
local function task_add(delay, interval, callback, repeating)
  if type(callback) ~= "function" then error("task callback must be a function", 3) end
  if task.pending() >= task.limit then error("task limit reached (" .. tostring(task.limit) .. ")", 3) end
  task._next_id = task._next_id + 1
  local id = task._next_id
  task._entries[id] = {
    remaining = math.max(0, math.floor(tonumber(delay) or 0)),
    interval = math.max(1, math.floor(tonumber(interval) or 1)),
    callback = callback,
    repeating = repeating
  }
  return id
end
function task.after(ticks, callback) return task_add(ticks, 1, callback, false) end
function task.every(ticks, callback, options)
  options = options or {}
  local interval = math.max(1, math.floor(tonumber(ticks) or 1))
  return task_add(options.immediate and 0 or (options.delay or interval), interval, callback, true)
end
function task.cancel(id)
  local existed = task._entries[id] ~= nil
  task._entries[id] = nil
  return existed
end
function task.clear() task._entries = {} end
function task.pending()
  local count = 0
  for _ in pairs(task._entries) do count = count + 1 end
  return count
end
events._internal.tick = events._internal.tick or {}
table.insert(events._internal.tick, function(payload)
  local due = {}
  for id, entry in pairs(task._entries) do
    entry.remaining = entry.remaining - 1
    if entry.remaining <= 0 then table.insert(due, id) end
  end
  table.sort(due)
  for _, id in ipairs(due) do
    local entry = task._entries[id]
    if entry ~= nil then
      local ok, keep = pcall(entry.callback, payload, id)
      if not ok then
        _shyne_report_error("task", tostring(id), tostring(keep))
        task._entries[id] = nil
      elseif entry.repeating and keep ~= false then
        entry.remaining = entry.interval
      else
        task._entries[id] = nil
      end
    end
  end
end)
events._internal.avatar_unload = events._internal.avatar_unload or {}
table.insert(events._internal.avatar_unload, function() task.clear() end)
