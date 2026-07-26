-- Shyne-native custom aquatic controller.
-- Coordinates an aquatic tail form with matching Blockbench animations;
-- preserved source modules are reference material and are not executed.

avatar.hide_vanilla(false)
avatar.camera.configure({ local_only = false, first_person_masking = false, hide_head = false })
avatar.texture.sync("manifest")
avatar.network.online(true)

local DRY_TICKS = 400
local tail_mode = state.get("custom.tail_mode", state.get("merling.tail_mode", "auto"))
local dry_ticks = 0
local tail_form = false
local current_locomotion = nil
local tail_physics = nil
local wave_time = 0

local function clamp(value, minimum, maximum)
  return math.max(minimum, math.min(maximum, value))
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
  state.set("custom.tail_mode", mode)
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

  tail_form = dry_ticks > 0
  model.Tail1:visible(tail_form)
  model.LeftLeg:visible(not tail_form)
  model.RightLeg:visible(not tail_form)
  model.DorsalEar:visible(tail_form)
  model.LeftEar:visible(tail_form)
  model.RightEar:visible(tail_form)

  -- Parameters are evaluated by Shyne's animation engine, then synchronized
  -- with remote players as part of the Avatar snapshot.
  local velocity = minecraft.player.velocity()
  local speed = clamp(math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z) * 20, 0, 2)
  local pitch = clamp(-velocity.y * 35, -20, 20)
  wave_time = wave_time + clamp(speed * 0.00025 + 0.0005, -0.0045, 0.0045)
  model.animation.parameter("time", wave_time)
  model.animation.parameter("tail_strength", 1 + speed)
  model.animation.parameter("strength", 1 + speed)
  model.animation.parameter("pitch", pitch)
  model.animation.parameter("yaw", 0)
  model.animation.parameter("roll", 0)
  model.animation.parameter("headY", minecraft.player.rotation().x)
  model.animation.parameter("height", tail_form and 1 or 0)
  model.animation.parameter("wet", wet and 1 or 0)
  model.animation.parameter("tail", tail_form and 1 or 0)
  model.animation.parameter("shark", 0)
  model.animation.parameter("normal", 1)
end

local function select_locomotion()
  local pose = minecraft.player.pose()

  if minecraft.player.sleeping() then
    return first_animation({ "sleep" })
  end
  if minecraft.player.vehicle() then
    return first_animation(tail_form and { "mountDown", "swim" } or { "small", "stand" })
  end
  if tail_form then
    if minecraft.player.on_ground() and not minecraft.player.swimming() then
      if minecraft.player.crouching() then return first_animation({ "crawl", "stand" }) end
      return first_animation({ "stand", "swim" })
    end
    return first_animation({ "swim", "stand" })
  end
  if minecraft.player.swimming() or minecraft.player.fall_flying() or pose == "CRAWLING" then
    return first_animation({ "smallSwim", "small" })
  end
  return first_animation({ "small", "stand" })
end

events.on("entity_init", function()
  model.root:visible(true):vanilla_parent("BODY")
  -- This stays additive to the imported tail animations. It is safe when a
  -- model has fewer tail bones: unresolved paths simply render nothing.
  tail_physics = rig.chain({ "model.Tail1", "model.Tail2", "model.Tail3", "model.Tail4" }, {
    stiffness = 0.16, damping = 0.80, falloff = 0.10,
    gravity = vector.new(9, 0, 0), motion = vector.new(-36, 0, 0), limit = vector.new(30, 22, 18)
  })
  update_form()
end)

events.on("tick", function()
  update_form()
  use_locomotion(select_locomotion())
end)

ui.action({ id = "tail_auto", title = "Tail: Auto", icon = "wave", on_use = function() set_tail_mode("auto") end })
ui.action({ id = "tail_always", title = "Tail: Always", icon = "star", on_use = function() set_tail_mode("tail") end })
ui.action({ id = "tail_legs", title = "Tail: Legs", icon = "heart", on_use = function() set_tail_mode("legs") end })
ui.action({ id = "twirl", title = "Twirl", icon = "spark", on_use = function() play_one_shot({ "twirl" }, 40) end })
ui.action({ id = "sing", title = "Sing", icon = "star", on_use = function() play_one_shot({ "sing" }, 40) end })

input.bind("twirl", {
  title = "Twirl",
  key = input.key.r,
  on_press = function() play_one_shot({ "twirl" }, 40) end
})

events.on("avatar_unload", function()
  if tail_physics then tail_physics:reset() end
  model.Tail1:reset()
  model.LeftLeg:reset()
  model.RightLeg:reset()
  model.DorsalEar:reset()
  model.LeftEar:reset()
  model.RightEar:reset()
end)
