-- ==============================================================================
-- Shyne Creator: Figura Data, JSON, Resources, & Nameplate 2.0 (64_figura_data_nameplate.lua)
-- Provides Figura-compatible Nameplate 2.0, sandboxed persistent data, safe JSON,
-- and sandboxed avatar asset/resource lookup.
-- ==============================================================================

local function parse_color_argb(value, g, b)
  if type(value) == "number" and g ~= nil and b ~= nil then
    local red = math.max(0, math.min(255, math.floor(value * (value <= 1 and 255 or 1))))
    local green = math.max(0, math.min(255, math.floor(g * (g <= 1 and 255 or 1))))
    local blue = math.max(0, math.min(255, math.floor(b * (b <= 1 and 255 or 1))))
    return (255 * 16777216) + (red * 65536) + (green * 256) + blue
  end
  if type(value) == "number" then
    local num = math.floor(value)
    if num <= 0xFFFFFF then return (255 * 16777216) + num end
    return num
  end
  if type(value) == "table" then
    local red = value.r or value.x or value[1] or 1
    local green = value.g or value.y or value[2] or 1
    local blue = value.b or value.z or value[3] or 1
    return parse_color_argb(red, green, blue)
  end
  if type(value) == "string" then
    local hex = value:gsub("#", "")
    local num = tonumber(hex, 16) or 0xFFFFFF
    if num <= 0xFFFFFF then return (255 * 16777216) + num end
    return num
  end
  return 0xFFFFFFFF
end

-- ------------------------------------------------------------------------------
-- 1. NAMEPLATE 2.0 (nameplate.ENTITY, nameplate.CHAT, nameplate.LIST, nameplate.ALL)
-- ------------------------------------------------------------------------------

local function make_nameplate_target(target_name)
  local target = {
    _target = target_name,
    _text = "",
    _visible = true,
    _badge = "",
    _color = 0xFFFFFFFF,
    _bold = false,
    _italic = false,
    _pos = { 0, 0, 0 },
    _scale = { 1, 1, 1 },
    _pivot = { 0, 0, 0 },
    _shadow = true,
    _bg_color = 0x40000000
  }

  function target:setText(t)
    self._text = tostring(t or "")
    self:_push()
    return self
  end
  function target:getText() return self._text end

  function target:setVisible(v)
    self._visible = v == nil or (v and true or false)
    self:_push()
    return self
  end
  function target:isVisible() return self._visible end

  function target:setBadge(b)
    self._badge = tostring(b or "")
    self:_push()
    return self
  end
  function target:getBadge() return self._badge end

  function target:setColor(c, g, b)
    self._color = parse_color_argb(c, g, b)
    self:_push()
    return self
  end
  function target:getColor()
    if vectors and vectors.vec3 then
      local r = math.floor((self._color / 65536) % 256) / 255
      local gr = math.floor((self._color / 256) % 256) / 255
      local bl = (self._color % 256) / 255
      return vectors.vec3(r, gr, bl)
    end
    return self._color
  end

  function target:setBold(b)
    self._bold = b and true or false
    self:_push()
    return self
  end
  function target:isBold() return self._bold end

  function target:setItalic(i)
    self._italic = i and true or false
    self:_push()
    return self
  end
  function target:isItalic() return self._italic end

  function target:setPos(x, y, z)
    if type(x) == "table" then
      z = x.z or x[3] or 0
      y = x.y or x[2] or 0
      x = x.x or x[1] or 0
    end
    self._pos = { tonumber(x) or 0, tonumber(y) or 0, tonumber(z) or 0 }
    self:_push_transform()
    return self
  end
  function target:getPos()
    if vectors and vectors.vec3 then
      return vectors.vec3(self._pos[1], self._pos[2], self._pos[3])
    end
    return { x = self._pos[1], y = self._pos[2], z = self._pos[3] }
  end

  function target:setScale(x, y, z)
    if type(x) == "table" then
      z = x.z or x[3] or 1
      y = x.y or x[2] or 1
      x = x.x or x[1] or 1
    end
    if y == nil and z == nil then y, z = x, x end
    self._scale = { tonumber(x) or 1, tonumber(y) or 1, tonumber(z) or 1 }
    self:_push_transform()
    return self
  end
  function target:getScale()
    if vectors and vectors.vec3 then
      return vectors.vec3(self._scale[1], self._scale[2], self._scale[3])
    end
    return { x = self._scale[1], y = self._scale[2], z = self._scale[3] }
  end

  function target:setPivot(x, y, z)
    if type(x) == "table" then
      z = x.z or x[3] or 0
      y = x.y or x[2] or 0
      x = x.x or x[1] or 0
    end
    self._pivot = { tonumber(x) or 0, tonumber(y) or 0, tonumber(z) or 0 }
    self:_push_transform()
    return self
  end
  function target:getPivot()
    if vectors and vectors.vec3 then
      return vectors.vec3(self._pivot[1], self._pivot[2], self._pivot[3])
    end
    return { x = self._pivot[1], y = self._pivot[2], z = self._pivot[3] }
  end

  function target:setShadow(s)
    self._shadow = s and true or false
    return self
  end
  function target:hasShadow() return self._shadow end

  function target:setBackgroundColor(c, g, b)
    self._bg_color = parse_color_argb(c, g, b)
    return self
  end
  function target:getBackgroundColor() return self._bg_color end

  function target:_push()
    if _avatar_nameplate_target_set then
      _avatar_nameplate_target_set(self._target, self._text, self._visible, self._badge, self._color, self._bold, self._italic)
    elseif self._target == "entity" and _avatar_nameplate_set then
      _avatar_nameplate_set(self._text, self._visible, self._badge, self._color, self._bold, self._italic)
    end
  end

  function target:_push_transform()
    if _avatar_nameplate_transform_set then
      _avatar_nameplate_transform_set(
        self._pos[1], self._pos[2], self._pos[3],
        self._scale[1], self._scale[2], self._scale[3],
        self._pivot[1], self._pivot[2], self._pivot[3]
      )
    end
  end

  return setmetatable(target, {
    __index = function(t, k)
      if k == "text" then return t:getText() end
      if k == "visible" then return t:isVisible() end
      if k == "badge" then return t:getBadge() end
      if k == "color" then return t:getColor() end
      if k == "pos" then return t:getPos() end
      if k == "scale" then return t:getScale() end
      if k == "pivot" then return t:getPivot() end
      return rawget(t, k)
    end,
    __newindex = function(t, k, v)
      if k == "text" then t:setText(v)
      elseif k == "visible" then t:setVisible(v)
      elseif k == "badge" then t:setBadge(v)
      elseif k == "color" then t:setColor(v)
      elseif k == "pos" then t:setPos(v)
      elseif k == "scale" then t:setScale(v)
      elseif k == "pivot" then t:setPivot(v)
      else rawset(t, k, v) end
    end
  })
end

local entity_np = make_nameplate_target("entity")
local chat_np = make_nameplate_target("chat")
local list_np = make_nameplate_target("list")

local all_np = {
  setText = function(self, t) entity_np:setText(t); chat_np:setText(t); list_np:setText(t); return self end,
  setVisible = function(self, v) entity_np:setVisible(v); chat_np:setVisible(v); list_np:setVisible(v); return self end,
  setBadge = function(self, b) entity_np:setBadge(b); chat_np:setBadge(b); list_np:setBadge(b); return self end,
  setColor = function(self, c, g, b) entity_np:setColor(c, g, b); chat_np:setColor(c, g, b); list_np:setColor(c, g, b); return self end,
  setBold = function(self, bo) entity_np:setBold(bo); chat_np:setBold(bo); list_np:setBold(bo); return self end,
  setItalic = function(self, it) entity_np:setItalic(it); chat_np:setItalic(it); list_np:setItalic(it); return self end
}

nameplate = {
  ENTITY = entity_np,
  CHAT = chat_np,
  LIST = list_np,
  ALL = all_np,
  setText = function(self, t) return entity_np:setText(t) end,
  getText = function(self) return entity_np:getText() end,
  setVisible = function(self, v) return entity_np:setVisible(v) end,
  isVisible = function(self) return entity_np:isVisible() end,
  setBadge = function(self, b) return entity_np:setBadge(b) end,
  getBadge = function(self) return entity_np:getBadge() end,
  setColor = function(self, c, g, b) return entity_np:setColor(c, g, b) end,
  getColor = function(self) return entity_np:getColor() end,
  setPos = function(self, x, y, z) return entity_np:setPos(x, y, z) end,
  getPos = function(self) return entity_np:getPos() end,
  setScale = function(self, x, y, z) return entity_np:setScale(x, y, z) end,
  getScale = function(self) return entity_np:getScale() end,
  setPivot = function(self, x, y, z) return entity_np:setPivot(x, y, z) end,
  getPivot = function(self) return entity_np:getPivot() end
}

-- Synchronize avatar.nameplate.configure with Nameplate 2.0
if avatar and avatar.nameplate then
  local orig_configure = avatar.nameplate.configure
  avatar.nameplate.configure = function(options)
    options = options or {}
    if options.text ~= nil then entity_np:setText(options.text) end
    if options.visible ~= nil then entity_np:setVisible(options.visible) end
    if options.badge ~= nil then entity_np:setBadge(options.badge) end
    if options.color ~= nil then entity_np:setColor(options.color) end
    if options.bold ~= nil then entity_np:setBold(options.bold) end
    if options.italic ~= nil then entity_np:setItalic(options.italic) end
    if options.pos ~= nil then entity_np:setPos(options.pos) end
    if options.scale ~= nil then entity_np:setScale(options.scale) end
    if options.pivot ~= nil then entity_np:setPivot(options.pivot) end
    if options.chat ~= nil and type(options.chat) == "table" then
      if options.chat.text ~= nil then chat_np:setText(options.chat.text) end
      if options.chat.badge ~= nil then chat_np:setBadge(options.chat.badge) end
      if options.chat.color ~= nil then chat_np:setColor(options.chat.color) end
      if options.chat.visible ~= nil then chat_np:setVisible(options.chat.visible) end
    end
    if options.list ~= nil and type(options.list) == "table" then
      if options.list.text ~= nil then list_np:setText(options.list.text) end
      if options.list.badge ~= nil then list_np:setBadge(options.list.badge) end
      if options.list.color ~= nil then list_np:setColor(options.list.color) end
      if options.list.visible ~= nil then list_np:setVisible(options.list.visible) end
    end
    if orig_configure then orig_configure(options) end
  end
  avatar.nameplate.ENTITY = entity_np
  avatar.nameplate.CHAT = chat_np
  avatar.nameplate.LIST = list_np
  avatar.nameplate.ALL = all_np
end

-- ------------------------------------------------------------------------------
-- 2. DATA PROXY (data:save, data:load, data[key] persistence)
-- ------------------------------------------------------------------------------

local _data_ns = "default"
data = {
  setName = function(self, name)
    _data_ns = tostring(name or "default")
    return self
  end,
  getName = function(self) return _data_ns end,
  save = function(self, key, value)
    if key == nil then return true end
    if _avatar_data_save then
      return _avatar_data_save(_data_ns, tostring(key), value)
    end
    return false
  end,
  load = function(self, key)
    if _avatar_data_load then
      return _avatar_data_load(_data_ns, tostring(key))
    end
    return nil
  end,
  set = function(self, key, value) return self:save(key, value) end,
  get = function(self, key, default)
    local val = self:load(key)
    if val == nil then return default end
    return val
  end,
  has = function(self, key)
    if _avatar_data_has then
      return _avatar_data_has(_data_ns, tostring(key))
    end
    return false
  end,
  clear = function(self)
    if _avatar_data_clear then
      return _avatar_data_clear(_data_ns)
    end
    return false
  end,
  getTable = function(self)
    if _avatar_data_get_all then
      return _avatar_data_get_all(_data_ns) or {}
    end
    return {}
  end
}

setmetatable(data, {
  __index = function(t, k)
    if type(k) == "string" and rawget(t, k) ~= nil then return rawget(t, k) end
    return t:load(k)
  end,
  __newindex = function(t, k, v)
    if type(k) == "string" and (k:sub(1, 1) == "_" or t[k] ~= nil) then
      rawset(t, k, v)
    else
      t:save(k, v)
    end
  end
})

-- ------------------------------------------------------------------------------
-- 3. JSON PARSER & ENCODER (json.encode, json.decode)
-- ------------------------------------------------------------------------------

json = {
  encode = function(self, value)
    local val = (self == json and value) or self
    if _avatar_json_encode then return _avatar_json_encode(val) end
    return "{}"
  end,
  decode = function(self, str)
    local s = (self == json and str) or self
    if _avatar_json_decode then return _avatar_json_decode(tostring(s or "")) end
    return nil
  end,
  toJson = function(self, value) return json:encode(value) end,
  fromJson = function(self, str) return json:decode(str) end
}

-- ------------------------------------------------------------------------------
-- 4. RESOURCES / ASSETS LOOKUP (resources:has, resources:getText, resources:getJson)
-- ------------------------------------------------------------------------------

resources = {
  has = function(self, path)
    local p = (self == resources and path) or self
    if _avatar_resource_has then return _avatar_resource_has(tostring(p or "")) end
    return false
  end,
  getText = function(self, path)
    local p = (self == resources and path) or self
    if _avatar_resource_read then return _avatar_resource_read(tostring(p or "")) end
    return nil
  end,
  read = function(self, path) return self:getText(path) end,
  getJson = function(self, path)
    local text = self:getText(path)
    if text == nil then return nil end
    return json:decode(text)
  end,
  list = function(self, dir)
    local d = (self == resources and dir) or self or ""
    if _avatar_resource_list then return _avatar_resource_list(tostring(d)) end
    return {}
  end
}

-- ------------------------------------------------------------------------------
-- 5. SANDBOXED AVATAR FILESYSTEM I/O (io.open, io.lines, io.type)
-- ------------------------------------------------------------------------------

io = io or {}
local file_mt = {}
file_mt.__index = file_mt

function file_mt:read(fmt)
  if self._closed then error("attempt to use a closed file", 2) end
  fmt = fmt or "*l"
  if self._mode == "w" or self._mode == "a" then error("file not open for reading", 2) end
  if not self._content then
    self._content = _avatar_resource_read and _avatar_resource_read(self._path) or ""
    self._pos = 1
  end
  if self._pos > #self._content then return nil end
  if fmt == "*a" or fmt == "*all" then
    local res = self._content:sub(self._pos)
    self._pos = #self._content + 1
    return res
  elseif fmt == "*l" or fmt == "*line" then
    local nl = self._content:find("\n", self._pos, true)
    local res
    if nl then
      res = self._content:sub(self._pos, nl - 1)
      self._pos = nl + 1
    else
      res = self._content:sub(self._pos)
      self._pos = #self._content + 1
    end
    if res:sub(-1) == "\r" then res = res:sub(1, -2) end
    return res
  elseif type(fmt) == "number" then
    local res = self._content:sub(self._pos, self._pos + fmt - 1)
    self._pos = self._pos + fmt
    return res
  end
  return nil
end

function file_mt:write(...)
  if self._closed then error("attempt to use a closed file", 2) end
  local args = { ... }
  for _, part in ipairs(args) do
    local str = tostring(part)
    if self._mode == "a" or self._mode == "a+" then
      if _avatar_file_append then _avatar_file_append(self._path, str) end
    else
      if not self._written then
        if _avatar_file_write then _avatar_file_write(self._path, str) end
        self._written = true
      else
        if _avatar_file_append then _avatar_file_append(self._path, str) end
      end
    end
  end
  return self
end

function file_mt:close()
  self._closed = true
  return true
end

function file_mt:lines()
  return function() return self:read("*l") end
end

function io.open(filename, mode)
  mode = mode or "r"
  filename = tostring(filename or "")
  if mode:find("r") and not (_avatar_resource_has and _avatar_resource_has(filename)) and not mode:find("%+") then
    return nil, "cannot open file '" .. filename .. "' (No such file)"
  end
  if mode:find("w") and not mode:find("a") then
    if _avatar_file_write then _avatar_file_write(filename, "") end
  elseif mode:find("a") then
    if _avatar_file_append then _avatar_file_append(filename, "") end
  end
  local f = {
    _path = filename,
    _mode = mode,
    _closed = false,
    _content = nil,
    _pos = 1,
    _written = true
  }
  return setmetatable(f, file_mt)
end

function io.lines(filename)
  local f, err = io.open(filename, "r")
  if not f then error(err, 2) end
  return function()
    local line = f:read("*l")
    if line == nil then f:close() end
    return line
  end
end

function io.type(obj)
  if type(obj) == "table" and getmetatable(obj) == file_mt then
    return obj._closed and "closed file" or "file"
  end
  return nil
end

-- ------------------------------------------------------------------------------
-- 6. SAFE SANDBOXED OS APIS (os.time, os.date, os.clock, os.getenv)
-- ------------------------------------------------------------------------------

os = os or {}

function os.time(tbl)
  if type(tbl) == "table" then
    local y = tbl.year or 1970
    local m = tbl.month or 1
    local d = tbl.day or 1
    local h = tbl.hour or 0
    local min = tbl.min or 0
    local s = tbl.sec or 0
    return _shyne_os_time and _shyne_os_time(y, m, d, h, min, s) or 0
  end
  return _shyne_os_time and _shyne_os_time() or 0
end

function os.date(fmt, time)
  return _shyne_os_date and _shyne_os_date(fmt or "%c", time or os.time()) or tostring(time or 0)
end

function os.clock()
  return _shyne_os_clock and _shyne_os_clock() or 0
end

function os.difftime(t2, t1)
  return (tonumber(t2) or 0) - (tonumber(t1) or 0)
end

function os.getenv(var)
  return _shyne_os_getenv and _shyne_os_getenv(tostring(var or "")) or nil
end

function os.execute(cmd)
  return nil, "os.execute is sandboxed for multiplayer security"
end
