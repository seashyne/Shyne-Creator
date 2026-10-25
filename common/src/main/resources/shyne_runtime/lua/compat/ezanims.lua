-- ==============================================================================
-- Shyne Avatar Runtime: EZAnims Compatibility Shim (compat/ezanims.lua)
-- Provides built-in compatibility for Figura's EZAnims state machine library.
-- ==============================================================================

local EZAnims = {
  _version = "1.2.0-shyne",
  _blend_time = 4,
  _states = {},
  _exclusivity = {},
  _registered_sources = {}
}
EZAnims.__index = EZAnims

local STATE_ALIASES = {
  idle = { "idle", "stand", "standing" },
  walk = { "walk", "walking" },
  sprint = { "sprint", "sprinting", "run", "running" },
  crouch = { "crouch", "crouching", "sneak", "sneaking" },
  crouchwalk = { "crouchwalk", "crouch_walk", "sneakwalk", "sneak_walk" },
  jump = { "jump", "jumping" },
  fall = { "fall", "falling" },
  swim = { "swim", "swimming" },
  fly = { "fly", "flying", "creativefly" },
  elytra = { "elytra", "gliding", "fallfly" },
  sit = { "sit", "sitting", "ride", "riding", "mount" },
  sleep = { "sleep", "sleeping" },
  attack = { "attack", "attackR", "swing" }
}

local function resolve_anim_from_source(source, keys)
  if type(source) ~= "table" then return nil end
  for _, k in ipairs(keys) do
    local candidate = source[k]
    if type(candidate) == "table" and candidate.play then
      if model and model.animation and model.animation.exists and model.animation.exists(candidate.name or k) then
        return candidate
      end
    end
  end
  return nil
end

function EZAnims:setBlendTime(ticks)
  self._blend_time = math.max(0, tonumber(ticks) or 4)
  return self
end

function EZAnims:blendTime(ticks)
  return self:setBlendTime(ticks)
end

function EZAnims:addExclusivity(...)
  table.insert(self._exclusivity, { ... })
  return self
end

function EZAnims:addState(name, anim, condition_fn)
  if name then
    self._states[tostring(name)] = {
      anim = anim,
      condition = condition_fn,
      active = false
    }
  end
  return self
end

function EZAnims:setState(name, active)
  local st = self._states[tostring(name)]
  if st then
    st.active = active and true or false
    if st.anim and st.anim.setPlaying then
      st.anim:setPlaying(st.active)
    end
  end
  return self
end

function EZAnims:getState(name)
  local st = self._states[tostring(name)]
  return st and st.active or false
end

function EZAnims:register(anim_table)
  if type(anim_table) == "table" then
    table.insert(self._registered_sources, anim_table)
  elseif animations then
    table.insert(self._registered_sources, animations)
  end
  return self
end

function EZAnims:init(anim_table)
  return self:register(anim_table)
end

local active_locomotion_anim = nil

local function select_locomotion_key()
  if not player or not player.isLoaded or not player:isLoaded() then return "idle" end
  local pose = player.getPose and player:getPose() or "STANDING"
  local vel = player.getVelocity and player:getVelocity() or { x = 0, y = 0, z = 0 }
  local vx = tonumber(vel.x or vel[1]) or 0
  local vy = tonumber(vel.y or vel[2]) or 0
  local vz = tonumber(vel.z or vel[3]) or 0
  local hspeed = math.sqrt(vx * vx + vz * vz)
  local on_ground = player.isOnGround and player:isOnGround() or true
  local crouching = (player.isCrouching and player:isCrouching()) or pose == "CROUCHING"
  local sprinting = player.isSprinting and player:isSprinting() or false
  local swimming = (player.isUnderwater and player:isUnderwater()) or pose == "SWIMMING"
  local gliding = (player.isGliding and player:isGliding()) or pose == "FALL_FLYING"
  local sleeping = pose == "SLEEPING"
  local vehicle = player.getVehicle and player:getVehicle() ~= nil

  if sleeping then return "sleep" end
  if vehicle then return "sit" end
  if gliding then return "elytra" end
  if swimming then return "swim" end
  if not on_ground then
    return vy > 0.05 and "jump" or "fall"
  end
  if crouching then
    return hspeed > 0.015 and "crouchwalk" or "crouch"
  end
  if sprinting and hspeed > 0.05 then return "sprint" end
  if hspeed > 0.015 then return "walk" end
  return "idle"
end

if events and events.TICK and events.TICK.register then
  events.TICK:register(function()
    for _, st in pairs(EZAnims._states) do
      if type(st.condition) == "function" then
        local should_play = st.condition() and true or false
        if should_play ~= st.active then
          st.active = should_play
          if st.anim and st.anim.setPlaying then
            if st.anim.blendTime then st.anim:blendTime(EZAnims._blend_time) end
            st.anim:setPlaying(should_play)
          end
        end
      end
    end

    if #EZAnims._registered_sources > 0 then
      local state_key = select_locomotion_key()
      local keys = STATE_ALIASES[state_key] or { state_key }
      local chosen = nil
      for _, src in ipairs(EZAnims._registered_sources) do
        chosen = resolve_anim_from_source(src, keys)
        if not chosen and state_key == "crouchwalk" then
          chosen = resolve_anim_from_source(src, STATE_ALIASES.crouch) or resolve_anim_from_source(src, STATE_ALIASES.walk)
        end
        if chosen then break end
      end
      if chosen ~= active_locomotion_anim then
        if active_locomotion_anim and active_locomotion_anim.stop then
          active_locomotion_anim:stop()
        end
        active_locomotion_anim = chosen
        if active_locomotion_anim and active_locomotion_anim.play then
          if active_locomotion_anim.blendTime then active_locomotion_anim:blendTime(EZAnims._blend_time) end
          if active_locomotion_anim.loop then active_locomotion_anim:loop(true) end
          active_locomotion_anim:play()
        end
      end
    end
  end, "shyne_ezanims_tick")
end

setmetatable(EZAnims, {
  __call = function(self, anim_table)
    return self:register(anim_table)
  end
})

_G.EZAnims = EZAnims
return EZAnims
