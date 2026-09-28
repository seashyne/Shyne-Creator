-- ==============================================================================
-- Modular Showcase: Signature Weapon Module (weapons/signature_blade.lua)
-- Demonstrates:
--  - Signature Weapon 3D Bone management (Hand vs Sheath)
--  - Integration with Minecraft held item state (player:getHeldItem())
--  - Blade beam wave attack & monster detection
-- ==============================================================================

local SignatureWeapon = {
  mode = "sheathed", -- "drawn", "sheathed", "hidden"
  is_drawn = false,
  blade_cooldown = 0
}

function SignatureWeapon.apply_visibility()
  local sheath = models.model and models.model.root and models.model.root.Body and models.model.root.Body.SheathedBlade
  local blade = models.model and models.model.root and models.model.root.RightArm and models.model.root.RightArm.SignatureBlade

  local weapons_allowed = not minecraft or not minecraft.settings or minecraft.settings.weapons_enabled()

  if not weapons_allowed or SignatureWeapon.mode == "hidden" then
    if sheath then sheath:setVisible(false) end
    if blade then blade:setVisible(false) end
    SignatureWeapon.is_drawn = false
  elseif SignatureWeapon.mode == "drawn" then
    if sheath then sheath:setVisible(false) end
    if blade then blade:setVisible(true) end
    SignatureWeapon.is_drawn = true
  else -- "sheathed"
    if sheath then sheath:setVisible(true) end
    if blade then blade:setVisible(false) end
    SignatureWeapon.is_drawn = false
  end
end

function SignatureWeapon.init()
  SignatureWeapon.apply_visibility()
end

function SignatureWeapon.cycle_mode()
  local pos = player:getPos()
  if SignatureWeapon.mode == "sheathed" then
    SignatureWeapon.mode = "drawn"
    SignatureWeapon.apply_visibility()
    sounds:playSound("minecraft:item.armor.equip_iron", pos, 1.0, 1.4)
    print("§b[Weapon] §aSignature Blade: §eDRAWN (มือขวา)")
  elseif SignatureWeapon.mode == "drawn" then
    SignatureWeapon.mode = "hidden"
    SignatureWeapon.apply_visibility()
    sounds:playSound("minecraft:item.armor.equip_leather", pos, 0.7, 0.8)
    print("§7[Weapon] §cSignature Blade: §7UNEQUIPPED (ถอดเก็บ)")
  else
    SignatureWeapon.mode = "sheathed"
    SignatureWeapon.apply_visibility()
    sounds:playSound("minecraft:item.armor.equip_leather", pos, 0.8, 1.2)
    print("§7[Weapon] §bSignature Blade: §fSHEATHED (สะพายหลัง)")
  end
  return SignatureWeapon.mode
end

function SignatureWeapon.toggle_drawn()
  if SignatureWeapon.mode == "hidden" then
    SignatureWeapon.mode = "sheathed"
  end
  local pos = player:getPos()
  if SignatureWeapon.mode == "sheathed" then
    SignatureWeapon.mode = "drawn"
    sounds:playSound("minecraft:item.armor.equip_iron", pos, 1.0, 1.4)
    print("§b[Weapon] §aUnsheathed Signature Blade!")
  else
    SignatureWeapon.mode = "sheathed"
    sounds:playSound("minecraft:item.armor.equip_leather", pos, 0.8, 1.2)
    print("§7[Weapon] Sheathed Signature Blade.")
  end
  SignatureWeapon.apply_visibility()
end

function SignatureWeapon.slash_wave(state, config)
  if SignatureWeapon.blade_cooldown > 0 then return end
  if not config.debug.infinite_mana and state.mana < config.skills.blade_beam.cost then
    sounds:playSound("minecraft:block.fire.extinguish", player:getPos(), 0.7, 1.6)
    return
  end

  if not SignatureWeapon.is_drawn then
    SignatureWeapon.toggle_drawn()
  end

  if not config.debug.infinite_mana then
    state.mana = state.mana - config.skills.blade_beam.cost
  end
  SignatureWeapon.blade_cooldown = config.skills.blade_beam.cooldown

  local pos = player:getPos()
  local look = player:getLookDir()

  sounds:playSound("minecraft:entity.player.attack.sweep", pos, 1.2, 1.0)
  sounds:playSound("minecraft:entity.warden.sonic_boom", pos, 0.8, 1.6)

  if animations and animations.slash_attack then
    animations.slash_attack:restart()
  end

  -- ปล่อยคลื่นดาบคริสตัลสีฟ้าพุ่งไปข้างหน้า (Crescent Blade Beam)
  for step = 1, 8 do
    local offset_x = pos.x + look.x * (step * 0.8)
    local offset_y = pos.y + 0.8 + look.y * (step * 0.8)
    local offset_z = pos.z + look.z * (step * 0.8)

    particle.spawn("minecraft:sweep_attack", { x = offset_x, y = offset_y, z = offset_z }, {
      velocity = { look.x * 0.5, 0, look.z * 0.5 }
    })
    particle.spawn("minecraft:electric_spark", { x = offset_x, y = offset_y, z = offset_z }, {
      velocity = { look.x * 0.3, 0.1, look.z * 0.3 }
    })
  end

  -- ตรวจจับศัตรูในแนวฟันดาบด้วย world.getEntities
  if world and world.getEntities then
    local targets = world.getEntities(7)
    for _, target in ipairs(targets) do
      if target.is_monster then
        particle.spawn("minecraft:crit", target:getPos(), { velocity = { 0, 0.5, 0 } })
        sounds:playSound("minecraft:entity.player.attack.crit", target:getPos(), 1.0, 1.1)
        if fx and fx.damage then
          fx.damage(250, target:getPos(), { crit = true })
        end
      end
    end
  end
end

function SignatureWeapon.tick(state, config)
  if SignatureWeapon.blade_cooldown > 0 then
    SignatureWeapon.blade_cooldown = SignatureWeapon.blade_cooldown - 1
  end

  -- ซิงก์กับไอเทมในมือหลัก (ถ้าถือดาบจะชักอาวุธอัตโนมัติ เฉพาะเมื่อไม่ได้ตั้งเป็น hidden)
  local held_main = minecraft.player.held_item("main")
  if held_main and not held_main.empty and string.find(tostring(held_main.id), "sword") then
    if SignatureWeapon.mode ~= "hidden" and SignatureWeapon.mode ~= "drawn" then
      SignatureWeapon.mode = "drawn"
      SignatureWeapon.apply_visibility()
    end
  end
end

return SignatureWeapon
