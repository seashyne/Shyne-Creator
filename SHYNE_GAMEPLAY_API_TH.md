# Shyne Gameplay API

Gameplay script เป็นโค้ดที่เจ้าของเซิร์ฟเวอร์ติดตั้งและให้ Server เป็นผู้ตัดสิน จึงแยกจาก Avatar Lua Standard ซึ่งทำงานบน Client

```lua
events.on("player_join", function(ctx)
  minecraft.message("ยินดีต้อนรับ", ctx.player)
end)

minecraft.command("time set day")
minecraft.world.set_block(0, 80, 0, "minecraft:stone")
minecraft.item.give("minecraft:apple", 3, player_id)
minecraft.item.give_shyne("aether_crystal", 1, player_id)
minecraft.sound.play("minecraft:block.amethyst_block.chime", player_id)
minecraft.task.after(20, "on_tick")

model.load("bbmodels/effect.bbmodel")
model.attach("bbmodels/effect.bbmodel", { player = player_id, scale = 1.0 })
model.play("bbmodels/effect.bbmodel", "pulse", player_id)
model.stop(player_id)
model.detach(player_id)
```

สิทธิ์และผลลัพธ์จริงต้องผ่านตัวตรวจของ Shyne/Minecraft Server เสมอ ไม่ควรเชื่อค่า damage, mana, permission หรือ cooldown ที่ส่งจาก Client
