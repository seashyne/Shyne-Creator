--[[
  timer.lua - Original Lightweight Game & Animation Timer
  Author: Seashyne (https://github.com/seashyne/Libraries)
  License: MIT
--]]

local Timer = {}
Timer.__index = Timer

function Timer.new()
    return setmetatable({
        _tasks = {},
        _nextId = 1
    }, Timer)
end

function Timer:after(delay, fn)
    local id = self._nextId
    self._nextId = self._nextId + 1
    self._tasks[id] = {
        clock = 0,
        delay = math.max(0, tonumber(delay) or 0),
        fn = fn,
        repeats = false
    }
    return id
end

function Timer:every(interval, fn, limit)
    local id = self._nextId
    self._nextId = self._nextId + 1
    self._tasks[id] = {
        clock = 0,
        delay = math.max(0.0001, tonumber(interval) or 0.1),
        fn = fn,
        repeats = true,
        count = 0,
        limit = limit
    }
    return id
end

function Timer:cancel(id)
    if self._tasks[id] then
        self._tasks[id] = nil
        return true
    end
    return false
end

function Timer:clear()
    self._tasks = {}
end

function Timer:update(dt)
    dt = math.max(0, tonumber(dt) or 0)
    for id, task in pairs(self._tasks) do
        task.clock = task.clock + dt
        if task.clock >= task.delay then
            task.fn()
            if task.repeats then
                task.clock = task.clock - task.delay
                task.count = task.count + 1
                if task.limit and task.count >= task.limit then
                    self._tasks[id] = nil
                end
            else
                self._tasks[id] = nil
            end
        end
    end
end

-- Default shared instance
local defaultTimer = Timer.new()

-- Module table supporting both dot syntax timer.after() and colon timer:after()
local module = {
    new = Timer.new,
    after = function(a, b, ...)
        if type(a) == "table" and a._tasks then return a:after(b, ...) end
        return defaultTimer:after(a, b)
    end,
    every = function(a, b, c, ...)
        if type(a) == "table" and a._tasks then return a:every(b, c, ...) end
        return defaultTimer:every(a, b, c)
    end,
    cancel = function(a, b)
        if type(a) == "table" and a._tasks then return a:cancel(b) end
        return defaultTimer:cancel(a)
    end,
    clear = function(a)
        if type(a) == "table" and a._tasks then return a:clear() end
        return defaultTimer:clear()
    end,
    update = function(a, b)
        if type(a) == "table" and a._tasks then return a:update(b) end
        return defaultTimer:update(a)
    end
}

return setmetatable(module, {
    __call = function(_) return Timer.new() end
})
