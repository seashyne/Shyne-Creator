-- Shyne Lua API Standard 1.1: auto API, capability, vector, scheduler and error example.
shyne.api.require("scheduler", ">=1.1")

local origin = vector.zero()
local target = vector.new(100, 40, 0)
local progress = 0

local function draw_status()
  if not permissions.has("hud_render") then return end
  local position = vector.lerp(origin, target, progress)
  render.text("api_status", {
    text = "Shyne API " .. shyne.api.version .. "  x=" .. tostring(math.floor(position.x)),
    x = 12,
    y = 12,
    color = 0xFF55FFFF,
    shadow = true
  })
end

events.once("entity_init", function()
  draw_status()
end)

task.every(5, function()
  progress = progress + 0.05
  if progress > 1 then progress = 0 end
  draw_status()
end)

events.on("avatar_unload", function()
  render.clear()
end)
