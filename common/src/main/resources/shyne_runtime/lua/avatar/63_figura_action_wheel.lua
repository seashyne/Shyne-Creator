-- ==============================================================================
-- Shyne Creator: Figura Action Wheel Bridge (63_figura_action_wheel.lua)
-- Full Figura-compatible ActionWheel, Page, and Action implementation.
-- Supports multi-page setPage navigation, custom .bbmodel/.png action textures,
-- toggleTitle/toggleTexture/toggleItem/toggleColor, 1..8 slot layout, rightClick,
-- scroll callbacks, JSON text component titles, and Vector3 colors.
-- ==============================================================================

---@class ActionWheel
action_wheel = action_wheel or {}
action_wheel._pages = action_wheel._pages or {}
action_wheel._page_by_id = action_wheel._page_by_id or {}
action_wheel._current_page = action_wheel._current_page or nil
action_wheel._action_seq = action_wheel._action_seq or 0

local function parse_rgb(r, g, b)
  if r == nil then return nil end
  if type(r) == "string" then
    local hex = r:gsub("^#", "")
    if #hex == 6 then
      local rv = tonumber(hex:sub(1, 2), 16)
      local gv = tonumber(hex:sub(3, 4), 16)
      local bv = tonumber(hex:sub(5, 6), 16)
      if rv and gv and bv then
        return { rv / 255, gv / 255, bv / 255 }
      end
    end
    return nil
  end
  if type(r) == "table" then
    local rx = tonumber(r.x or r.r or r[1]) or 1
    local gy = tonumber(r.y or r.g or r[2]) or 1
    local bz = tonumber(r.z or r.b or r[3]) or 1
    return { rx, gy, bz }
  end
  return {
    tonumber(r) or 1,
    tonumber(g == nil and r or g) or 1,
    tonumber(b == nil and r or b) or 1
  }
end

local function rgb_to_vec3(rgb)
  if not rgb then return nil end
  if vectors and vectors.vec3 then
    return vectors.vec3(rgb[1] or 1, rgb[2] or 1, rgb[3] or 1)
  end
  return { x = rgb[1] or 1, y = rgb[2] or 1, z = rgb[3] or 1, [1] = rgb[1] or 1, [2] = rgb[2] or 1, [3] = rgb[3] or 1 }
end

local function pack_argb(rgb)
  if not rgb then return nil end
  local r = math.floor(math.max(0, math.min(1, tonumber(rgb[1]) or 1)) * 255 + 0.5)
  local g = math.floor(math.max(0, math.min(1, tonumber(rgb[2]) or 1)) * 255 + 0.5)
  local b = math.floor(math.max(0, math.min(1, tonumber(rgb[3]) or 1)) * 255 + 0.5)
  local unsigned = (255 * 16777216) + (r * 65536) + (g * 256) + b
  if unsigned >= 2147483648 then
    return unsigned - 4294967296
  end
  return unsigned
end

local function clean_title(value)
  if value == nil then return "" end
  local s = tostring(value)
  local first = s:sub(1, 1)
  if (first == "{" or first == "[") and s:find('"text"%s*:') then
    local parts = {}
    for chunk in s:gmatch('"text"%s*:%s*"([^"]*)"') do
      if chunk ~= "" then
        parts[#parts + 1] = chunk
      end
    end
    if #parts > 0 then
      return table.concat(parts)
    end
  end
  return s
end

local function extract_item_id(item)
  if item == nil then return "" end
  if type(item) == "table" then
    if type(item.getID) == "function" then return tostring(item:getID() or "") end
    if item.id ~= nil then return tostring(item.id) end
    if item._id ~= nil then return tostring(item._id) end
  end
  return tostring(item)
end

local function extract_texture_spec(tex, u, v, w, h, scale)
  if tex == nil then return nil end
  local spec = ""
  if type(tex) == "table" then
    if type(tex.getID) == "function" then
      spec = tostring(tex:getID() or "")
    elseif tex._id ~= nil and tostring(tex._id) ~= "" then
      spec = tostring(tex._id)
    elseif tex.id ~= nil and tostring(tex.id) ~= "" then
      spec = tostring(tex.id)
    elseif type(tex.getName) == "function" then
      spec = tostring(tex:getName() or "")
    elseif tex._name ~= nil then
      spec = tostring(tex._name)
    end
  else
    spec = tostring(tex)
  end
  if spec == "" then return nil end
  return {
    spec = spec,
    u = math.floor(tonumber(u) or 0),
    v = math.floor(tonumber(v) or 0),
    w = math.floor(tonumber(w) or 0),
    h = math.floor(tonumber(h) or 0),
    scale = tonumber(scale) or 1
  }
end

local action_methods = {}

function action_methods:title(t)
  self._rawTitle = t
  self._title = clean_title(t)
  self:_update()
  return self
end
action_methods.setTitle = action_methods.title
function action_methods:getTitle() return self._rawTitle ~= nil and tostring(self._rawTitle) or self._title end

function action_methods:toggleTitle(t)
  self._rawToggleTitle = t
  self._toggleTitle = t ~= nil and clean_title(t) or nil
  self:_update()
  return self
end
action_methods.setToggleTitle = action_methods.toggleTitle
function action_methods:getToggleTitle()
  if self._rawToggleTitle ~= nil then return tostring(self._rawToggleTitle) end
  return self._toggleTitle or self:getTitle()
end

function action_methods:item(i)
  self._item = extract_item_id(i)
  self:_update()
  return self
end
action_methods.setItem = action_methods.item
function action_methods:getItem() return self._item end

function action_methods:toggleItem(i)
  self._toggleItem = i ~= nil and extract_item_id(i) or nil
  self:_update()
  return self
end
action_methods.setToggleItem = action_methods.toggleItem
function action_methods:getToggleItem() return self._toggleItem or self._item end

function action_methods:color(r, g, b)
  self._color = parse_rgb(r, g, b)
  self:_update()
  return self
end
action_methods.setColor = action_methods.color
function action_methods:getColor() return rgb_to_vec3(self._color) end

function action_methods:hoverColor(r, g, b)
  self._hoverColor = parse_rgb(r, g, b)
  self:_update()
  return self
end
action_methods.setHoverColor = action_methods.hoverColor
function action_methods:getHoverColor() return rgb_to_vec3(self._hoverColor) end

function action_methods:toggleColor(r, g, b)
  self._toggleColor = parse_rgb(r, g, b)
  self:_update()
  return self
end
action_methods.setToggleColor = action_methods.toggleColor
function action_methods:getToggleColor() return rgb_to_vec3(self._toggleColor) end

function action_methods:texture(tex, u, v, w, h, scale)
  self._texture = extract_texture_spec(tex, u, v, w, h, scale)
  self:_update()
  return self
end
action_methods.setTexture = action_methods.texture

function action_methods:toggleTexture(tex, u, v, w, h, scale)
  self._toggleTexture = extract_texture_spec(tex, u, v, w, h, scale)
  self:_update()
  return self
end
action_methods.setToggleTexture = action_methods.toggleTexture

function action_methods:onLeftClick(fn)
  rawset(self, "_onLeftClick", fn)
  self:_update()
  return self
end
action_methods.setOnLeftClick = action_methods.onLeftClick
function action_methods:getOnLeftClick() return rawget(self, "_onLeftClick") end

function action_methods:onRightClick(fn)
  rawset(self, "_onRightClick", fn)
  self:_update()
  return self
end
action_methods.setOnRightClick = action_methods.onRightClick
function action_methods:getOnRightClick() return rawget(self, "_onRightClick") end

function action_methods:onScroll(fn)
  rawset(self, "_onScroll", fn)
  self:_update()
  return self
end
action_methods.setOnScroll = action_methods.onScroll
function action_methods:getOnScroll() return rawget(self, "_onScroll") end

function action_methods:onToggle(fn)
  rawset(self, "_onToggle", fn)
  self:_update()
  return self
end
action_methods.setOnToggle = action_methods.onToggle
function action_methods:getOnToggle() return rawget(self, "_onToggle") end

function action_methods:onUntoggle(fn)
  rawset(self, "_onUntoggle", fn)
  self:_update()
  return self
end
action_methods.setOnUntoggle = action_methods.onUntoggle
function action_methods:getOnUntoggle() return rawget(self, "_onUntoggle") end

function action_methods:toggled(state)
  if state == nil then return self._toggled == true end
  self._toggled = state and true or false
  self:_update()
  return self
end
action_methods.setToggled = action_methods.toggled
function action_methods:isToggled() return self._toggled == true end

function action_methods:closeOnUse(state)
  if state == nil then
    self._closeOnUse = true
  else
    self._closeOnUse = state and true or false
  end
  self:_update()
  return self
end
action_methods.setCloseOnUse = action_methods.closeOnUse
function action_methods:isCloseOnUse()
  if self._closeOnUse ~= nil then return self._closeOnUse end
  -- Figura default: toggle actions stay open, leaf action clicks close by default
  return not self:_isToggleAction()
end

function action_methods:_isToggleAction()
  return rawget(self, "_onToggle") ~= nil
    or rawget(self, "_onUntoggle") ~= nil
    or self._toggleTitle ~= nil
    or self._toggleTexture ~= nil
    or self._toggleItem ~= nil
    or self._toggleColor ~= nil
end

function action_methods:_triggerPrimary()
  action_wheel._last_triggering_action = self
  if type(action_wheel.leftClick) == "function" then
    local ok, res = pcall(action_wheel.leftClick, self)
    if not ok and _shyne_report_error then _shyne_report_error("action_wheel_left_click", self.id or "action", tostring(res)) end
    if ok and res == true then return end
  end
  if self:_isToggleAction() then
    self._toggled = not self._toggled
    if self._toggled then
      local fn = rawget(self, "_onToggle")
      if type(fn) == "function" then
        local ok, err = pcall(fn, true, self)
        if not ok and _shyne_report_error then _shyne_report_error("action_toggle", self.id or "action", tostring(err)) end
      end
    else
      local untoggleFn = rawget(self, "_onUntoggle")
      local toggleFn = rawget(self, "_onToggle")
      if type(untoggleFn) == "function" then
        local ok, err = pcall(untoggleFn, false, self)
        if not ok and _shyne_report_error then _shyne_report_error("action_untoggle", self.id or "action", tostring(err)) end
      elseif type(toggleFn) == "function" then
        local ok, err = pcall(toggleFn, false, self)
        if not ok and _shyne_report_error then _shyne_report_error("action_toggle", self.id or "action", tostring(err)) end
      end
    end
    self:_update()
  end
  local leftFn = rawget(self, "_onLeftClick")
  if type(leftFn) == "function" then
    local ok, err = pcall(leftFn, self)
    if not ok and _shyne_report_error then _shyne_report_error("action_left_click", self.id or "action", tostring(err)) end
  end
end

function action_methods:_triggerSecondary()
  if type(action_wheel.rightClick) == "function" then
    local ok, res = pcall(action_wheel.rightClick, self)
    if not ok and _shyne_report_error then _shyne_report_error("action_wheel_right_click", self.id or "action", tostring(res)) end
    if ok and res == true then return end
  end
  local rightFn = rawget(self, "_onRightClick")
  if type(rightFn) == "function" then
    local ok, err = pcall(rightFn, self)
    if not ok and _shyne_report_error then _shyne_report_error("action_right_click", self.id or "action", tostring(err)) end
  end
end

function action_methods:_triggerScroll(dir)
  if type(action_wheel.scroll) == "function" then
    local ok, res = pcall(action_wheel.scroll, dir, self)
    if not ok and _shyne_report_error then _shyne_report_error("action_wheel_scroll", self.id or "action", tostring(res)) end
    if ok and res == true then return end
  end
  local scrollFn = rawget(self, "_onScroll")
  if type(scrollFn) == "function" then
    local ok, err = pcall(scrollFn, dir, self)
    if not ok and _shyne_report_error then _shyne_report_error("action_scroll", self.id or "action", tostring(err)) end
  end
end

local _updating = false
function action_methods:_update()
  if _updating then return end
  if not self._page then return end
  if type(_avatar_action_register) ~= "function" then return end

  _updating = true
  local page_name = self._page.id or "main"
  local id = tostring(self.id or (page_name .. "_slot_" .. tostring(self._slot or 1)))
  local is_toggle = self:_isToggleAction()
  local active_toggled = is_toggle and (self._toggled == true)

  local active_title = self._title or ""
  if active_toggled and self._toggleTitle ~= nil then
    active_title = self._toggleTitle
  end

  local active_item = self._item or ""
  if active_toggled and self._toggleItem ~= nil and self._toggleItem ~= "" then
    active_item = self._toggleItem
  end

  local active_tex = self._texture
  if active_toggled and self._toggleTexture ~= nil then
    active_tex = self._toggleTexture
  end

  local col = pack_argb(self._color)
  local hcol = pack_argb(self._hoverColor)
  local tcol = pack_argb(self._toggleColor)
  local slot_zero_based = (type(self._slot) == "number" and self._slot >= 1 and self._slot <= 8)
    and (math.floor(self._slot) - 1)
    or -1

  local has_right = (type(rawget(self, "_onRightClick")) == "function") or (type(action_wheel.rightClick) == "function")
  local has_scroll = (type(rawget(self, "_onScroll")) == "function") or (type(action_wheel.scroll) == "function")

  _avatar_action_register(
    id,
    active_title,
    self._desc or "",
    page_name,
    false,
    self:isCloseOnUse(), -- Closes leaf actions on use; sub-page setPage transitions never close
    function() self:_triggerPrimary() end,
    active_item,
    has_right and function() self:_triggerSecondary() end or nil,
    is_toggle,
    active_toggled,
    col,
    hcol,
    slot_zero_based,
    active_tex and active_tex.spec or "",
    active_tex and active_tex.u or 0,
    active_tex and active_tex.v or 0,
    active_tex and active_tex.w or 0,
    active_tex and active_tex.h or 0,
    active_tex and active_tex.scale or 1,
    tcol,
    has_scroll and function(dir) self:_triggerScroll(dir) end or nil
  )
  self._registered = true
  _updating = false
end

local action_mt = {
  __index = function(self, key)
    if key == "leftClick" then return rawget(self, "_onLeftClick") end
    if key == "rightClick" then return rawget(self, "_onRightClick") end
    if key == "toggle" then return rawget(self, "_onToggle") end
    if key == "untoggle" then return rawget(self, "_onUntoggle") end
    if key == "scroll" then return rawget(self, "_onScroll") end
    return action_methods[key]
  end,
  __newindex = function(self, key, value)
    if key == "leftClick" then self:onLeftClick(value); return end
    if key == "rightClick" then self:onRightClick(value); return end
    if key == "toggle" then self:onToggle(value); return end
    if key == "untoggle" then self:onUntoggle(value); return end
    if key == "scroll" then self:onScroll(value); return end
    rawset(self, key, value)
  end
}

local function create_action(page, slot, explicit_id)
  action_wheel._action_seq = (action_wheel._action_seq or 0) + 1
  local act = setmetatable({
    id = explicit_id or ((page and page.id or "wheel") .. "_act_" .. tostring(action_wheel._action_seq)),
    _title = "",
    _item = "",
    _desc = "",
    _toggled = false,
    _page = page,
    _slot = slot,
    _registered = false
  }, action_mt)
  return act
end

local page_mt = {}
page_mt.__index = page_mt

local function next_free_slot(page)
  for s = 1, 8 do
    if page._slots[s] == nil then return s end
  end
  return #page.actions + 1
end

function page_mt:newAction(slot)
  local numeric_slot = tonumber(slot)
  local explicit_id = nil
  if numeric_slot ~= nil then
    numeric_slot = math.floor(numeric_slot)
  elseif type(slot) == "string" and slot ~= "" then
    explicit_id = slot
    numeric_slot = next_free_slot(self)
  else
    numeric_slot = next_free_slot(self)
  end

  if numeric_slot >= 1 and numeric_slot <= 8 and self._slots[numeric_slot] ~= nil then
    local old = self._slots[numeric_slot]
    if old and old.id and type(_avatar_action_unregister) == "function" then
      _avatar_action_unregister(self.id, old.id)
    end
  end

  local act = create_action(self, numeric_slot, explicit_id)
  if numeric_slot >= 1 and numeric_slot <= 8 then
    self._slots[numeric_slot] = act
  end
  table.insert(self.actions, act)
  act:_update()
  return act
end

function page_mt:setAction(slot, act)
  local numeric_slot = math.floor(tonumber(slot) or -1)
  if numeric_slot == -1 then
    numeric_slot = next_free_slot(self)
  end
  if self._slots[numeric_slot] ~= nil then
    local old = self._slots[numeric_slot]
    if old and old ~= act and old.id and type(_avatar_action_unregister) == "function" then
      _avatar_action_unregister(self.id, old.id)
    end
  end
  if act == nil then
    self._slots[numeric_slot] = nil
    return self
  end
  act._page = self
  act._slot = numeric_slot
  act.id = act.id or (self.id .. "_slot_" .. tostring(numeric_slot))
  if numeric_slot >= 1 and numeric_slot <= 8 then
    self._slots[numeric_slot] = act
  end
  local found = false
  for _, existing in ipairs(self.actions) do
    if existing == act then found = true; break end
  end
  if not found then table.insert(self.actions, act) end
  act:_update()
  return self
end

function page_mt:getAction(slot)
  local numeric_slot = tonumber(slot)
  if numeric_slot ~= nil then
    return self._slots[math.floor(numeric_slot)]
  end
  for _, act in ipairs(self.actions) do
    if act.id == slot or act._title == slot then return act end
  end
  return nil
end

function page_mt:getActions()
  local out = {}
  for s = 1, 8 do
    if self._slots[s] ~= nil then
      out[s] = self._slots[s]
    end
  end
  if next(out) == nil then
    for i, act in ipairs(self.actions) do out[i] = act end
  end
  return out
end

function page_mt:getTitle() return self.title end
function page_mt:setTitle(t) self.title = tostring(t or ""); return self end
function page_mt:keepSlots(b) self._keepSlots = (b == nil) and true or (b and true or false); return self end
page_mt.setKeepSlots = page_mt.keepSlots
function page_mt:isKeepSlots() return self._keepSlots == true end

function action_wheel:newAction()
  return create_action(nil, nil, nil)
end

function action_wheel:newPage(title)
  local clean = (title ~= nil and tostring(title) ~= "") and tostring(title) or nil
  local id = clean or ("page_" .. tostring(#self._pages + 1))
  if self._page_by_id[id] ~= nil then
    id = id .. "_" .. tostring(#self._pages + 1)
  end
  local page = setmetatable({
    id = id,
    title = clean or "",
    actions = {},
    _slots = {},
    _keepSlots = false
  }, page_mt)
  table.insert(self._pages, page)
  self._page_by_id[id] = page
  if clean and not self._page_by_id[clean] then
    self._page_by_id[clean] = page
  end
  if type(_avatar_action_register_page) == "function" then
    _avatar_action_register_page(id)
  end
  if not self._current_page then
    self._current_page = page
    if type(_avatar_action_set_page) == "function" then
      _avatar_action_set_page(page.id)
    end
  end
  return page
end

function action_wheel:setPage(page)
  local resolved = page
  if type(page) == "string" then
    resolved = self:getPage(page)
  end
  self._current_page = resolved

  local page_title = ""
  if resolved then
    if resolved.title and resolved.title ~= "" then
      page_title = resolved.title
    elseif resolved._inferred_title and resolved._inferred_title ~= "" then
      page_title = resolved._inferred_title
    elseif self._last_triggering_action and self._last_triggering_action._title and self._last_triggering_action._title ~= "" then
      page_title = self._last_triggering_action._title
      resolved._inferred_title = page_title
    end
  end

  if type(_avatar_action_set_page) == "function" then
    _avatar_action_set_page(resolved and resolved.id or nil, page_title)
  end
  return self
end

function action_wheel:getCurrentPage()
  if type(_avatar_action_get_page) == "function" then
    local active_id = _avatar_action_get_page()
    if active_id and self._page_by_id[active_id] then
      self._current_page = self._page_by_id[active_id]
    end
  end
  return self._current_page
end

function action_wheel:getPage(title)
  if title == nil then return nil end
  local key = tostring(title)
  if self._page_by_id[key] then return self._page_by_id[key] end
  local lower_key = key:lower()
  for _, page in ipairs(self._pages) do
    if page.title == key or page.id == key or page.title:lower() == lower_key or page.id:lower() == lower_key then
      return page
    end
  end
  return nil
end

function action_wheel:getPages()
  local copy = {}
  for i, page in ipairs(self._pages) do copy[i] = page end
  return copy
end

function action_wheel:isEnabled()
  return false
end

function action_wheel:getSelectedSlot()
  return nil
end

function action_wheel:getSelected()
  return nil
end

function action_wheel:execute(slot, isLeft)
  local page = self._current_page
  if not page then return self end
  local act = slot and page:getAction(slot) or nil
  if not act then return self end
  if isLeft == nil or isLeft == true then
    act:_triggerPrimary()
  else
    act:_triggerSecondary()
  end
  return self
end

if figuraMetatables then
  figuraMetatables.ActionWheel = getmetatable(action_wheel) or action_wheel
  figuraMetatables.Page = page_mt
  figuraMetatables.Action = action_mt
end
