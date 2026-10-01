# Aether Power & Item Showcase

แพ็กตัวอย่างที่ใช้ทดสอบ **Power Deck**, **Item Catalog**, PNG icon และ gameplay script ฝั่ง server ในชุดเดียว มีพลัง 3 แบบและไอเท็มกดใช้ 3 ชิ้น:

| พลัง | มานา / คูลดาวน์ | ไอเท็มที่กดใช้ |
| --- | --- | --- |
| Aether Bolt | 12 / 0.8 วินาที | Aether Focus |
| Aether Ward | 24 / 4 วินาที | Aether Wardstone |
| Aether Starfall | 50 / 9 วินาที | Aether Star Shard |

`main.lua` ทำให้ Bolt และ Starfall ยิง projectile จริงจาก server และ Ward เล่นเสียงพร้อมข้อความตอบกลับ ส่วนค่า mana, cooldown และการปฏิเสธการใช้ถูกตรวจโดย `SkillExecutor` ฝั่ง server ก่อน Lua เสมอ

## ติดตั้งและลองใช้

1. คัดลอกโฟลเดอร์นี้ทั้งโฟลเดอร์ไปยัง server หรือโลก singleplayer ที่:

   ```text
   <Minecraft instance>/shyne-mods/aether-showcase-pack/
   ```

2. เข้าเกมในฐานะผู้ดูแล แล้วรัน:

   ```text
   /shyne reload
   /shyne items
   /shyne skills
   ```

3. รับไอเท็มโดยแทน `<your-name>` ด้วยชื่อผู้เล่นจริง:

   ```text
   /shyne giveitem <your-name> aether.focus 1
   /shyne giveitem <your-name> aether.wardstone 1
   /shyne giveitem <your-name> aether.star_shard 1
   ```

4. เปิด `Esc → Powers → Actions & Keys` แล้วเลือก Aether Bolt, Aether Ward หรือ Aether Starfall จากรายการซ้าย กด **Add & bind** และกดปุ่มที่ผูกไว้เพื่อทดสอบ Power Deck
5. เปิด `Esc → Items` เพื่อดูไอคอน PNG, คำอธิบาย, rarity, mana action และ cooldown ของทั้งสามไอเท็ม แล้วลองคลิกขวาไอเท็มในมือ

> Mana HUD จะปรากฏก็ต่อเมื่อผู้เล่นเปิดใช้ Avatar และมี action ที่ใช้ mana อยู่ใน deck แล้วเท่านั้น นี่เป็นพฤติกรรมตั้งใจของ UI เพื่อไม่ให้แสดง `100 / 100` หลอกในโลกที่ยังไม่มี Power

## สิ่งที่อ่านต่อได้จากแพ็กนี้

- `shyne-package.json` ประกาศ PNG ที่เกมใช้จริง โดย SVG ใน `sources/icons/` เป็น source สำหรับแก้ใน Figma/Illustrator/React เท่านั้น
- `skills/*.json` เป็น data contract ของ action; ไม่ต้องเขียน Java หรือกำหนด rarity เพื่อให้ creator สร้างพลังได้
- `items/*.json` ใช้ `use_skill` เพื่อให้คลิกขวาไอเท็มเรียก skill เดียวกับ Power Deck
- `main.lua` รับ `on_skill_key(ctx)` หลัง server อนุมัติการใช้พลัง แล้วจึงส่ง effect ที่ผู้สร้างออกแบบเอง

ตรวจแพ็กก่อนนำไปใช้จริง:

```powershell
python .\tools\creator\shyne_creator.py validate-pack .\tools\examples\aether-showcase-pack
```
