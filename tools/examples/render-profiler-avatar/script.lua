local cyan = 0xFF55FFFF

events.on("entity_init", function()
  render.text("title", { text = "Shyne Render API", x = 12, y = 12, color = cyan, shadow = true })
  render.item("diamond", { item = "minecraft:diamond", x = 12, y = 28 })
  render.block("amethyst", { block = "minecraft:amethyst_block", x = 32, y = 28 })
  render.sprite("logo", {
    texture = "shyne_creator:textures/gui/shyne_creator_logo.png",
    x = 54, y = 12, width = 32, height = 32
  })
  render.line("underline", {
    from = vector.new(12, 50, 0), to = vector.new(120, 50, 0), color = cyan, width = 2
  })
end)

events.on("tick", function()
  local position = minecraft.player.position()
  render.world("player_marker", {
    type = "text",
    text = "World task",
    position = vector.new(position.x, position.y + 2.5, position.z),
    color = 0xFFFFFF55,
    shadow = true
  })
end)

events.on("avatar_unload", function()
  render.clear()
end)
