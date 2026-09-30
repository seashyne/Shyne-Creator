-- Script Canvas UI: every visible element below is authored by this Avatar.
-- Press U in-game to open it. No Shyne menu, button style, or layout is supplied.

local cyan = 0xFF55FFFF
local violet = 0xFFB28DFF
local selected = "Home"
local panel = nil
local last_width, last_height = -1, -1

local function draw()
  local screen = render.screen()
  if not screen.ready or panel == nil then return end

  local width = math.min(300, screen.width - 32)
  local height = 178
  local x = math.floor((screen.width - width) / 2)
  local y = math.floor((screen.height - height) / 2)

  panel:rect("backdrop", { x = x, y = y, width = width, height = height, color = 0xF0141C31, z_index = 1 })
  panel:outline("border", { x = x, y = y, width = width, height = height, thickness = 2, color = cyan, z_index = 2 })
  panel:text("title", { x = x + 16, y = y + 15, text = "MY AVATAR MENU", color = cyan, shadow = true, z_index = 3 })
  panel:text("caption", { x = x + 16, y = y + 31, text = "Written entirely in Lua", color = 0xFFB9C6DE, z_index = 3 })

  local tabs = { "Home", "Style", "About" }
  for index, label in ipairs(tabs) do
    local tab_x = x + 16 + (index - 1) * 86
    local active = label == selected
    panel:rect("tab_" .. label, {
      x = tab_x, y = y + 58, width = 78, height = 22,
      color = active and 0xFF314C7A or 0xFF1A263D, z_index = 3
    })
    panel:text("tab_label_" .. label, {
      x = tab_x + 8, y = y + 65, text = label,
      color = active and cyan or 0xFFE8F0FF, z_index = 4
    })
    panel:button({ id = "tab_" .. label, x = tab_x, y = y + 58, width = 78, height = 22,
      on_click = function() selected = label; draw() end
    })
  end

  panel:text("content", {
    x = x + 16, y = y + 99, text = "Selected: " .. selected,
    color = violet, shadow = true, z_index = 3
  })
  panel:text("hint", {
    x = x + 16, y = y + 118, text = "Click a tab, then close this canvas.",
    color = 0xFFC7D3E8, z_index = 3
  })

  local close_x, close_y = x + width - 86, y + height - 34
  panel:rect("close_background", { x = close_x, y = close_y, width = 70, height = 18, color = 0xFF7A263A, z_index = 3 })
  panel:text("close_label", { x = close_x + 17, y = close_y + 5, text = "Close", color = 0xFFFFFFFF, z_index = 4 })
  panel:button({ id = "close", x = close_x, y = close_y, width = 70, height = 18,
    on_click = function() panel:close() end
  })
end

events.on("entity_init", function()
  panel = ui.canvas({
    id = "avatar_menu",
    backdrop = 0x99000000,
    close_on_escape = true,
    on_open = function() draw() end,
    on_close = function() print("Avatar canvas closed") end
  })

  input.bind("open_avatar_menu", {
    title = "Open Avatar Menu",
    key = input.key.u,
    on_press = function() panel:open() end
  })
end)

events.on("render", function()
  local screen = render.screen()
  if screen.ready and (screen.width ~= last_width or screen.height ~= last_height) then
    last_width, last_height = screen.width, screen.height
    draw()
  end
end)

events.on("avatar_unload", function()
  if panel then panel:clear() end
end)
