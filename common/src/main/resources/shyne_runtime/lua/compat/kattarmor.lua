-- ==============================================================================
-- Shyne Avatar Runtime: KattArmor Compatibility Shim (compat/kattarmor.lua)
-- Provides built-in compatibility for Figura's KattArmor custom armor system.
-- ==============================================================================

local function create_armor_slot(slot_name, item_slot_index, vanilla_part_name)
  local slot = {
    name = slot_name,
    slot_index = item_slot_index,
    parts = {},
    trim_parts = {},
    prev_item_id = nil
  }

  function slot:addParts(...)
    for _, p in ipairs({ ... }) do
      if type(p) == "table" then
        table.insert(self.parts, p)
      end
    end
    return self
  end

  function slot:addTrimParts(...)
    for _, p in ipairs({ ... }) do
      if type(p) == "table" then
        table.insert(self.trim_parts, p)
      end
    end
    return self
  end

  function slot:setVisible(vis)
    for _, p in ipairs(self.parts) do
      if p and p.setVisible then p:setVisible(vis) end
    end
    return self
  end

  if vanilla_model and vanilla_model[vanilla_part_name] and vanilla_model[vanilla_part_name].setVisible then
    -- KattArmor users expect custom armor parts to replace the corresponding vanilla armor piece when registered
  end

  return slot
end

local function create_material_entry(mat_id)
  local mat = {
    id = mat_id,
    texture = nil,
    color = nil,
    parts = {}
  }
  function mat:setTexture(tex) self.texture = tex; return self end
  function mat:setColor(r, g, b) self.color = { r, g, b }; return self end
  function mat:addParts(...)
    for _, p in ipairs({ ... }) do table.insert(self.parts, p) end
    return self
  end
  function mat:setDamageOverlay(enabled) return self end
  function mat:setEmissive(enabled) return self end
  return mat
end

local function new_katt_armor_instance()
  local instance = {}

  local helmet = create_armor_slot("Helmet", 6, "HELMET")
  local chestplate = create_armor_slot("Chestplate", 5, "CHESTPLATE")
  local leggings = create_armor_slot("Leggings", 4, "LEGGINGS")
  local boots = create_armor_slot("Boots", 3, "BOOTS")

  instance.Armor = setmetatable({
    Helmet = helmet,
    Chestplate = chestplate,
    Leggings = leggings,
    Boots = boots,
    head = helmet,
    chest = chestplate,
    legs = leggings,
    feet = boots
  }, {
    __index = function(t, k)
      local lower = tostring(k):lower()
      if lower:find("helm") or lower:find("head") then return helmet end
      if lower:find("chest") or lower:find("body") then return chestplate end
      if lower:find("leg") or lower:find("pant") then return leggings end
      if lower:find("boot") or lower:find("foot") or lower:find("feet") then return boots end
      return rawget(t, k)
    end
  })

  instance.Materials = setmetatable({}, {
    __index = function(t, k)
      local entry = create_material_entry(tostring(k))
      rawset(t, k, entry)
      return entry
    end
  })

  instance.Helmet = helmet
  instance.Chestplate = chestplate
  instance.Leggings = leggings
  instance.Boots = boots

  function instance:registerOnChange(fn)
    self._on_change = fn
    return self
  end

  if events and events.TICK and events.TICK.register then
    events.TICK:register(function()
      if not player or not player.isLoaded or not player:isLoaded() or not player.getItem then return end
      for _, slot in ipairs({ helmet, chestplate, leggings, boots }) do
        if #slot.parts > 0 then
          local ok, item = pcall(function() return player:getItem(slot.slot_index) end)
          local id = (ok and type(item) == "table" and item.id) and tostring(item.id) or "minecraft:air"
          local equipped = id ~= "" and id ~= "minecraft:air" and id ~= "air"
          if id ~= slot.prev_item_id then
            slot.prev_item_id = id
            slot:setVisible(equipped)
            if type(instance._on_change) == "function" then
              pcall(instance._on_change, slot.name, item)
            end
          end
        end
      end
    end, "shyne_kattarmor_tick")
  end

  return setmetatable(instance, {
    __call = function(self) return self end
  })
end

local KattArmor = new_katt_armor_instance()
_G.KattArmor = KattArmor
return KattArmor
