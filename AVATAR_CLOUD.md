# Shyne Avatar Cloud

Shyne Avatar Cloud มีสองส่วนที่แยกสิทธิ์กัน: Private Backup สำหรับเจ้าของบัญชี และ Public Share สำหรับผู้สร้างที่ตั้งใจเผยแพร่ Avatar ให้คนอื่นทดลองใช้

## สำหรับผู้เล่น

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

## สถาปัตยกรรม

- D1: account, challenge, session, ownership และ version
- R2: content-addressed chunks ที่ key `chunks/<sha256>` แต่ Worker ตรวจ owner ทุกครั้งก่อนอ่าน
- Worker: ตรวจ Minecraft session, authorization, manifest, size และ hash
- Minecraft server: skill, power, combat, profile และ peer Avatar state

Cloud ล่มแล้ว LAN/Server gameplay ยังทำงานได้ Avatar ที่อยู่ในเครื่องและ cache ยังใช้งานได้

## Public Share

ผู้สร้างเลือก Publish เองและกำหนด visibility, license กับ permission manifest ได้ ผู้ใช้คนอื่นเห็นเฉพาะรายการที่ Public ใน Discover และต้อง Sign in พร้อมอนุมัติสิทธิ์อันตรายก่อนใช้ ตัวไฟล์ `.sc` ถูกเข้ารหัส เซ็นกำกับ และใช้ lease อายุสั้นที่ผูกกับบัญชีและเครื่อง เมื่อผู้สร้าง Revoke ระบบหยุดออก lease ใหม่และ Avatar ที่กำลังใช้จะหยุดเมื่อ lease หมด

Public Share ช่วยป้องกันการแก้ไฟล์และแจกต่อแบบทั่วไป แต่ไม่รับประกันว่าจะกันการดึงข้อมูลจาก client ที่ถูกดัดแปลงได้ 100% เพราะเครื่องผู้เล่นต้องถอดรหัสข้อมูลในหน่วยความจำเพื่อเรนเดอร์ในที่สุด

รายละเอียด HTTP อยู่ใน `CLOUD_API.md`
