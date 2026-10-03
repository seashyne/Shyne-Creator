-- ==============================================================================
-- Shyne Creator: Figura Items & World Extensions (62_figura_items_world.lua)
-- Provides ItemStackProxy, player item getters, and comprehensive world queries.
-- ==============================================================================

if not vectors then
  pcall(require, "avatar.59_figura_vectors")
end

local function to_coords(pos)
  if type(pos) == "table" then
    return pos.x or pos[1] or 0, pos.y or pos[2] or 0, pos.z or pos[3] or 0
  end
  return tonumber(pos) or 0, 0, 0
end

-- ------------------------------------------------------------------------------
-- 1. ITEM STACK PROXY
-- ------------------------------------------------------------------------------

---@class FiguraItemStack
local item_stack_mt = {}
item_stack_mt.__index = item_stack_mt

function item_stack_mt:getId() return self._raw.id or "minecraft:air" end
function item_stack_mt:getID() return self:getId() end
function item_stack_mt:getType() return self:getId() end
function item_stack_mt:getName() return self._raw.name or self:getId() end
function item_stack_mt:getCount() return tonumber(self._raw.count) or 0 end
function item_stack_mt:getDamage() return tonumber(self._raw.damage) or 0 end
function item_stack_mt:getMaxDamage() return tonumber(self._raw.max_damage) or 0 end
function item_stack_mt:hasGlint() return self._raw.glint == true end
function item_stack_mt:isEmpty() return self._raw.empty == true or self:getCount() <= 0 or self:getId() == "minecraft:air" end
function item_stack_mt:getMaterial() return self._raw.material or "" end
function item_stack_mt:getTrimMaterial() return self._raw.trim_material end
function item_stack_mt:getTrimPattern() return self._raw.trim_pattern end

local function make_item_proxy(raw_table)
  return setmetatable({ _raw = raw_table or {} }, item_stack_mt)
end

-- ------------------------------------------------------------------------------
-- 2. PLAYER ITEM APIS (player:getItem, player:getHeldItem)
-- ------------------------------------------------------------------------------

player = player or {}

--- Retrieves an item stack from the player by slot index or equipment slot name.
--- Slots 1..6: 1=Mainhand, 2=Offhand, 3=Feet, 4=Legs, 5=Chest, 6=Head
---@param slot number|string Slot index (1-6) or slot name ("mainhand", "offhand", "head", etc.)
---@return FiguraItemStack|nil
function player:getItem(slot)
  local num = tonumber(slot)
  if num ~= nil then
    if num % 1 ~= 0 or num < 1 or num > 6 then return nil end
  end
  local data = _shyne_read("player.item", slot or 1)
  if not data or not data.id or data.id == "" then return nil end
  return make_item_proxy(data)
end

--- Retrieves the item in the player's main hand or off hand.
---@param offhand boolean|nil True for off hand, false/nil for main hand
---@return FiguraItemStack|nil
function player:getHeldItem(offhand)
  return self:getItem(offhand == true and 2 or 1)
end

-- ------------------------------------------------------------------------------
-- 3. WORLD STATE & ENVIRONMENT EXPANSIONS
-- ------------------------------------------------------------------------------

world = world or {}

--- Returns detailed block state information including properties and light.
---@param pos table Position {x, y, z}
---@return table Block state details
function world.getBlockState(pos)
  local x, y, z = to_coords(pos)
  local info = _shyne_read("world.block_info", x, y, z)
  if not info then
    return { id = "minecraft:air", solid = false, fluid = false, properties = {} }
  end
  return {
    id = info.id or "minecraft:air",
    solid = info.solid == true,
    fluid = info.fluid == true,
    light = info.light or 0,
    block_light = info.block_light or 0,
    sky_light = info.sky_light or 0,
    redstone = info.redstone or 0,
    properties = info.properties or {},
    pos = vectors.vec3(x, y, z)
  }
end

--- Returns the block light level (0-15) at the given coordinates.
---@param pos table Position {x, y, z}
---@return number Light level
function world.getBlockLight(pos)
  local x, y, z = to_coords(pos)
  return _shyne_read("world.block_light", x, y, z) or 0
end

--- Returns the sky light level (0-15) at the given coordinates.
---@param pos table Position {x, y, z}
---@return number Light level
function world.getSkyLight(pos)
  local x, y, z = to_coords(pos)
  return _shyne_read("world.sky_light", x, y, z) or 0
end

--- Returns the total light level (0-15) at the given coordinates.
---@param pos table Position {x, y, z}
---@return number Light level
function world.getLight(pos)
  local x, y, z = to_coords(pos)
  return _shyne_read("world.light", x, y, z) or 0
end

--- Returns the best redstone power signal (0-15) delivered to this block.
---@param pos table Position {x, y, z}
---@return number Signal level
function world.getRedstonePower(pos)
  local x, y, z = to_coords(pos)
  return _shyne_read("world.redstone", x, y, z) or 0
end

--- Returns the biome identifier at the given coordinates.
---@param pos table Position {x, y, z}
---@return string Biome ID
function world.getBiome(pos)
  local x, y, z = to_coords(pos)
  return _shyne_read("world.biome", x, y, z) or ""
end

function world.getDayTime()
  return _shyne_read("world.day_time") or 0
end

function world.isRaining()
  return _shyne_read("world.raining") == true
end

function world.isThundering()
  return _shyne_read("world.thundering") == true
end

--- Modifies a block in the world at the given position.
local function handle_set_block(arg1, arg2, arg3, arg4, arg5)
  if arg1 == world then
    return handle_set_block(arg2, arg3, arg4, arg5)
  end
  local x, y, z, blockId
  if type(arg1) == "table" and (type(arg2) == "string" or type(arg2) == "table") then
    x, y, z = to_coords(arg1)
    blockId = type(arg2) == "table" and (arg2.id or arg2[1]) or tostring(arg2 or "minecraft:air")
  elseif type(arg1) == "string" and type(arg2) == "table" then
    x, y, z = to_coords(arg2)
    blockId = arg1
  elseif tonumber(arg1) ~= nil and tonumber(arg2) ~= nil and tonumber(arg3) ~= nil then
    x = tonumber(arg1)
    y = tonumber(arg2)
    z = tonumber(arg3)
    blockId = type(arg4) == "table" and (arg4.id or arg4[1]) or tostring(arg4 or "minecraft:air")
  else
    x, y, z = to_coords(arg1)
    blockId = tostring(arg2 or "minecraft:air")
  end
  return _shyne_world_set_block and _shyne_world_set_block(math.floor(x), math.floor(y), math.floor(z), blockId) or false
end

world.setBlock = handle_set_block

--- Sets world daytime. Requires 'world_edit' or 'command' permission in avatar.json.
---@param arg1 any Self or daytime ticks
---@param arg2 any Daytime ticks if called with colon
---@return boolean
local function handle_set_time(arg1, arg2)
  local time = (arg1 == world) and arg2 or arg1
  return _shyne_world_set_time and _shyne_world_set_time(tonumber(time) or 0) or false
end

world.setTime = handle_set_time

if figuraMetatables then
  figuraMetatables.ItemStack = item_stack_mt
end
