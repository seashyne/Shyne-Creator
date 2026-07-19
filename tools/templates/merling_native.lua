-- Shyne-native Merling controller.
-- Coordinates the model's tail form with the matching Blockbench animations;
-- preserved source modules are reference material and are not executed.

avatar.hide_vanilla(true)
avatar.camera.configure({ local_only = true, first_person_masking = true, hide_head = true })
avatar.texture.sync("manifest")
avatar.network.online(true)

local DRY_TICKS = 400
local SMALL_TAIL_SCALE = 0.5

local tail_mode = state.get("merling.tail_mode", "auto")
local small_tail = state.get("merling.small_tail", true)
local dry_ticks = 0
local tail_amount = small_tail and SMALL_TAIL_SCALE or 0
local leg_amount = 1
local ear_amount = 0
local current_locomotion = nil

local function clamp(value, minimum, maximum)
  return math.max(minimum, math.min(maximum, value))
end

local function approach(current, target, speed)
  if math.abs(target - current) < 0.001 then return target end
  return current + (target - current) * speed
end

local function first_animation(names)
  for _, name in ipairs(names) do
    if model.animation.exists(name) then return name end
  end
  return nil
end

local function use_locomotion(name)
  if name == current_locomotion then return end
  if current_locomotion then
    model.animation.get(current_locomotion):fade_out(7):stop()
  end
  current_locomotion = name
  if name then
    model.animation.get(name):loop(true):transition(7):priority(0):play()
  end
end

local function play_one_shot(names, priority)
  local name = first_animation(names)
  if name then
    model.animation.get(name):loop(false):fade_in(3):fade_out(5):priority(priority or 30):play()
  end
end

local function set_tail_mode(mode)
  tail_mode = mode
  state.set("merling.tail_mode", mode)
end

local function update_form()
  local wet = minecraft.player.wet() or minecraft.player.in_water()
    or minecraft.player.underwater() or minecraft.player.in_lava()

  if tail_mode == "tail" then
    dry_ticks = DRY_TICKS
  elseif tail_mode == "legs" then
    dry_ticks = 0
  elseif wet then
    dry_ticks = DRY_TICKS
  else
    dry_ticks = math.max(0, dry_ticks - 1)
  end

  local wet_amount = clamp(dry_ticks / DRY_TICKS, 0, 1)
  local target_tail = wet_amount
  local target_legs = wet_amount >= 0.75 and 0 or 1
  local target_ears = wet_amount

  -- Keep the configurable small tail on land, matching the original design.
  if small_tail then target_tail = math.max(target_tail, SMALL_TAIL_SCALE) end

  tail_amount = approach(tail_amount, target_tail, 0.2)
  leg_amount = approach(leg_amount, target_legs, 0.35)
  ear_amount = approach(ear_amount, target_ears, 0.2)

  model.Tail1:scale(tail_amount)
  model.LeftLeg:scale(leg_amount)
  model.RightLeg:scale(leg_amount)
  model.DorsalEar:scale(ear_amount)
  model.LeftEar:scale(ear_amount)
  model.RightEar:scale(ear_amount)

  -- Parameters are evaluated by Shyne's animation engine, then synchronized
  -- with remote players as part of the Avatar snapshot.
  local velocity = minecraft.player.velocity()
  local speed = clamp(math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z) * 20, 0, 2)
  local pitch = clamp(-velocity.y * 35, -20, 20)
  model.animation.parameter("tail_strength", 1 + speed)
  model.animation.parameter("strength", 1 + speed)
  model.animation.parameter("pitch", pitch)
  model.animation.parameter("yaw", 0)
  model.animation.parameter("roll", 0)
  model.animation.parameter("headY", minecraft.player.rotation().x)
  model.animation.parameter("height", wet_amount)
  model.animation.parameter("wet", wet and 1 or 0)
  model.animation.parameter("tail", tail_amount >= 0.75 and 1 or 0)
  model.animation.parameter("shark", 0)
end

local function select_locomotion()
  local large_tail = tail_amount >= 0.75
  local pose = minecraft.player.pose()

  if minecraft.player.sleeping() then
    return first_animation({ "sleep" })
  end
  if minecraft.player.vehicle() then
    return first_animation(large_tail and { "mountDown", "swim" } or { "small", "stand" })
  end
  if large_tail then
    if minecraft.player.on_ground() and not minecraft.player.swimming() then
      if minecraft.player.crouching() then return first_animation({ "crawl", "stand" }) end
      return first_animation({ "stand", "swim" })
    end
    return first_animation({ "swim", "stand" })
  end
  if small_tail then
    if minecraft.player.swimming() or minecraft.player.fall_flying() or pose == "CRAWLING" then
      return first_animation({ "smallSwim", "small" })
    end
    return first_animation({ "small", "stand" })
  end
  return nil
end

events.on("entity_init", function()
  model.root:visible(true)
  update_form()
end)

events.on("tick", function()
  update_form()
  use_locomotion(select_locomotion())
end)

ui.action({ id = "tail_auto", title = "Tail: Auto", icon = "wave", on_use = function() set_tail_mode("auto") end })
ui.action({ id = "tail_always", title = "Tail: Always", icon = "star", on_use = function() set_tail_mode("tail") end })
ui.action({ id = "tail_legs", title = "Tail: Legs", icon = "heart", on_use = function() set_tail_mode("legs") end })
ui.action({
  id = "small_tail",
  title = "Toggle Small Tail",
  icon = "spark",
  on_use = function()
    small_tail = not small_tail
    state.set("merling.small_tail", small_tail)
  end
})
ui.action({ id = "twirl", title = "Twirl", icon = "spark", on_use = function() play_one_shot({ "twirl" }, 40) end })
ui.action({ id = "sing", title = "Sing", icon = "star", on_use = function() play_one_shot({ "sing" }, 40) end })

input.bind("twirl", {
  title = "Twirl",
  key = input.key.r,
  on_press = function() play_one_shot({ "twirl" }, 40) end
})

events.on("avatar_unload", function()
  model.Tail1:reset()
  model.LeftLeg:reset()
  model.RightLeg:reset()
  model.DorsalEar:reset()
  model.LeftEar:reset()
  model.RightEar:reset()
end)
