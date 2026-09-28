--[[
  signal.lua - Lightweight Event / Observer Dispatcher for Pure Lua
  Part of Seashyne Libraries (https://github.com/seashyne/Libraries)
  License: MIT
--]]

local Signal = {}
Signal.__index = Signal

function Signal.new()
    return setmetatable({
        _listeners = {}
    }, Signal)
end

function Signal:connect(fn)
    if type(fn) ~= "function" then
        error("Signal:connect expects a function", 2)
    end
    table.insert(self._listeners, fn)
    -- Return disconnect handle function
    local disconnected = false
    return function()
        if disconnected then return end
        disconnected = true
        for i = #self._listeners, 1, -1 do
            if self._listeners[i] == fn then
                table.remove(self._listeners, i)
                break
            end
        end
    end
end

function Signal:once(fn)
    local disconnect
    disconnect = self:connect(function(...)
        disconnect()
        fn(...)
    end)
    return disconnect
end

function Signal:fire(...)
    for i = 1, #self._listeners do
        local fn = self._listeners[i]
        if fn then
            fn(...)
        end
    end
end

function Signal:clear()
    self._listeners = {}
end

function Signal:count()
    return #self._listeners
end

return setmetatable(Signal, {
    __call = function(_)
        return Signal.new()
    end
})
