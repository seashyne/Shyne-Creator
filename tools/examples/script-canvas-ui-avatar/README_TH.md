# Script Canvas UI Avatar

ตัวอย่างหน้า UI เต็มจอที่ Creator เขียนเองทั้งหมดด้วย Lua ไม่มี template, button หรือธีมของ Shyne ถูกวาดทับ

1. คัดลอกทั้งโฟลเดอร์ไปที่ `.minecraft/shyne-mods/avatars/`
2. เปิด Avatar จากคลัง และอนุมัติ `hud_render`
3. กด `U` เพื่อเปิดหน้า Canvas

ตัวอย่างแสดง `ui.canvas`, `canvas:rect/text/outline`, `canvas:button`, callback คลิก, การจัดตำแหน่ง responsive และ `canvas:close()`. เปลี่ยน visual หรือสร้างองค์ประกอบใหม่ได้ผ่าน `render.*`; button เป็นเพียง hitbox โปร่งใส จึงไม่มีหน้าตาใดถูกบังคับโดยม็อด
