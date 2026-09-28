--[[
  vector.lua - Fast 2D and 3D Vector Math for Pure Lua
  Part of Seashyne Libraries (https://github.com/seashyne/Libraries)
  License: MIT
--]]

local Vector = {}
Vector.__index = Vector

function Vector.new(x, y, z)
    return setmetatable({
        x = tonumber(x) or 0,
        y = tonumber(y) or 0,
        z = tonumber(z) or 0
    }, Vector)
end

function Vector.vec2(x, y)
    return Vector.new(x, y, 0)
end

function Vector.vec3(x, y, z)
    return Vector.new(x, y, z)
end

-- Operator overloads
function Vector.__add(a, b)
    if type(b) == "number" then
        return Vector.new(a.x + b, a.y + b, a.z + b)
    end
    return Vector.new(a.x + b.x, a.y + b.y, a.z + b.z)
end

function Vector.__sub(a, b)
    if type(b) == "number" then
        return Vector.new(a.x - b, a.y - b, a.z - b)
    end
    return Vector.new(a.x - b.x, a.y - b.y, a.z - b.z)
end

function Vector.__mul(a, b)
    if type(a) == "number" then
        return Vector.new(a * b.x, a * b.y, a * b.z)
    elseif type(b) == "number" then
        return Vector.new(a.x * b, a.y * b, a.z * b)
    else
        return Vector.new(a.x * b.x, a.y * b.y, a.z * b.z)
    end
end

function Vector.__div(a, b)
    if type(b) == "number" then
        if b == 0 then error("Vector division by zero", 2) end
        return Vector.new(a.x / b, a.y / b, a.z / b)
    else
        return Vector.new(a.x / b.x, a.y / b.y, a.z / b.z)
    end
end

function Vector.__unm(v)
    return Vector.new(-v.x, -v.y, -v.z)
end

function Vector.__eq(a, b)
    return a.x == b.x and a.y == b.y and a.z == b.z
end

function Vector.__tostring(v)
    return string.format("Vector(%g, %g, %g)", v.x, v.y, v.z)
end

-- Methods
function Vector:clone()
    return Vector.new(self.x, self.y, self.z)
end

function Vector:lengthSq()
    return self.x * self.x + self.y * self.y + self.z * self.z
end

function Vector:length()
    return math.sqrt(self:lengthSq())
end

function Vector:normalize()
    local len = self:length()
    if len > 0 then
        return Vector.new(self.x / len, self.y / len, self.z / len)
    end
    return Vector.new(0, 0, 0)
end

function Vector:dot(other)
    return self.x * other.x + self.y * other.y + self.z * other.z
end

function Vector:cross(other)
    return Vector.new(
        self.y * other.z - self.z * other.y,
        self.z * other.x - self.x * other.z,
        self.x * other.y - self.y * other.x
    )
end

function Vector:distanceTo(other)
    local dx = self.x - other.x
    local dy = self.y - other.y
    local dz = self.z - other.z
    return math.sqrt(dx * dx + dy * dy + dz * dz)
end

function Vector:lerp(target, alpha)
    alpha = math.max(0, math.min(1, alpha))
    return Vector.new(
        self.x + (target.x - self.x) * alpha,
        self.y + (target.y - self.y) * alpha,
        self.z + (target.z - self.z) * alpha
    )
end

function Vector:unpack()
    return self.x, self.y, self.z
end

-- Common constants
Vector.zero  = Vector.new(0, 0, 0)
Vector.one   = Vector.new(1, 1, 1)
Vector.up    = Vector.new(0, 1, 0)
Vector.down  = Vector.new(0, -1, 0)
Vector.left  = Vector.new(-1, 0, 0)
Vector.right = Vector.new(1, 0, 0)
Vector.forward = Vector.new(0, 0, 1)
Vector.back    = Vector.new(0, 0, -1)

return setmetatable(Vector, {
    __call = function(_, x, y, z)
        return Vector.new(x, y, z)
    end
})
