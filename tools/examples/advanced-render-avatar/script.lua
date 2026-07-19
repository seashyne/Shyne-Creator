local cyan = 0xFF55FFFF
local card = nil
local title = nil
local tick_count = 0
local last_width = -1

events.on("entity_init", function()
  -- A group moves, fades, layers, and hides all child tasks together.
  card = render.group("status_card", { x = 12, y = 12, opacity = 0.92, z_index = 20 })
  render.rect("card.background", {
    group = "status_card", x = 0, y = 0, width = 168, height = 66,
    color = 0xE5101728, z_index = 0
  })
  render.outline("card.border", {
    group = "status_card", x = 0, y = 0, width = 168, height = 66,
    thickness = 2, color = cyan, z_index = 1
  })
  render.text("card.title", {
    group = "status_card", text = "SHYNE CUSTOM RENDER", x = 10, y = 9,
    color = cyan, shadow = true, z_index = 2
  })
  render.text("card.status", {
    group = "status_card", text = "Runtime ready", x = 10, y = 25,
    color = 0xFFFFFFFF, shadow = true, z_index = 2
  })
  render.polyline("card.graph", {
    group = "status_card",
    points = {
      vector.new(10, 53, 0), vector.new(35, 42, 0), vector.new(60, 48, 0),
      vector.new(90, 35, 0), vector.new(120, 44, 0), vector.new(156, 30, 0)
    },
    color = cyan, width = 2, z_index = 2
  })
  title = render.task("card.status")
end)

events.on("tick", function()
  tick_count = tick_count + 1
  local screen = render.screen()
  if screen.ready and screen.width ~= last_width then
    last_width = screen.width
    -- Keep the card pinned to the top-right on every GUI scale.
    card:update({ x = math.max(12, screen.width - 180) })
  end

  if tick_count % 20 == 0 then
    local stats = render.stats()
    title:update({ text = "Tasks " .. stats.rendered .. " / " .. stats.tasks })
  end

  local position = minecraft.player.position()
  render.world("player_marker", {
    type = "text",
    text = "Shyne world task",
    position = vector.new(position.x, position.y + 2.5, position.z),
    color = 0xFFFFFF55,
    shadow = true,
    max_distance = 64
  })
end)

ui.action({
  id = "toggle_render_card",
  title = "Toggle render card",
  toggle = true,
  default = true,
  on_toggle = function(visible)
    if visible then card:show() else card:hide() end
  end
})

events.on("avatar_unload", function()
  render.clear()
end)
