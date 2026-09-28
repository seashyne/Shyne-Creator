-- ==============================================================================
-- Shyne Creator: Figura Action Wheel Bridge (63_figura_action_wheel.lua)
-- Provides Action Wheel pages and actions mapped to Shyne's Palette System.
-- ==============================================================================

---@class ActionWheel
action_wheel = action_wheel or {
  _pages = {},
  _current_page = nil
}

local action_mt = {}
action_mt.__index = action_mt

function action_mt:title(t) self._title = tostring(t or ""); self:_update(); return self end
function action_mt:setTitle(t) return self:title(t) end
function action_mt:item(i) self._item = tostring(i or ""); self:_update(); return self end
function action_mt:setItem(i) return self:item(i) end
function action_mt:color(r, g, b) self._color = { r or 1, g or 1, b or 1 }; return self end
function action_mt:setColor(r, g, b) return self:color(r, g, b) end
function action_mt:hoverColor(r, g, b) self._hoverColor = { r or 1, g or 1, b or 1 }; return self end
function action_mt:setHoverColor(r, g, b) return self:hoverColor(r, g, b) end
function action_mt:texture(path, u, v, w, h) self._texture = path; return self end
function action_mt:onLeftClick(fn) self._onLeftClick = fn; self:_update(); return self end
function action_mt:onRightClick(fn) self._onRightClick = fn; self:_update(); return self end
function action_mt:onScroll(fn) self._onScroll = fn; return self end
function action_mt:toggled(t) self._toggled = t and true or false; return self end
function action_mt:onToggle(fn) self._onToggle = fn; return self end

function action_mt:_update()
  if self._title ~= "" then
    local page_name = self._page and self._page.id or "main"
    local id = self.id or (page_name .. "_" .. self._title:gsub("%s+", "_"):lower())
    local col = nil
    if self._color then
      local r = math.floor(math.max(0, math.min(1, tonumber(self._color[1]) or 1)) * 255)
      local g = math.floor(math.max(0, math.min(1, tonumber(self._color[2]) or 1)) * 255)
      local b = math.floor(math.max(0, math.min(1, tonumber(self._color[3]) or 1)) * 255)
      col = (255 * 16777216) + (r * 65536) + (g * 256) + b
    end
    local hcol = nil
    if self._hoverColor then
      local r = math.floor(math.max(0, math.min(1, tonumber(self._hoverColor[1]) or 1)) * 255)
      local g = math.floor(math.max(0, math.min(1, tonumber(self._hoverColor[2]) or 1)) * 255)
      local b = math.floor(math.max(0, math.min(1, tonumber(self._hoverColor[3]) or 1)) * 255)
      hcol = (255 * 16777216) + (r * 65536) + (g * 256) + b
    end
    _avatar_action_register(
      id,
      self._title,
      self._desc or "",
      page_name,
      false,
      self._onToggle == nil,
      function()
        if self._onToggle then self._toggled = not self._toggled; self._onToggle(self._toggled); self:_update() end
        if self._onLeftClick then self._onLeftClick() end
      end,
      self._item or "",
      self._onRightClick and function() self._onRightClick() end or nil,
      self._onToggle ~= nil,
      self._toggled == true,
      col,
      hcol
    )
    self._registered = true
  end
end

local page_mt = {}
page_mt.__index = page_mt

function page_mt:newAction(id)
  local act = setmetatable({
    id = id or ("act_" .. tostring(#self.actions + 1)),
    _title = "",
    _item = "",
    _desc = "",
    _page = self,
    _registered = false
  }, action_mt)
  table.insert(self.actions, act)
  return act
end

function page_mt:getAction(id)
  for _, act in ipairs(self.actions) do
    if act.id == id then return act end
  end
  return nil
end

function action_wheel:newPage(title)
  local page = setmetatable({
    id = title or ("page_" .. tostring(#self._pages + 1)),
    title = title or "",
    actions = {}
  }, page_mt)
  table.insert(self._pages, page)
  if not self._current_page then self._current_page = page end
  return page
end

function action_wheel:setPage(page)
  self._current_page = page
end

function action_wheel:getCurrentPage()
  return self._current_page
end
