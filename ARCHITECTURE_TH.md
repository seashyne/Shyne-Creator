# Shyne Creator Architecture

เอกสารนี้อธิบายขอบเขตของไฟล์หลัก เพื่อให้แก้ระบบ Avatar โดยไม่ต้องเดาจากไฟล์ขนาดใหญ่

## แบ่งความรับผิดชอบ

| ส่วน | หน้าที่ | ข้อควรระวัง |
|---|---|---|
| `common/` | model parser, avatar state, Lua contract, animation state และ UI ที่ไม่ผูก loader | ห้าม import Fabric/NeoForge |
| `fabric/` | bridge ของ Fabric, renderer, network และ Minecraft client API | ต้องรักษา API ให้เท่ากับ NeoForge |
| `neoforge/` | bridge ของ NeoForge ที่มีหน้าที่เท่ากัน | เปลี่ยนพฤติกรรม avatar ต้องแก้คู่กับ Fabric |
| `shyne_avatar.lua` | index อธิบายลำดับโมดูล Lua Standard 2.0 | ไม่เก็บ implementation ขนาดใหญ่ในไฟล์นี้ |
| `shyne_runtime/lua/avatar/*.lua` | core API แยกเป็น vector/model-animation/avatar-world/render/easy API | host ต้องต่อทุกไฟล์แล้ว compile เป็น Lua chunk เดียวเพื่อรักษา scope ของ `local` |
| `AvatarPhysicsController` | physics preset จาก Blockbench และ additive layer `shyne.physics` | ห้ามเขียนทับ rotation หลักหรือ Lua layer |
| `AvatarRenderTaskRegistry` | snapshot ของ HUD/world task, budget, culling, lighting และ native bone binding | world task ต้องไม่ย้อนกลับไป project ลง HUD |
| `AvatarMatrixDecomposition` | แปลง renderer matrix เป็น world rotation/scale สำหรับ Lua ให้ Fabric/NeoForge ใช้กฎเดียวกัน | ต้องลบ Blockbench Y reflection ก่อนคืน rotation |
| `shyne_rig.lua` | optional Native Rig: spring, chain, armor และ attachment | physics ต้องใช้ `rot_add()` เท่านั้น |

## ลำดับ transform ของ renderer

`vanilla parent transform → Blockbench animation/local bone → direct Lua rotation → additive layers (Lua + native physics + constraints) → child bone`

ลำดับนี้ทำให้ `parent_type: "Head"` ตามหัวจริง และหูที่กำลังกระดิกยังหมุนตามหัวได้. `part:rot()` ควบคุม rotation หลักและแทน animation channel; `part:rot_add()` เป็นเพียง offset ที่บวกท้ายสุด

## Render lifecycle และ bone attachment

`AvatarFrameMixin` เปิด event render/world หนึ่งครั้งต่อเฟรมจริง ไม่ผูกกับ client tick ส่วน renderer จะ publish bone matrix ระหว่าง feature submission ก่อน `AvatarWorldRenderMixin` ส่ง world task ที่ท้าย `LevelRenderer.submitFeatures` ลำดับนี้ทำให้ task ที่ผูก bone ใช้ matrix เฟรมปัจจุบันโดยไม่ต้องให้ Lua คำนวณซ้ำใน `post_render`

World task ใช้ camera-relative PoseStack, depth-tested render type, world light และ frustum/distance budget โดยตรง `billboard = false` จะสืบทอด matrix ของ bone ส่วน `billboard = true` จะรับเฉพาะตำแหน่งแล้วหันเข้ากล้อง พิกัดและความยาวเส้นต้องถูกจำกัดก่อนสร้าง GPU vertex และ exception ของ task หนึ่งต้องไม่ทำให้ทั้ง LevelRenderer ล้ม

## State dirty lane

ค่าถาวร เช่น visibility, parent, material และ manifest ใช้ `snapshotDirty` เพื่อส่งทันที การเคลื่อนไหวถี่สูงจาก Lua/physics ใช้ `poseDirty` และส่งประมาณ 10 Hz ส่วน `avatar.state` ที่ไม่ sync ห้ามทำให้เกิด network snapshot วิธีนี้ป้องกัน physics 20 Hz จากการบังคับส่ง state เต็มทุก tick โดยไม่ทำให้การเปลี่ยน visibility ช้า

dirty revision จะถูกยืนยันต่อเมื่อ client ได้รับ ACK echo จาก server เท่านั้น การส่งสำเร็จในเครื่องยังไม่ถือว่าสำเร็จบน server หาก packet ถูก rate-limit client จะรวม state ล่าสุดแล้ว retry โดย full model/clear จะค้างจน ACK กลับมา ฝั่งรับ interpolate transform ระหว่าง snapshot 100 ms และไม่รับ server echo ของผู้เล่น local กลับมาทับ pose เฟรมปัจจุบัน เวลา animation บนสายส่งเป็น elapsed age แล้ว rebase ที่แต่ละเครื่อง จึงไม่ขึ้นกับ system clock ของผู้เล่น Part path รองรับชื่อ Blockbench ที่มีช่องว่างและ Unicode แต่ยังจำกัดความยาว/จำนวน/ค่าตัวเลขและปฏิเสธ NaN/Infinity

## Animation coordinate version

Blockbench `.bbmodel` format 4.x เก็บแกน animation ในทิศที่ Figura V4 และ Shyne renderer ใช้ได้ตรงๆ จึงห้ามกลับเครื่องหมายซ้ำ ส่วน format 5.x ต้องแปลง X ของ position และ X/Y ของ rotation ตามกฎ V5 การตัดสินใจนี้อิง `meta.format_version` ไม่ใช่เดาจากชื่อไฟล์ Expression `Math.sin/cos` และ `q.anim_time` ประมวลผลแบบ degree/Molang ใน sandbox ของ Shyne โดยไม่โหลด `Molang.lua`

## ทำไมยังมี Java ซ้ำระหว่าง Fabric กับ NeoForge

สอง loader ใช้ event/network/renderer API คนละชุด แม้ logic Avatar จะเหมือนกัน. ข้อมูลที่แชร์ได้ถูกอยู่ใน `common` แล้ว; adapter ที่เหลือจึงต้องมีสองไฟล์และ build มี `verifyLoaderParity` คอยตรวจ. เมื่อย้าย code ใหม่ ให้เริ่มจาก `common` ก่อน แล้วทำ adapter บางที่สุดเท่าที่ทำได้

## กติกาการเพิ่ม feature

1. เพิ่ม state และ test ใน `common` ก่อน
2. เพิ่ม bridge ใน Fabric และ NeoForge ให้ parity ผ่าน
3. เพิ่ม API หลักในโมดูลย่อย `shyne_runtime/lua/avatar/` หรือแยกระบบ optional เป็น `shyne_runtime/lua/shyne_<feature>.lua`; ห้ามทำ index กลับไปเป็นไฟล์ 800+ บรรทัด
4. เพิ่ม comment ที่อธิบายเหตุผล, transform order, sync และข้อจำกัด—not comment ที่บอกเพียงว่าโค้ดบรรทัดนั้นทำอะไร
5. อัปเดต API standard, schema, docs และ protocol เมื่อ payload เปลี่ยน
