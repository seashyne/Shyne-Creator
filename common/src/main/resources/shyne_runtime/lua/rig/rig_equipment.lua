-- ==============================================================================
-- Shyne Rig Equipment & Cosmetic Module (rig/rig_equipment.lua)
-- Manages attachments of custom model parts to Minecraft's vanilla player limbs,
-- armor slot visibility tracking (with trims and material variants), and
-- custom animated Elytra flight folding.
-- ==============================================================================

local rig_equipment = {}

--- Attaches an array of custom parts to a Minecraft vanilla player bone.
---@param parts table Array of part paths or part proxy objects
---@param parent string Vanilla bone identifier (e.g. "HEAD", "BODY", "LEFT_ARM", "RIGHT_ARM", "LEFT_LEG", "RIGHT_LEG")
---@param options table|nil Options table { mode = "full" | "rotation" | "position" }
---@param rig_math table Math helper module
---@return table The input parts table
function rig_equipment.attach(parts, parent, options, rig_math)
  options = options or {}
  for _, part in ipairs(parts or {}) do
    rig_math.part(part):vanilla_parent(parent, options.mode or "full")
  end
  return parts
end

--- Configures cosmetic armor models driven by actual player equipment slots.
--- Supports automatic vanilla limb attachment, slot detection, and material/trim variants.
---@param options table Configuration mapping armor slots (head, chest, legs, feet, etc.) to part definitions
---@param rig_registry table The shared rig._controllers table
---@param rig_math table Math helper module
---@return table Armor controller with :start(), :stop() methods
function rig_equipment.create_armor(options, rig_registry, rig_math)
  options = options or {}
  local defaults = {
    { key = "head", slot = "head", parent = "HEAD" },
    { key = "chest", slot = "chest", parent = "BODY" },
    { key = "left_arm", slot = "chest", parent = "LEFT_ARM" },
    { key = "right_arm", slot = "chest", parent = "RIGHT_ARM" },
    { key = "legs", slot = "legs", parent = "BODY" },
    { key = "left_leg", slot = "legs", parent = "LEFT_LEG" },
    { key = "right_leg", slot = "legs", parent = "RIGHT_LEG" },
    { key = "feet", slot = "feet", parent = "LEFT_LEG" },
    { key = "left_foot", slot = "feet", parent = "LEFT_LEG" },
    { key = "right_foot", slot = "feet", parent = "RIGHT_LEG" }
  }

  local controller = { bindings = {}, enabled = true }

  for _, default in ipairs(defaults) do
    local spec = options[default.key]
    if spec ~= nil then
      if type(spec) ~= "table" then
        spec = { parts = spec }
      elseif spec.parts == nil and spec.variants == nil and spec.parent == nil and spec.slot == nil
          and spec.mode == nil and spec.match == nil and spec.visible_when_empty == nil then
        spec = { parts = spec }
      end

      local parts = spec.parts or {}
      if type(parts) == "string" then parts = { parts } end
      rig_equipment.attach(parts, spec.parent or default.parent, { mode = spec.mode or options.mode or "full" }, rig_math)

      local variants = spec.variants or {}
      for _, variant_parts in pairs(variants) do
        if type(variant_parts) == "string" then variant_parts = { variant_parts } end
        rig_equipment.attach(variant_parts, spec.parent or default.parent, { mode = spec.mode or options.mode or "full" }, rig_math)
      end

      table.insert(controller.bindings, {
        slot = spec.slot or default.slot,
        parts = parts,
        variants = variants,
        visible_when_empty = spec.visible_when_empty == true,
        match = spec.match
      })
    end
  end

  --- Scans equipped armor stacks each tick and toggles part visibility accordingly.
  function controller:update()
    if not self.enabled then return end
    for _, binding in ipairs(self.bindings) do
      local stack = minecraft.player.armor(binding.slot) or { empty = true }
      local visible = binding.visible_when_empty or not stack.empty
      if visible and type(binding.match) == "function" then
        visible = binding.match(stack) and true or false
      end

      for _, part in ipairs(binding.parts) do
        rig_math.part(part):visible(visible)
      end

      local explicit_match = false
      for item_id in pairs(binding.variants) do
        local key = tostring(item_id):lower()
        if key ~= "default" and (
          key == tostring(stack.id or ""):lower() or
          key == "material:" .. tostring(stack.material or ""):lower() or
          key == "trim_material:" .. tostring(stack.trim_material or ""):lower() or
          key == "trim_pattern:" .. tostring(stack.trim_pattern or ""):lower()
        ) then
          explicit_match = true
          break
        end
      end

      for item_id, variant_parts in pairs(binding.variants) do
        if type(variant_parts) == "string" then variant_parts = { variant_parts } end
        local key = tostring(item_id):lower()
        local selected = visible and (
          (key == "default" and not explicit_match) or
          (key ~= "default" and (
            key == tostring(stack.id or ""):lower() or
            key == "material:" .. tostring(stack.material or ""):lower() or
            key == "trim_material:" .. tostring(stack.trim_material or ""):lower() or
            key == "trim_pattern:" .. tostring(stack.trim_pattern or ""):lower()
          ))
        )
        for _, part in ipairs(variant_parts) do
          rig_math.part(part):visible(selected)
        end
      end
    end
  end

  function controller:start() self.enabled = true; return self end
  function controller:stop() self.enabled = false; return self end

  if rig_registry ~= nil then
    table.insert(rig_registry, controller)
  end
  return controller
end

--- Creates a custom Elytra cosmetic controller attached to player's torso.
--- Automatically syncs visibility with whether the player has an elytra equipped in the chest slot
--- and is actively fall-flying (or show_folded is set).
---@param parts table Array of part paths or part proxy objects
---@param options table|nil Elytra options { mode, show_folded }
---@param rig_registry table The shared rig._controllers table
---@param rig_math table Math helper module
---@return table Elytra controller with :start(), :stop() methods
function rig_equipment.create_elytra(parts, options, rig_registry, rig_math)
  options = options or {}
  if type(parts) == "string" then parts = { parts } end
  rig_equipment.attach(parts or {}, "BODY", { mode = options.mode or "full" }, rig_math)

  local controller = { parts = parts or {}, enabled = true }

  function controller:update()
    if not self.enabled then return end
    local chest = minecraft.player.armor("chest") or { empty = true }
    local visible = chest.id == "minecraft:elytra" and (options.show_folded == true or minecraft.player.fall_flying())
    for _, part in ipairs(self.parts) do
      rig_math.part(part):visible(visible)
    end
  end

  function controller:start() self.enabled = true; return self end
  function controller:stop() self.enabled = false; return self end

  if rig_registry ~= nil then
    table.insert(rig_registry, controller)
  end
  return controller
end

return rig_equipment
