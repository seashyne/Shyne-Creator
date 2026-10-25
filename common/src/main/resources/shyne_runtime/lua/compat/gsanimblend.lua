-- ==============================================================================
-- Shyne Avatar Runtime: GSAnimBlend Compatibility Shim (compat/gsanimblend.lua)
-- Provides built-in compatibility for Figura's GSAnimBlend library.
-- ==============================================================================

local GSAnimBlend = {
  _version = "2.1.0-shyne",
  callback = {}
}

GSAnimBlend.curve = {
  linear = function(t) return t end,
  easeInSine = function(t) return 1 - math.cos((t * math.pi) * 0.5) end,
  easeOutSine = function(t) return math.sin((t * math.pi) * 0.5) end,
  easeInOutSine = function(t) return -(math.cos(math.pi * t) - 1) * 0.5 end,
  easeInQuad = function(t) return t * t end,
  easeOutQuad = function(t) return 1 - (1 - t) * (1 - t) end,
  easeInOutQuad = function(t) return t < 0.5 and 2 * t * t or 1 - ((-2 * t + 2) ^ 2) * 0.5 end,
  easeInCubic = function(t) return t * t * t end,
  easeOutCubic = function(t) return 1 - ((1 - t) ^ 3) end,
  easeInOutCubic = function(t) return t < 0.5 and 4 * t * t * t or 1 - ((-2 * t + 2) ^ 3) * 0.5 end
}

function GSAnimBlend.blendTime(anim, in_ticks, out_ticks)
  if type(anim) == "table" and anim.blendTime then
    return anim:blendTime(in_ticks, out_ticks)
  end
  return anim
end

function GSAnimBlend.setBlendTime(anim, in_ticks, out_ticks)
  return GSAnimBlend.blendTime(anim, in_ticks, out_ticks)
end

function GSAnimBlend.blendCurve(anim, curve_fn)
  if type(anim) == "table" and anim.blendCurve then
    return anim:blendCurve(curve_fn)
  end
  return anim
end

function GSAnimBlend.onBlend(anim, fn)
  if type(anim) == "table" and anim.onBlend then
    return anim:onBlend(fn)
  end
  return anim
end

function GSAnimBlend.safeHappy()
  return GSAnimBlend
end

setmetatable(GSAnimBlend, {
  __index = function(self, key)
    if GSAnimBlend.curve[key] then return GSAnimBlend.curve[key] end
    return rawget(self, key)
  end,
  __call = function(self, ...)
    return self
  end
})

_G.GSAnimBlend = GSAnimBlend
return GSAnimBlend
