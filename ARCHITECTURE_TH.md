# Shyne Creator Architecture

เอกสารนี้อธิบายขอบเขตของไฟล์หลัก เพื่อให้แก้ระบบ Avatar โดยไม่ต้องเดาจากไฟล์ขนาดใหญ่

## แบ่งความรับผิดชอบ

| ส่วน | หน้าที่ | ข้อควรระวัง |
|---|---|---|
| `common/` | model parser, avatar state, Lua contract, animation state และ UI ที่ไม่ผูก loader | ห้าม import Fabric/NeoForge |
| `fabric/` | bridge ของ Fabric, renderer, network และ Minecraft client API | ต้องรักษา API ให้เท่ากับ NeoForge |
| `neoforge/` | bridge ของ NeoForge ที่มีหน้าที่เท่ากัน | เปลี่ยนพฤติกรรม avatar ต้องแก้คู่กับ Fabric |
| `shyne_avatar.lua` | core Lua API: vector, model, player/world, event, scheduler, UI | เป็น API เสถียร ไม่ควรใส่ feature ใหญ่ใหม่เพิ่ม |
| `shyne_rig.lua` | optional Native Rig: spring, chain, armor และ attachment | physics ต้องใช้ `rot_add()` เท่านั้น |

## ลำดับ transform ของ renderer

`vanilla parent transform → Blockbench animation/local bone → Lua rot_add physics → child bone`

ลำดับนี้ทำให้ `parent_type: "Head"` ตามหัวจริง และหูที่กำลังกระดิกยังหมุนตามหัวได้. `part:rot()` ควบคุม rotation หลักและแทน animation channel; `part:rot_add()` เป็นเพียง offset ที่บวกท้ายสุด

## ทำไมยังมี Java ซ้ำระหว่าง Fabric กับ NeoForge

สอง loader ใช้ event/network/renderer API คนละชุด แม้ logic Avatar จะเหมือนกัน. ข้อมูลที่แชร์ได้ถูกอยู่ใน `common` แล้ว; adapter ที่เหลือจึงต้องมีสองไฟล์และ build มี `verifyLoaderParity` คอยตรวจ. เมื่อย้าย code ใหม่ ให้เริ่มจาก `common` ก่อน แล้วทำ adapter บางที่สุดเท่าที่ทำได้

## กติกาการเพิ่ม feature

1. เพิ่ม state และ test ใน `common` ก่อน
2. เพิ่ม bridge ใน Fabric และ NeoForge ให้ parity ผ่าน
3. แยก Lua feature ใหญ่เป็น `shyne_runtime/lua/shyne_<feature>.lua`
4. เพิ่ม comment ที่อธิบายเหตุผล, transform order, sync และข้อจำกัด—not comment ที่บอกเพียงว่าโค้ดบรรทัดนั้นทำอะไร
5. อัปเดต API standard, schema, docs และ protocol เมื่อ payload เปลี่ยน
