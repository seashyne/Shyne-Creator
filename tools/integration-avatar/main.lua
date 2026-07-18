avatar.hide_vanilla(true)
avatar.texture.sync("manifest")
avatar.network.online(true)
avatar.nameplate.configure({ text = "Shyne API Test", visible = true })

local root = model.root
local marker = root.Marker
local idle = model.animation.get("idle"):loop(true):weight(1):priority(0):fade_in(8):fade_out(8)
local pulse = model.animation.get("pulse"):loop(true):weight(0.65):priority(10):speed(1.25):fade_in(6):fade_out(6):mask({ "Marker" }):additive(true)
local glow = false

events.on("entity_init", function()
  idle:play()
  pulse:play()
  marker:color(0.25, 0.85, 1):opacity(0.8)
end)

events.on("tick", function(event)
  local wet = minecraft.player.in_water()
  marker:emissive(glow or wet)
  state.sync("wet", wet)
end)

events.on("microphone", function(event)
  local amount = event.speaking and (1 + event.level * 0.15) or 1
  marker:scale(amount)
end)

ui.action({
  id = "toggle_glow",
  title = "Glow",
  icon = "star",
  toggle = true,
  close = false,
  on_toggle = function(enabled)
    glow = enabled
    marker:emissive(enabled)
    sound.play("minecraft:block.amethyst_block.chime", { volume = 0.6, pitch = enabled and 1.2 or 0.8 })
  end
})

input.bind("pulse", {
  title = "Pulse animation",
  key = input.key.p,
  on_press = function()
    pulse:restart()
    particle.spawn("minecraft:bubble", minecraft.player.position(), { velocity = vector.new(0, 0.08, 0) })
  end
})

events.on("avatar_unload", function()
  avatar.hide_vanilla(false)
end)
