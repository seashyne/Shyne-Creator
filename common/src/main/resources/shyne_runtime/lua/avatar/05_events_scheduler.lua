-- ==============================================================================
-- Shyne Avatar Runtime: Event Bus & Task Scheduler (avatar/05_events_scheduler.lua)
-- Manages synchronous event dispatching, subscription handles, and frame/tick schedulers.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- EVENT BUS (events)
-- ------------------------------------------------------------------------------
events = {
  api_version = "2.0",
  _handlers = {},
  _internal = {},
  context = {
    SHYNE_GUI = "SHYNE_GUI",
    MINECRAFT_GUI = "MINECRAFT_GUI",
    FIRST_PERSON = "FIRST_PERSON",
    RENDER = "RENDER",
    WORLD = "WORLD",
    PORTRAIT = "PORTRAIT",
    SKULL = "SKULL",
    HELD_ITEM = "HELD_ITEM",
    OTHER = "OTHER"
  }
}

local function event_name(name) return string.lower(tostring(name or "")) end

--- Subscribes a callback function to an event.
---@param name string Event name (e.g. "tick", "render", "post_render", "entity_init")
---@param callback function Callback function receiving the event payload table
---@return function The registered callback function (for unregistering)
function events.on(name, callback)
  if type(callback) ~= "function" then error("events.on requires a function", 2) end
  name = event_name(name)
  events._handlers[name] = events._handlers[name] or {}
  table.insert(events._handlers[name], callback)
  return callback
end

--- Unsubscribes a callback function from an event.
---@param name string Event name
---@param callback function The exact callback function previously registered
function events.off(name, callback)
  local handlers = events._handlers[event_name(name)] or {}
  for i = #handlers, 1, -1 do
    if handlers[i] == callback then table.remove(handlers, i) end
  end
end

--- Subscribes a one-shot callback that automatically unregisters after being triggered once.
---@param name string Event name
---@param callback function Callback function
---@return function Wrapper function
function events.once(name, callback)
  if type(callback) ~= "function" then error("events.once requires a function", 2) end
  local wrapper
  wrapper = function(payload)
    events.off(name, wrapper)
    return callback(payload)
  end
  return events.on(name, wrapper)
end

--- Clears all registered callbacks for an event name, or all events if name is nil.
---@param name string|nil
function events.clear(name)
  if name == nil then events._handlers = {} else events._handlers[event_name(name)] = nil end
end

--- Dispatches an event to all internal and public subscribers.
---@param name string Event name
---@param payload table|nil Event payload table
function events._dispatch(name, payload)
  name = event_name(name)
  payload = payload or { type = name }

  -- 1. Internal handlers (run before avatar scripts)
  for index, callback in ipairs(events._internal[name] or {}) do
    local ok, message = pcall(callback, payload)
    if not ok then _shyne_report_error("event_internal", name .. "#" .. tostring(index), tostring(message)) end
  end

  -- 2. Snapshot public handlers to safely allow subscribe/unsubscribe during dispatch
  local handlers = {}
  for index, callback in ipairs(events._handlers[name] or {}) do handlers[index] = callback end
  for index, callback in ipairs(handlers) do
    local ok, message = pcall(callback, payload)
    if not ok then _shyne_report_error("event", name .. "#" .. tostring(index), tostring(message)) end
  end
end

-- ------------------------------------------------------------------------------
-- TASK SCHEDULER (task)
-- ------------------------------------------------------------------------------
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

--- Schedules a callback to run once after a specified number of game ticks.
---@param ticks number Delay in ticks (20 ticks = 1 second)
---@param callback function Callback function
---@return number Unique task ID
function task.after(ticks, callback)
  return task_add(ticks, 1, callback, false)
end

--- Schedules a repeating callback executed at a fixed interval of game ticks.
---@param ticks number Interval in ticks
---@param callback function Callback function
---@param options table|nil Optional { immediate = boolean, delay = number }
---@return number Unique task ID
function task.every(ticks, callback, options)
  options = options or {}
  local interval = math.max(1, math.floor(tonumber(ticks) or 1))
  return task_add(options.immediate and 0 or (options.delay or interval), interval, callback, true)
end

--- Cancels a pending scheduled task by its ID.
---@param id number Task ID
---@return boolean True if task existed and was cancelled
function task.cancel(id)
  local existed = task._entries[id] ~= nil
  task._entries[id] = nil
  return existed
end

--- Cancels all pending scheduled tasks.
function task.clear() task._entries = {} end

--- Returns the number of currently scheduled pending tasks.
---@return number
function task.pending()
  local count = 0
  for _ in pairs(task._entries) do count = count + 1 end
  return count
end

-- Tick hook for advancing task scheduler timers
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

-- Unload hook for clearing scheduled tasks
events._internal.avatar_unload = events._internal.avatar_unload or {}
table.insert(events._internal.avatar_unload, function() task.clear() end)

-- Render hook for forwarding skull context to skull_render
events._internal.render = events._internal.render or {}
table.insert(events._internal.render, function(payload)
  if payload and (payload.context == "SKULL" or payload.is_skull) then
    events._dispatch("skull_render", payload)
  end
end)
