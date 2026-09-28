-- ==============================================================================
-- Shyne Avatar Runtime: Vanilla Model & Body Replacement (avatar/21_vanilla_model.lua)
-- Universal replace_vanilla & body part replacement system.
-- Supports: boolean, string, comma-separated list, array table, and key-value map.
-- ==============================================================================

local function bool(value) return value and true or false end

-- ------------------------------------------------------------------------------
-- UNIVERSAL REPLACE_VANILLA & BODY PART REPLACEMENT SYSTEM
-- ------------------------------------------------------------------------------
local PART_GROUPS = {
  arms = { "RIGHT_ARM", "LEFT_ARM", "RIGHT_SLEEVE", "LEFT_SLEEVE" },
  arm = { "RIGHT_ARM", "LEFT_ARM", "RIGHT_SLEEVE", "LEFT_SLEEVE" },
  legs = { "RIGHT_LEG", "LEFT_LEG", "RIGHT_PANTS", "LEFT_PANTS" },
  leg = { "RIGHT_LEG", "LEFT_LEG", "RIGHT_PANTS", "LEFT_PANTS" },
  head = { "HEAD", "HAT" },
  body = { "BODY", "JACKET" },
  torso = { "BODY", "JACKET" },
  right_arm = { "RIGHT_ARM", "RIGHT_SLEEVE" },
  rightarm = { "RIGHT_ARM", "RIGHT_SLEEVE" },
  right_hand = { "RIGHT_ARM", "RIGHT_SLEEVE" },
  left_arm = { "LEFT_ARM", "LEFT_SLEEVE" },
  leftarm = { "LEFT_ARM", "LEFT_SLEEVE" },
  left_hand = { "LEFT_ARM", "LEFT_SLEEVE" },
  right_leg = { "RIGHT_LEG", "RIGHT_PANTS" },
  rightleg = { "RIGHT_LEG", "RIGHT_PANTS" },
  right_foot = { "RIGHT_LEG", "RIGHT_PANTS" },
  left_leg = { "LEFT_LEG", "LEFT_PANTS" },
  leftleg = { "LEFT_LEG", "LEFT_PANTS" },
  left_foot = { "LEFT_LEG", "LEFT_PANTS" },
  hat = { "HAT" },
  jacket = { "JACKET" },
  right_sleeve = { "RIGHT_SLEEVE" },
  left_sleeve = { "LEFT_SLEEVE" },
  right_pants = { "RIGHT_PANTS" },
  left_pants = { "LEFT_PANTS" },
  sleeves = { "RIGHT_SLEEVE", "LEFT_SLEEVE" },
  pants = { "RIGHT_PANTS", "LEFT_PANTS" },
  cape = { "CAPE" },
  elytra = { "ELYTRA" },
  armor = { "ARMOR", "HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS" },
  helmet = { "HELMET" },
  chestplate = { "CHESTPLATE" },
  leggings = { "LEGGINGS" },
  boots = { "BOOTS" },
  items = { "HELD_ITEMS", "LEFT_ITEM", "RIGHT_ITEM", "MAIN_HAND", "OFF_HAND" },
  held_items = { "HELD_ITEMS", "LEFT_ITEM", "RIGHT_ITEM", "MAIN_HAND", "OFF_HAND" },
  all = { "PLAYER" },
  player = { "PLAYER" }
}

local function set_vanilla_visible_native(key, visible)
  if _avatar_vanilla_visible then _avatar_vanilla_visible(key, visible) end
end

local function apply_part_visibility(part_name, visible)
  local key = tostring(part_name or ""):lower():gsub("%s+", ""):gsub("-", "_")
  local targets = PART_GROUPS[key]
  if targets then
    for _, target in ipairs(targets) do set_vanilla_visible_native(target, visible) end
  else
    set_vanilla_visible_native(key:upper(), visible)
  end
end

local function replace_vanilla_impl(config, opt_state)
  if config == nil then return end

  if type(config) == "boolean" then
    set_vanilla_visible_native("PLAYER", not config)
    if not config then
      for _, targets in pairs(PART_GROUPS) do
        for _, t in ipairs(targets) do set_vanilla_visible_native(t, true) end
      end
    end
    return
  end

  if type(config) == "string" then
    local should_replace = (opt_state == nil) and true or bool(opt_state)
    local raw = config:gsub("[,;]", " ")
    for token in raw:gmatch("%S+") do
      local lower = token:lower()
      if lower == "all" then
        set_vanilla_visible_native("PLAYER", not should_replace)
      elseif lower == "none" then
        set_vanilla_visible_native("PLAYER", true)
        for _, targets in pairs(PART_GROUPS) do
          for _, t in ipairs(targets) do set_vanilla_visible_native(t, true) end
        end
      else
        if should_replace then set_vanilla_visible_native("PLAYER", true) end
        apply_part_visibility(token, not should_replace)
      end
    end
    return
  end

  if type(config) == "table" then
    local has_partial = false
    for k, _ in pairs(config) do
      if type(k) == "number" or (type(k) == "string" and k:lower() ~= "all" and k:lower() ~= "player") then
        has_partial = true
        break
      end
    end
    if has_partial then set_vanilla_visible_native("PLAYER", true) end

    for k, v in pairs(config) do
      if type(k) == "number" and (type(v) == "string" or type(v) == "table") then
        local should_replace = (opt_state == nil) and true or bool(opt_state)
        replace_vanilla_impl(v, should_replace)
      elseif type(k) == "string" then
        local should_replace = bool(v)
        local lower = k:lower()
        if lower == "all" or lower == "player" then
          set_vanilla_visible_native("PLAYER", not should_replace)
        else
          apply_part_visibility(k, not should_replace)
        end
      end
    end
  end
end

-- ------------------------------------------------------------------------------
-- VANILLA PLAYER MODEL PROXY
-- ------------------------------------------------------------------------------
local function vanilla_proxy(part)
  local proxy = { part = tostring(part or "PLAYER") }
  function proxy:visible(value)
    if value == nil then
      return _avatar_vanilla_transform and _avatar_vanilla_transform(self.part).visible or true
    end
    replace_vanilla_impl(self.part, not bool(value))
    return self
  end
  function proxy:show() return self:visible(true) end
  function proxy:hide() return self:visible(false) end
  function proxy:setVisible(value) return self:visible(value) end
  function proxy:getVisible() return self:visible() end
  function proxy:position()
    local t = _avatar_vanilla_transform and _avatar_vanilla_transform(self.part)
    return vector.new(t and t.position or { x = 0, y = 0, z = 0 })
  end
  function proxy:rotation()
    local t = _avatar_vanilla_transform and _avatar_vanilla_transform(self.part)
    return vector.new(t and t.rotation or { x = 0, y = 0, z = 0 })
  end
  return proxy
end

vanilla_model = setmetatable({}, {
  __index = function(_, part) return vanilla_proxy(part) end
})

-- Global Shyne and Avatar exports
shyne = shyne or {}
shyne.replace_vanilla = replace_vanilla_impl
shyne.replaceVanilla = replace_vanilla_impl
shyne.hide_vanilla = replace_vanilla_impl
shyne.hideVanilla = replace_vanilla_impl

avatar = avatar or {}
function avatar.vanilla(part) return vanilla_proxy(part) end
function avatar.hide_vanilla(value, opt) replace_vanilla_impl(value, opt) end
function avatar.hideVanilla(value, opt) replace_vanilla_impl(value, opt) end
function avatar.replace_vanilla(value, opt) replace_vanilla_impl(value, opt) end
function avatar.replaceVanilla(value, opt) replace_vanilla_impl(value, opt) end
