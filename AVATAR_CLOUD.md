# Shyne Avatar Cloud

Shyne Avatar Cloud 2.2 แยกข้อมูลเป็นสองส่วนชัดเจน: **Private Backup** สำหรับเจ้าของบัญชี และ **Public Share** สำหรับ Avatar ที่ผู้สร้างตั้งใจเผยแพร่เป็น ZIP

## Private Backup สำหรับผู้เล่น

1. เปิด `Cloud` ใน Shyne Creator Settings
2. เปิด Avatar Manager ด้วยปุ่ม `H` แล้วเข้า `Avatar Cloud`
3. กด `Minecraft sign-in` ระบบใช้ session ที่ Minecraft Launcher ล็อกอินอยู่และไม่ขอรหัสผ่าน
4. เลือก Avatar ในเครื่องแล้วกด `Back up active Avatar`
5. บนเครื่องอื่น ให้ยืนยันด้วยบัญชีเดิม เลือกรายการ แล้วกด `Restore to this PC`

Avatar ในเครื่องยังเป็นโฟลเดอร์ `.minecraft/shyne-mods/avatars/<avatar-id>/` ตามปกติ Cloud แบ่งไฟล์เป็น chunk ภายใน ตรวจ SHA-256 ทุกก้อน และติดตั้งกลับมาเป็นโฟลเดอร์ปกติหลัง validation ผ่าน

หน้าจอแสดง progress, cancel, retry และสาเหตุเมื่อใช้งานปุ่มไม่ได้ การรับส่ง chunk ที่ล้มเหลวจะลองใหม่สูงสุด 3 ครั้ง

## Private Backup และความเป็นส่วนตัว

- รายการ Avatar, manifest และ chunk ต้องมี Shyne session ของเจ้าของ
- บัญชีอื่นค้นหา ดู metadata หรือดาวน์โหลดไฟล์ไม่ได้ แม้รู้รหัส Avatar หรือ hash ของ chunk
- รหัส Avatar เป็น namespace ภายในแต่ละบัญชี ผู้ใช้ต่างบัญชีจึงสำรอง Avatar ที่ใช้รหัสเดียวกันได้
- Cloud เก็บ Minecraft UUID, ชื่อล่าสุด, Shyne session token แบบ hash, metadata และไฟล์ Avatar
- Cloud ไม่เก็บ Microsoft/Minecraft access token, รหัสผ่าน, skill, power, damage หรือ cooldown

Shyne session ฝั่งเครื่องอยู่ที่ `config/shyne-creator/cloud-session.json` และหมดอายุภายใน 30 วัน การ Sign out จะขอเพิกถอน session บน backend

## Public Share

ผู้สร้างกด Publish เองและกำหนด license กับ permission manifest ได้ Backend รับ ZIP จาก Shyne Creator แล้วตรวจโครงสร้าง ขนาด เส้นทางไฟล์ และ SHA-256 ก่อนเก็บ ผู้เล่นค้นหา metadata ได้ใน Discover แต่ต้อง Sign in และอนุมัติ permission ของ package hash นั้นก่อนดาวน์โหลดและเริ่ม runtime

Public Share 2.2 ใช้ ZIP มาตรฐานผ่าน HTTPS โดยตรง ไม่ใช้ `.sc v1`, `.sc v2`, data key หรือ lease แบบกำหนดเอง Client จะตรวจ package hash ที่ได้รับจาก metadata อีกครั้งก่อนแตกไฟล์ และตัว extractor ยังบังคับจำนวนไฟล์ ขนาดรวม และเส้นทางปลอดภัย

เมื่อผู้สร้างกด Revoke ระบบลบ Public ZIP และปฏิเสธการดาวน์โหลดครั้งใหม่ โดยไม่กระทบ Private Backup การ Revoke ไม่สามารถลบสำเนาที่ผู้เล่นดาวน์โหลดหรือติดตั้งไปแล้วจากระยะไกล ผู้สร้างจึงควร Publish เฉพาะไฟล์ที่ยินยอมให้ผู้รับดาวน์โหลดเท่านั้น

## สถาปัตยกรรม

- D1: account, challenge, session, ownership, manifest, publication และ metadata
- R2 Private: chunk แยก namespace ตามเจ้าของที่ `private/<owner-uuid>/chunks/<sha256>`
- R2 Public: ZIP แต่ละ publication ที่ `public/<share-id>/<object-id>.zip`
- Worker: ตรวจ Minecraft session, authorization, manifest, ZIP policy, size และ hash
- Minecraft server: skill, power, combat, profile และ peer Avatar state

Cloud ล่มแล้ว LAN/Server gameplay ยังทำงานได้ Avatar ที่อยู่ในเครื่องยังใช้งานได้ตามปกติ

รายละเอียด HTTP อยู่ใน [CLOUD_API.md](CLOUD_API.md) และขอบเขต Public ZIP อยู่ใน [PUBLIC_SHARE.md](PUBLIC_SHARE.md)
