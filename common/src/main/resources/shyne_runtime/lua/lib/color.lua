--[[
  color.lua - Original Color & RGBA Math Utilities
  Author: Seashyne (https://github.com/seashyne/Libraries)
  License: MIT
--]]

local Color = {}
Color.__index = Color

function Color.new(r, g, b, a)
    return setmetatable({
        r = math.max(0, math.min(1, tonumber(r) or 1)),
        g = math.max(0, math.min(1, tonumber(g) or 1)),
        b = math.max(0, math.min(1, tonumber(b) or 1)),
        a = math.max(0, math.min(1, tonumber(a) or 1))
    }, Color)
end

function Color.fromHex(hex)
    if type(hex) == "number" then
        local a = bit32 and bit32.band(bit32.rshift(hex, 24), 0xFF) or math.floor(hex / 16777216) % 256
        local r = bit32 and bit32.band(bit32.rshift(hex, 16), 0xFF) or math.floor(hex / 65536) % 256
        local g = bit32 and bit32.band(bit32.rshift(hex, 8), 0xFF) or math.floor(hex / 256) % 256
        local b = bit32 and bit32.band(hex, 0xFF) or hex % 256
        if a == 0 and hex <= 0xFFFFFF then a = 255 end
        return Color.new(r / 255, g / 255, b / 255, a / 255)
    end
    hex = tostring(hex):gsub("[%s#]", "")
    if #hex == 6 then
        local r = tonumber(hex:sub(1, 2), 16) or 255
        local g = tonumber(hex:sub(3, 4), 16) or 255
        local b = tonumber(hex:sub(5, 6), 16) or 255
        return Color.new(r / 255, g / 255, b / 255, 1)
    elseif #hex == 8 then
        local r = tonumber(hex:sub(1, 2), 16) or 255
        local g = tonumber(hex:sub(3, 4), 16) or 255
        local b = tonumber(hex:sub(5, 6), 16) or 255
        local a = tonumber(hex:sub(7, 8), 16) or 255
        return Color.new(r / 255, g / 255, b / 255, a / 255)
    end
    return Color.new(1, 1, 1, 1)
end

function Color:lerp(target, t)
    t = math.max(0, math.min(1, t))
    return Color.new(
        self.r + (target.r - self.r) * t,
        self.g + (target.g - self.g) * t,
        self.b + (target.b - self.b) * t,
        self.a + (target.a - self.a) * t
    )
end

function Color:toHex()
    return string.format("#%02X%02X%02X",
        math.floor(self.r * 255 + 0.5),
        math.floor(self.g * 255 + 0.5),
        math.floor(self.b * 255 + 0.5)
    )
end

function Color:toInt()
    local a = math.floor(self.a * 255 + 0.5)
    local r = math.floor(self.r * 255 + 0.5)
    local g = math.floor(self.g * 255 + 0.5)
    local b = math.floor(self.b * 255 + 0.5)
    return a * 16777216 + r * 65536 + g * 256 + b
end

function Color:unpack()
    return self.r, self.g, self.b, self.a
end

return setmetatable(Color, {
    __call = function(_, r, g, b, a)
        return Color.new(r, g, b, a)
    end
})
