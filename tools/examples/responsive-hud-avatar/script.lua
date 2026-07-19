local hud = nil
local last_width = -1

events.on("entity_init", function()
  hud = render.group("responsive", { x = 8, y = 8, z_index = 100 })
  render.rect("responsive.bg", {
    group = "responsive", x = 0, y = 0, width = 104, height = 28,
    color = 0xC0000000
  })
  render.outline("responsive.edge", {
    group = "responsive", x = 0, y = 0, width = 104, height = 28,
    thickness = 1, color = 0xFF55FFFF, z_index = 1
  })
  render.text("responsive.label", {
    group = "responsive", x = 8, y = 9, text = "Responsive HUD",
    color = 0xFFFFFFFF, shadow = true, z_index = 2
  })
end)

render.on_frame(function()
  local screen = render.screen()
  if screen.ready and screen.width ~= last_width then
    last_width = screen.width
    hud:update({ x = math.floor((screen.width - 104) / 2) })
  end
end)

events.on("avatar_unload", function()
  render.clear()
end)
