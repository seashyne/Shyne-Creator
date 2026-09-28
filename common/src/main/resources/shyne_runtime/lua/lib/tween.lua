-- tween.lua - Tweening library for Lua
-- Copyright (c) 2014 Enrique García Cota (kikito) (MIT License)

local tween = {
  _VERSION     = '2.1.1',
  _DESCRIPTION = 'tweening in Lua',
  _URL         = 'https://github.com/kikito/tween.lua',
  _LICENSE     = 'MIT'
}

-- Easing functions
-- t = elapsed time, b = begin value, c = change in value, d = duration
local function linear(t, b, c, d) return c * t / d + b end

local function inQuad(t, b, c, d) return c * (t / d) ^ 2 + b end
local function outQuad(t, b, c, d) t = t / d; return -c * t * (t - 2) + b end
local function inOutQuad(t, b, c, d)
  t = t / d * 2
  if t < 1 then return c / 2 * t ^ 2 + b end
  return -c / 2 * ((t - 1) * (t - 3) - 1) + b
end

local function inCubic(t, b, c, d) return c * (t / d) ^ 3 + b end
local function outCubic(t, b, c, d) return c * ((t / d - 1) ^ 3 + 1) + b end
local function inOutCubic(t, b, c, d)
  t = t / d * 2
  if t < 1 then return c / 2 * t ^ 3 + b end
  return c / 2 * ((t - 2) ^ 3 + 2) + b
end

local function inSine(t, b, c, d) return -c * math.cos(t / d * (math.pi / 2)) + c + b end
local function outSine(t, b, c, d) return c * math.sin(t / d * (math.pi / 2)) + b end
local function inOutSine(t, b, c, d) return -c / 2 * (math.cos(math.pi * t / d) - 1) + b end

local function inExpo(t, b, c, d) return t == 0 and b or c * 2 ^ (10 * (t / d - 1)) + b end
local function outExpo(t, b, c, d) return t == d and b + c or c * (-(2 ^ (-10 * t / d)) + 1) + b end
local function inOutExpo(t, b, c, d)
  if t == 0 then return b end
  if t == d then return b + c end
  t = t / d * 2
  if t < 1 then return c / 2 * 2 ^ (10 * (t - 1)) + b end
  return c / 2 * (-(2 ^ (-10 * (t - 1))) + 2) + b
end

local function inCirc(t, b, c, d) t = t / d; return -c * (math.sqrt(1 - t * t) - 1) + b end
local function outCirc(t, b, c, d) t = t / d - 1; return c * math.sqrt(1 - t * t) + b end
local function inOutCirc(t, b, c, d)
  t = t / d * 2
  if t < 1 then return -c / 2 * (math.sqrt(1 - t * t) - 1) + b end
  t = t - 2
  return c / 2 * (math.sqrt(1 - t * t) + 1) + b
end

local function outBounce(t, b, c, d)
  t = t / d
  if t < 1 / 2.75 then return c * (7.5625 * t * t) + b
  elseif t < 2 / 2.75 then t = t - (1.5 / 2.75); return c * (7.5625 * t * t + 0.75) + b
  elseif t < 2.5 / 2.75 then t = t - (2.25 / 2.75); return c * (7.5625 * t * t + 0.9375) + b
  else t = t - (2.625 / 2.75); return c * (7.5625 * t * t + 0.984375) + b end
end
local function inBounce(t, b, c, d) return c - outBounce(d - t, 0, c, d) + b end
local function inOutBounce(t, b, c, d)
  if t < d / 2 then return inBounce(t * 2, 0, c, d) * 0.5 + b end
  return outBounce(t * 2 - d, 0, c, d) * 0.5 + c * 0.5 + b
end

local function inBack(t, b, c, d, s)
  s = s or 1.70158
  t = t / d
  return c * t * t * ((s + 1) * t - s) + b
end
local function outBack(t, b, c, d, s)
  s = s or 1.70158
  t = t / d - 1
  return c * (t * t * ((s + 1) * t + s) + 1) + b
end
local function inOutBack(t, b, c, d, s)
  s = (s or 1.70158) * 1.525
  t = t / d * 2
  if t < 1 then return c / 2 * (t * t * ((s + 1) * t - s)) + b end
  t = t - 2
  return c / 2 * (t * t * ((s + 1) * t + s) + 2) + b
end

local function inElastic(t, b, c, d)
  if t == 0 then return b end
  t = t / d
  if t == 1 then return b + c end
  local p = d * 0.3
  local a = c
  local s = p / 4
  t = t - 1
  return -(a * 2 ^ (10 * t) * math.sin((t * d - s) * (2 * math.pi) / p)) + b
end
local function outElastic(t, b, c, d)
  if t == 0 then return b end
  t = t / d
  if t == 1 then return b + c end
  local p = d * 0.3
  local a = c
  local s = p / 4
  return a * 2 ^ (-10 * t) * math.sin((t * d - s) * (2 * math.pi) / p) + c + b
end
local function inOutElastic(t, b, c, d)
  if t == 0 then return b end
  t = t / d * 2
  if t == 2 then return b + c end
  local p = d * (0.3 * 1.5)
  local a = c
  local s = p / 4
  if t < 1 then
    t = t - 1
    return -0.5 * (a * 2 ^ (10 * t) * math.sin((t * d - s) * (2 * math.pi) / p)) + b
  end
  t = t - 1
  return a * 2 ^ (-10 * t) * math.sin((t * d - s) * (2 * math.pi) / p) * 0.5 + c + b
end

tween.easing = {
  linear = linear,
  inQuad = inQuad, outQuad = outQuad, inOutQuad = inOutQuad,
  inCubic = inCubic, outCubic = outCubic, inOutCubic = inOutCubic,
  inSine = inSine, outSine = outSine, inOutSine = inOutSine,
  inExpo = inExpo, outExpo = outExpo, inOutExpo = inOutExpo,
  inCirc = inCirc, outCirc = outCirc, inOutCirc = inOutCirc,
  inBounce = inBounce, outBounce = outBounce, inOutBounce = inOutBounce,
  inBack = inBack, outBack = outBack, inOutBack = inOutBack,
  inElastic = inElastic, outElastic = outElastic, inOutElastic = inOutElastic
}

local Tween = {}
Tween.__index = Tween

function Tween:set(clock)
  self.clock = math.max(0, math.min(self.duration, clock))
  for key, targetVal in pairs(self.target) do
    local initialVal = self.initial[key]
    if initialVal ~= nil then
      self.subject[key] = self.easing(self.clock, initialVal, targetVal - initialVal, self.duration)
    end
  end
  return self.clock >= self.duration
end

function Tween:reset()
  return self:set(0)
end

function Tween:update(dt)
  return self:set(self.clock + dt)
end

function tween.new(duration, subject, target, easing)
  assert(type(duration) == "number" and duration > 0, "duration must be a positive number")
  assert(type(subject) == "table", "subject must be a table")
  assert(type(target) == "table", "target must be a table")
  easing = easing or "linear"
  if type(easing) == "string" then
    easing = tween.easing[easing]
    assert(type(easing) == "function", "unknown easing function")
  end

  local initial = {}
  for k, _ in pairs(target) do
    initial[k] = subject[k] or 0
  end

  return setmetatable({
    duration = duration,
    subject = subject,
    target = target,
    initial = initial,
    easing = easing,
    clock = 0
  }, Tween)
end

return tween
