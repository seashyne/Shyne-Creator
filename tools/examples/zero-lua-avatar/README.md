# Zero-Lua Avatar

ตัวอย่างเริ่มต้นสำหรับ Shyne Avatar Standard 2.0 ไม่มี script และไม่มี texture จากบุคคลอื่น

เปิด `model.bbmodel` ใน Blockbench แล้วสร้างชิ้นส่วนใต้ `HeadAccessory` ชิ้นส่วนนั้นจะตามหัว Minecraft ผ่าน `parent_type: "Head"` โดยอัตโนมัติ

เพิ่ม animation ใน Blockbench แล้วกำหนด Idle, Walk, Sprint, Swim หรือ Blink ผ่าน Shyne Standard 2.0 Plugin ได้โดยไม่ต้องเขียน Lua

ตัวอย่างนี้ตั้งใจให้ `avatar.json` มีเพียง `name` เพื่อยืนยันว่าค่า Standard, ID, model, profile และ Auto Animation ใช้ค่าเริ่มต้นได้จริง ID จะมาจากชื่อโฟลเดอร์ Avatar
