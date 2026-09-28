# มาตรฐานประสิทธิภาพ Shyne Creator

สถานะ: เกณฑ์เริ่มต้นสำหรับการทดสอบรุ่น 2.12.0 (28 กันยายน 2026)  
ขอบเขต: Fabric และ NeoForge บน Minecraft 26.3

## ผลตรวจรอบแรก

การตรวจครั้งนี้อ่านโค้ดและรัน `:fabric:test :neoforge:test verifyLoaderParity` สำเร็จ โดยแต่ละ Loader ผ่าน 143 การทดสอบ ไม่มีข้อผิดพลาดหรือการข้ามการทดสอบ ผลนี้ยืนยันการคอมไพล์และพฤติกรรมที่มี test ครอบคลุม แต่ **ยังไม่ได้วัด FPS, เวลาเฟรม, MSPT, หน่วยความจำจริง หรือทราฟฟิกเครือข่ายระหว่างเล่นเกม** จึงยังไม่ให้สถานะ “ผ่านมาตรฐานประสิทธิภาพ” แก่รุ่นนี้

สิ่งที่มีอยู่แล้ว:

- `AvatarProfiler` เก็บเวลา Lua, โมเดล และ render task แบบ rolling 240 ตัวอย่าง พร้อม Export JSON จากหน้า `Shyne Settings → Advanced → Avatar Profiler`.
- Render task จำกัด 256 งานต่อ Avatar, วาดไม่เกิน 128 งานต่อ pass และมีงบสำหรับจุดเส้นกับตัวอักษร (`AvatarRenderTaskRegistry`).
- Pose snapshot ถูกหน่วงอย่างน้อย 80 ms และระบบเครือข่ายมีเพดานขนาดแพ็กเก็ตกับ rate limit (`AvatarSnapshotSync`, `ShyneNetworkValidator`).
- Audio stream จำกัดจำนวน active stream ไว้ 8 และมีการรอเมื่อคิวเสียงสะสม (`AvatarAudioStreamManager`, `AvatarAudioStream`).

## ประเด็นที่ต้องแก้หรือวัดเพิ่ม

| ลำดับ | ประเด็น | ผลต่อการประเมิน/การเล่น | สิ่งที่ต้องทำ |
|---|---|---|---|
| สูง | `AvatarProfiler.snapshot()` นำ **ค่าเฉลี่ยต่อครั้งที่ถูกเรียก** ของ Lua render, model render และ task render มาบวกเป็น `avatarFrameMs` แต่หนึ่งเฟรมอาจเรียกแต่ละหมวดหลายครั้ง; HUD กับ world task ยังบันทึกลงหมวดเดียวกัน | ตัวเลข “Avatar/frame” และ “Estimated FPS loss” อาจต่ำหรือสูงกว่าต้นทุนจริง ห้ามใช้เป็นเกณฑ์ผ่านเดี่ยว ๆ | เก็บเวลารวมตาม frame id แล้วรายงาน median/p95 ต่อเฟรม; วัด FPS เทียบ baseline จากเกมจริง |
| สูง | `ShyneServerPolicy` ตั้งจำนวน projectile, summon และอัตรา cast เป็น unlimited; `ProjectileRuntime.tick()` ค้นหา entity และเป้าหมายให้ projectile ทุกตัว | Content Pack ที่สร้างวัตถุจำนวนมากอาจเพิ่ม MSPT โดยไม่มีเพดานค่าเริ่มต้น | วัดกรณีสกิลหนักหลายผู้เล่น และกำหนด quota สำหรับเซิร์ฟเวอร์ที่ต้องการรักษา TPS; เก็บค่า active projectile/summon ในรายงาน |
| กลาง | `AvatarRenderTaskRegistry.snapshots()` สร้างรายการใหม่และเรียง `zIndex` ทั้งใน HUD และ world pass ทุกเฟรม | เกิด allocation และ sort ซ้ำเมื่อจำนวน task สูง | วัด allocation และเวลาในกรณี 128/256 task; หากเป็นคอขวด ให้ cache รายการจนกว่าจะมีการเปลี่ยน task |
| กลาง | ตัวเลข `avatarBytes` ใน profiler ใช้ขนาดไฟล์บนดิสก์รวมกับขนาด task โดยประมาณ | ไม่ใช่ RAM หรือ VRAM ของ Avatar จริง | แยก disk bytes, heap และ texture/GPU estimate ในรายงานก่อนใช้เป็นเกณฑ์หน่วยความจำ |

## เกณฑ์ผ่านสำหรับรุ่นถัดไป

ตัวเลขด้านล่างเป็น **เป้าหมายเริ่มต้นของโครงการ** ไม่ใช่ผลที่วัดได้แล้ว ปรับได้หลังเก็บ baseline บนเครื่องทดสอบเดิมและบันทึกเหตุผลไว้

| หมวด | เกณฑ์ผ่าน | วิธีวัด |
|---|---|---|
| ความถูกต้อง | `:fabric:test`, `:neoforge:test`, `verifyLoaderParity` ผ่านทั้งหมด | รันกับ source เดียวกับ JAR ที่จะเผยแพร่ |
| Client ว่าง | Shyne ที่ยังไม่เลือก Avatar เพิ่ม median frame time ไม่เกิน 1 ms และ p95 ไม่เกิน 2 ms เมื่อเทียบกับเกม/Loader เดียวกันที่ไม่มี Shyne | บันทึกเวลาเฟรมจริงในฉากเดิมหลัง warm-up |
| Avatar มาตรฐาน | ไม่มี freeze เกิน 100 ms ที่เกิดซ้ำขณะเดิน หมุนกล้อง เล่น animation และสลับมุมมอง; บันทึก median/p95 frame time เทียบ Client ว่าง | อย่างน้อย 3 รอบต่อ Loader; รายงานค่าเปลี่ยนแปลง ไม่ใช้ FPS เฉลี่ยค่าเดียว |
| Server | ที่ 8 ผู้เล่นจำลองกับ Avatar และ Content Pack ตัวแทน เกมรักษา 20 TPS; p95 MSPT ไม่เกิน 50 ms และเพิ่มจาก baseline ไม่เกิน 5 ms | วัด server tick พร้อมจำนวน projectile/summon สูงสุดที่พบ |
| เครือข่าย | Pose ที่เปลี่ยนต่อเนื่องส่งไม่เกิน 10 ครั้ง/วินาทีต่อผู้เล่น และ full model ไม่ถูกส่งซ้ำต่อเนื่องหลัง ACK | ตรวจจำนวน/ขนาด packet ในช่วง steady state 3 นาทีและช่วง reconnect |
| หน่วยความจำ | หลังสลับหรือ reload Avatar เดิม 10 ครั้งและรอ GC ขนาด heap ที่ยังใช้อยู่ไม่เพิ่มต่อเนื่อง; ไม่มี thread, texture หรือ audio stream ค้าง | วัด heap/จำนวน thread ก่อนและหลัง พร้อมทำซ้ำอีกหนึ่งรอบ |
| ความปลอดภัยเมื่อโหลดหนัก | เมื่อเกินงบ render/network ระบบลดหรือปฏิเสธงานโดยไม่ทำให้เกมค้างหรือเซิร์ฟเวอร์ล้ม | ทดสอบ Avatar/task/packet ใกล้เพดานทั้งสอง Loader |

## ชุดทดสอบที่ต้องเก็บก่อนปล่อยรุ่น

1. ใช้เครื่อง, Java 25, ความละเอียด, render distance, shader, modpack และ world เดิมตลอดการเปรียบเทียบ บันทึก CPU, GPU, RAM และรุ่นไดรเวอร์
2. วัด 4 สถานการณ์บน Fabric และ NeoForge: เกมที่ไม่มี Shyne, Shyne ว่าง, Avatar ตัวอย่างปกติ, และฉากหนักที่มี 8 ผู้เล่น/Avatar พร้อม render task และสกิล
3. Warm-up อย่างน้อย 2 นาที แล้วเก็บข้อมูล 3 นาทีต่อรอบ ทำ 3 รอบต่อสถานการณ์ รายงาน median และ p95 ของเวลาเฟรม/เวลา server tick; เก็บ FPS และ MSPT ประกอบ
4. เปิด Avatar Profiler แล้ว Export JSON ในแต่ละรอบ เก็บจำนวน task, ขนาด Avatar, ค่า Lua/model/task และ warnings พร้อม log ของเกม ค่า `avatarFrameMs` และ `estimatedFpsLoss` ใช้ชี้จุดตรวจต่อเท่านั้นจนกว่าจะแก้การรวมเวลาต่อเฟรม
5. ทดสอบการเข้าร่วม/ออกจากเซิร์ฟเวอร์ การเปลี่ยน Avatar 10 ครั้ง, การเปิดเสียง stream และกรณีสกิลที่สร้าง projectile/summon มาก เพื่อดูการคืนทรัพยากรและ spike
6. ถ้าค่าเกินเกณฑ์ ให้แนบฉากทดสอบ, profiler JSON, กราฟเวลาเฟรม/MSPT, จำนวน entity และ build hash ก่อนแก้ แล้ววัดซ้ำด้วยเงื่อนไขเดิม

## ข้อกำหนดสำหรับงานพัฒนาต่อ

- โค้ดบน render thread หรือ server tick ต้องมีต้นทุนที่เพิ่มตามจำนวน Avatar, task, entity หรือ packet อย่างชัดเจน พร้อมเพดานหรือการลดงานเมื่อเกินงบ
- งานอ่านไฟล์ ดาวน์โหลด ถอดรหัส และสร้าง asset ต้องไม่หยุด render/server tick; เมื่อ unload ต้องปล่อย worker, stream และ GPU resource
- อย่าถือว่า `maximumMs` จาก rolling 240 calls เท่ากับ p95 frame time; อย่าถือว่าขนาดไฟล์บนดิสก์เท่ากับหน่วยความจำที่ใช้
- ทุกการปรับประสิทธิภาพต้องเทียบก่อน/หลังในฉากเดียวกัน และต้องผ่านทั้ง Fabric กับ NeoForge
