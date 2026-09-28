-- inspect.lua - Human-readable representation of Lua tables
-- Copyright (c) 2013 Enrique García Cota (kikito) (MIT License)

local inspect = {
  _VERSION = 'inspect.lua 3.1.0',
  _DESCRIPTION = 'human-readable representations of tables',
  _URL = 'https://github.com/kikito/inspect.lua',
  _LICENSE = 'MIT'
}

local function isIdentifier(str)
  return type(str) == 'string' and str:match('^[_%a][_%a%d]*$') ~= nil
end

local function smartQuote(str)
  return string.format("%q", str)
end

local function inspectInternal(root, options)
  options = options or {}
  local depth = options.depth or 5
  local newline = options.newline or '\n'
  local indent = options.indent or '  '

  local seen = {}
  local buffer = {}

  local function put(str)
    buffer[#buffer + 1] = str
  end

  local function serialize(item, currentDepth, currentIndent)
    local t = type(item)
    if t == 'nil' then
      put('nil')
    elseif t == 'number' or t == 'boolean' then
      put(tostring(item))
    elseif t == 'string' then
      put(smartQuote(item))
    elseif t == 'function' or t == 'thread' or t == 'userdata' then
      put(string.format("<%s>", tostring(item)))
    elseif t == 'table' then
      if seen[item] then
        put(string.format("<circular %s>", tostring(item)))
        return
      end
      if currentDepth >= depth then
        put('{...}')
        return
      end
      seen[item] = true

      local nextIndent = currentIndent .. indent
      put('{' .. newline)

      -- 1. Array sequence keys
      local count = #item
      for i = 1, count do
        put(nextIndent)
        serialize(item[i], currentDepth + 1, nextIndent)
        if i < count or next(item, count) ~= nil then
          put(',' .. newline)
        else
          put(newline)
        end
      end

      -- 2. Hash map keys
      for k, v in pairs(item) do
        local isSeq = type(k) == 'number' and k >= 1 and k <= count and math.floor(k) == k
        if not isSeq then
          put(nextIndent)
          if isIdentifier(k) then
            put(k .. ' = ')
          else
            put('[')
            serialize(k, currentDepth + 1, nextIndent)
            put('] = ')
          end
          serialize(v, currentDepth + 1, nextIndent)
          put(',' .. newline)
        end
      end

      put(currentIndent .. '}')
      seen[item] = nil
    end
  end

  serialize(root, 0, '')
  return table.concat(buffer)
end

setmetatable(inspect, {
  __call = function(_, root, options)
    return inspectInternal(root, options)
  end
})

inspect.inspect = inspectInternal
return inspect
