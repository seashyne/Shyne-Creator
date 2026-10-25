import overview from '../../README.md?raw'
import playerQuickstart from '../../docs/guides/PLAYER_QUICKSTART_TH.md?raw'
import quickstart from '../../docs/guides/CREATOR_QUICKSTART_TH.md?raw'
import standard from '../../docs/standards/SHYNE_STANDARD_2_TH.md?raw'
import apiContracts from '../../docs/api/API_CONTRACTS_TH.md?raw'
import blockbenchPlugin from '../../tools/blockbench/README_TH.md?raw'
import blockbench from '../../docs/guides/BLOCKBENCH_ANIMATION_STANDARD.md?raw'
import avatarSystem from '../../docs/architecture/AVATAR_SYSTEM.md?raw'
import rig from '../../docs/api/RIG_API_TH.md?raw'
import lua from '../../docs/api/SHYNE_LUA_API_TH.md?raw'
import render from '../../docs/api/CUSTOM_RENDER_API_TH.md?raw'
import gameplay from '../../docs/api/SHYNE_GAMEPLAY_API_TH.md?raw'
import powerAssets from '../../docs/standards/POWER_ASSET_PACKAGE_TH.md?raw'
import completeApi from '../../docs/api/SHYNE_API_COMPLETE_TH.md?raw'
import globalsReference from '../../docs/api/GLOBALS_REFERENCE_TH.md?raw'
import cloud from '../../docs/architecture/AVATAR_CLOUD.md?raw'
import cloudApi from '../../docs/api/CLOUD_API.md?raw'
import publicShare from '../../docs/guides/PUBLIC_SHARE.md?raw'
import security from '../../docs/legal/SECURITY.md?raw'
import multiplayer from '../../docs/architecture/MULTIPLAYER_TESTING.md?raw'
import sdk from '../../docs/standards/CREATOR_SDK_TH.md?raw'
import architecture from '../../docs/architecture/ARCHITECTURE_TH.md?raw'
import architectureReport from '../../docs/architecture/ARCHITECTURE_REPORT_TH.md?raw'
import licenseDoc from '../../docs/legal/LICENSE_TH.md?raw'
import compatMatrix from '../../docs/api/API_COMPATIBILITY_MATRIX.md?raw'
import changelog from '../../CHANGELOG.md?raw'
import scriptRecipes from '../../docs/guides/SCRIPT_RECIPES_TH.md?raw'
import zeroLuaExample from '../../tools/examples/zero-lua-avatar/README.md?raw'
import luaTwoExample from '../../tools/examples/lua-api-2.0-avatar/README.md?raw'
import advancedExample from '../../tools/examples/advanced-render-avatar/README_TH.md?raw'
import hudExample from '../../tools/examples/responsive-hud-avatar/README_TH.md?raw'
import profilerExample from '../../tools/examples/render-profiler-avatar/README_TH.md?raw'
import actionWheelExample from '../../tools/examples/action-wheel-showcase-avatar/README.md?raw'
import scriptCanvasExample from '../../tools/examples/script-canvas-ui-avatar/README_TH.md?raw'
import aetherShowcaseExample from '../../tools/examples/aether-showcase-pack/README_TH.md?raw'

// Import All Themed Tutorials
import tutorial01 from '../../docs/tutorials/01_PLAYER_BASICS_TH.md?raw'
import tutorial02 from '../../docs/tutorials/02_BLOCKBENCH_MODELING_TH.md?raw'
import tutorial03 from '../../docs/tutorials/03_ZERO_LUA_AVATAR_TH.md?raw'
import tutorial04 from '../../docs/tutorials/04_LUA_SCRIPTING_TH.md?raw'
import tutorial05 from '../../docs/tutorials/05_MULTIPLAYER_AND_PINGS_TH.md?raw'
import tutorial06 from '../../docs/tutorials/06_PHYSICS_AND_POLISH_TH.md?raw'
import tutorial07 from '../../docs/tutorials/07_POWERS_AND_SKILLS_TH.md?raw'
import tutorial08 from '../../docs/tutorials/08_CUSTOM_ITEMS_AND_3D_MODELS_TH.md?raw'

export type DocIcon = 'book' | 'download' | 'sparkles' | 'box' | 'play' | 'layers' | 'braces' | 'palette' | 'gamepad' | 'cloud' | 'shield' | 'users' | 'wrench' | 'workflow'

export type DocCategory = 'เริ่มต้น' | 'บทเรียน' | 'Blockbench' | 'สร้าง Avatar' | 'API และระบบ' | 'เผยแพร่และพัฒนา'

export type DocItem = {
  slug: string
  title: string
  shortTitle: string
  description: string
  category: DocCategory
  icon: DocIcon
  content: string
  api?: boolean
}

export type TutorialTopic = 'เริ่มต้น' | 'Blockbench 3D' | 'Zero-Lua' | 'เขียนสคริปต์ Lua' | 'ระบบออนไลน์' | 'ฟิสิกส์ & กราฟิก' | 'พลัง & ไอเท็ม'

export type TutorialItem = {
  id: string
  slug: string
  title: string
  shortTitle: string
  topic: TutorialTopic
  difficulty: 'พื้นฐาน' | 'ปานกลาง' | 'ขั้นสูง'
  duration: string
  description: string
  icon: DocIcon
  content: string
}

export const version = '2.18.1'
export const minecraftVersion = '26.3'

export const tutorials: TutorialItem[] = [
  {
    id: 'tutorial-player-basics',
    slug: 'tutorial-player-basics',
    title: 'บทเรียนที่ 1: เริ่มต้นใช้งาน Shyne Creator สำหรับผู้เล่น',
    shortTitle: '1. เริ่มต้นสำหรับผู้เล่น',
    topic: 'เริ่มต้น',
    difficulty: 'พื้นฐาน',
    duration: '3 นาที',
    description: 'ติดตั้งม็อดบน Fabric/NeoForge, วางไฟล์ Avatar (.zip), เปิดคลังด้วยเมาส์ และตั้งค่าไมค์ให้ปากขยับ',
    icon: 'gamepad',
    content: tutorial01,
  },
  {
    id: 'tutorial-blockbench-modeling',
    slug: 'tutorial-blockbench-modeling',
    title: 'บทเรียนที่ 2: ออกแบบโมเดล 3D และ Animation ด้วย Blockbench',
    shortTitle: '2. โมเดล 3D & Animation',
    topic: 'Blockbench 3D',
    difficulty: 'ปานกลาง',
    duration: '5 นาที',
    description: 'ตั้งโครงกระดูกให้ตรงกับ Vanilla, หู/หาง/Pivot Points, วาด Texture ดวงตาเรืองแสง Emissive และสร้าง Animation',
    icon: 'wrench',
    content: tutorial02,
  },
  {
    id: 'tutorial-zero-lua',
    slug: 'tutorial-zero-lua',
    title: 'บทเรียนที่ 3: สร้าง Avatar แบบ Zero-Lua (ไม่ต้องเขียนโค้ด)',
    shortTitle: '3. สร้าง Avatar แบบ Zero-Lua',
    topic: 'Zero-Lua',
    difficulty: 'พื้นฐาน',
    duration: '3 นาที',
    description: 'เขียน avatar.json มาตรฐาน, Auto Animations เดิน/วิ่ง/กะพริบตา, ปากขยับตามเสียงพูด และ Mask ซ่อนตัวละครเดิม',
    icon: 'sparkles',
    content: tutorial03,
  },
  {
    id: 'tutorial-lua-scripting',
    slug: 'tutorial-lua-scripting',
    title: 'บทเรียนที่ 4: การเขียนสคริปต์ Lua ตั้งแต่เริ่มต้น',
    shortTitle: '4. เขียนสคริปต์ Lua',
    topic: 'เขียนสคริปต์ Lua',
    difficulty: 'ปานกลาง',
    duration: '6 นาที',
    description: 'Event Lifecycle (TICK, RENDER, KEY_PRESS), สั่งขยับกระดูก, สร้าง Action Wheel 8 ช่อง และเล่นเสียง Sound Effects',
    icon: 'braces',
    content: tutorial04,
  },
  {
    id: 'tutorial-multiplayer-pings',
    slug: 'tutorial-multiplayer-pings',
    title: 'บทเรียนที่ 5: ระบบออนไลน์และการซิงก์ท่าทางให้เพื่อนเห็น (Pings RPC)',
    shortTitle: '5. ระบบออนไลน์ (Pings)',
    topic: 'ระบบออนไลน์',
    difficulty: 'ปานกลาง',
    duration: '4 นาที',
    description: 'ทำไมต้องใช้ pings, สร้างคำสั่งข้ามเครือข่าย, ส่งอาร์กิวเมนต์ตัวแปร และการเล่น Emote ให้เพื่อนในเซิร์ฟเวอร์เห็นพร้อมกัน',
    icon: 'users',
    content: tutorial05,
  },
  {
    id: 'tutorial-physics-and-polish',
    slug: 'tutorial-physics-and-polish',
    title: 'บทเรียนที่ 6: ฟิสิกส์และการตกแต่งขั้นสูง (Physics, HUD & Profiler)',
    shortTitle: '6. ฟิสิกส์ & เรนเดอร์ขั้นสูง',
    topic: 'ฟิสิกส์ & กราฟิก',
    difficulty: 'ขั้นสูง',
    duration: '5 นาที',
    description: 'Spring Physics สำหรับหูและหาง, ปรับแต่งแขน First-Person, วาด Custom HUD บนหน้าจอ และตรวจงบ Performance',
    icon: 'workflow',
    content: tutorial06,
  },
  {
    id: 'tutorial-powers-and-skills',
    slug: 'tutorial-powers-and-skills',
    title: 'บทเรียนที่ 7: การสร้างพลังและสกิลต่อสู้ (Powers & Combat Skills)',
    shortTitle: '7. พลัง & สกิลต่อสู้',
    topic: 'พลัง & ไอเท็ม',
    difficulty: 'ปานกลาง',
    duration: '5 นาที',
    description: 'สร้าง Gameplay Pack, กำหนด Skill JSON (Projectile, Mana, Cooldown), ปล่อยลูกศรเวทมนตร์ด้วย Lua และผูก Action Wheel',
    icon: 'sparkles',
    content: tutorial07,
  },
  {
    id: 'tutorial-custom-items',
    slug: 'tutorial-custom-items',
    title: 'บทเรียนที่ 8: สร้างไอเท็มและอาวุธ 3D ด้วย Blockbench (Custom 3D Items)',
    shortTitle: '8. ไอเท็ม & อาวุธ 3D',
    topic: 'พลัง & ไอเท็ม',
    difficulty: 'ปานกลาง',
    duration: '5 นาที',
    description: 'ปั้นโมเดล 3D ใน Blockbench, ตั้งค่า Presentation ในมือ/กระเป๋า/พื้น, ผูก use_skill คลิกขวายิงเวท และ Lua Hook on_item_use',
    icon: 'wrench',
    content: tutorial08,
  },
]

const installation = `# ติดตั้ง Shyne Creator

Shyne Creator **${version}** รองรับ Minecraft **${minecraftVersion}** บน Fabric และ NeoForge โดยม็อดทั้งสอง Loader ใช้ Mod ID \`shyne_creator\`, Shyne API Standard และ network protocol ชุดเดียวกัน

> **ไฮไลต์การอัปเดตรุ่น ${version}**:
> - 🎛️ **Zero-Friction In-Game Layer Toggles**: ปุ่มลัดเปิด/ปิดปีก (Elytra), ชุดเกราะ (Armor), ผ้าคลุม (Cape) และของสวมหัว (Head) ในหน้า Outfit Wardrobe และ Avatar Manager ทันที พร้อมซิงก์ให้เพื่อนในเซิร์ฟเวอร์เห็นตรงกัน
> - 📂 **Drag & Drop Installation**: ลากไฟล์ \`.zip\`, โฟลเดอร์ Avatar หรือไฟล์ \`.bbmodel\` และ \`.png\` วางลงในจอเกมเพื่อติดตั้งและนำเข้าได้ทันที
> - 🎥 **First-Person Body Mod Compatibility**: ตรวจจับและรองรับ FirstPersonModel, RealCamera, และ Shoulder Surfing Reloaded ซ่อนเฉพาะหัวไม่บังสายตา แต่แสดงลำตัว แขน ขา และปีก 3D ครบถ้วน
> - 🧩 **Built-in Figura Library Shims**: มีไลบรารีจำลอง \`gsanimblend\`, \`ezanims\`, \`kattarmor\` ในตัว รองรับโมเดลสำเร็จรูปจากชุมชน Figura ได้ทันที
> - 🌊 **Smart Anti-Clip Physics**: ฟิสิกส์ยกหางและชายเสื้อขึ้นอัตโนมัติเมื่อย่อตัว (Sneak) หรือขี่พาหนะ (Riding) ป้องกันจมทะลุพื้น และล็อกมุมสะบัดไม่ให้ผมหรือผ้าคลุมทะลุเข้ากลางลำตัว
> - 🛡️ **Zero Central Dependency & Secure Sandbox**: ส่งข้อมูลผ่าน Minecraft Server ของห้องโดยตรง ไร้คอขวดเซิร์ฟเวอร์กลาง และปลอดภัยสูงสุดด้วย Lua Sandbox ตัดสิทธิ์ OS/IO 100%

> Shyne Creator เป็นระบบสำหรับใช้ Avatar และ Content Pack ไม่ใช่ม็อดที่เพิ่ม Avatar หรือสกิลมาให้ทันทีหลังติดตั้ง หากต้องการเริ่มเล่น ให้ติดตั้งตัวม็อดแล้วอ่าน [เริ่มใช้ Shyne ใน 1 นาที](PLAYER_QUICKSTART_TH.md)

> ไฟล์ Fabric และ NeoForge เป็นคนละไฟล์ ห้ามใส่ทั้งสองไฟล์ใน Minecraft instance เดียวกัน

## เลือกไฟล์ให้ถูก

| คุณต้องการทำอะไร | ไฟล์ที่ต้องใช้ | CurseForge File ID |
| --- | --- | --- |
| เล่นและใช้ Avatar บน Fabric | \`shyne-creator-fabric-${version}.jar\` พร้อม Fabric API | [9316795](https://www.curseforge.com/minecraft/mc-mods/shyne-creator/files/9316795) |
| เล่นและใช้ Avatar บน NeoForge | \`shyne-creator-neoforge-${version}.jar\` | [9316794](https://www.curseforge.com/minecraft/mc-mods/shyne-creator/files/9316794) |
| สร้าง Avatar ด้วย Blockbench | Shyne Creator Kit — **ห้ามนำไปใส่ในโฟลเดอร์ mods** | — |

## ความต้องการของระบบ

| รายการ | เวอร์ชัน |
| --- | --- |
| Minecraft | ${minecraftVersion} |
| Java | 25 ขึ้นไป |
| Fabric Loader | 0.19.3 ขึ้นไป |
| Fabric API | 0.161.0+${minecraftVersion} |
| NeoForge | 26.3.0.16-beta ขึ้นไป |

## ติดตั้งบน Fabric

1. ติดตั้ง Fabric Loader สำหรับ Minecraft ${minecraftVersion}
2. ใส่ Fabric API และไฟล์ \`shyne-creator-fabric-${version}.jar\` ลงในโฟลเดอร์ \`.minecraft/mods/\`
3. เปิดเกมแล้วตรวจว่ามีโลโก้ Shyne ในหน้าเมนูหลัก หรือเมนู \`Esc → อวตาร\`

## ติดตั้งบน NeoForge

1. ติดตั้ง NeoForge สำหรับ Minecraft ${minecraftVersion}
2. ใส่ไฟล์ \`shyne-creator-neoforge-${version}.jar\` ลงในโฟลเดอร์ \`.minecraft/mods/\`
3. เปิดเกมด้วยโปรไฟล์ NeoForge แล้วตรวจว่ามีเมนู \`Esc → อวตาร\`

## ติดตั้งเสร็จแล้วทำอะไรต่อ

1. เข้าโลก Minecraft แล้วกด \`G\` จากนั้นเลือก \`จัดการอวตาร\` หรือเปิด \`Esc → อวตาร\`
2. ใช้ลูกกลิ้งเมาส์ (Mouse Wheel) เลื่อนดูรายการ Avatar ในคลังได้อย่างลื่นไหล
3. กด \`เปิดโฟลเดอร์\` เพื่อติดตั้ง Avatar ที่มีอยู่ (รองรับไฟล์ \`.zip\` วางได้โดยตรงทันที) หรือเปิด Cloud ในการตั้งค่าเพื่อค้นหา Avatar
4. กลับมาหน้าคลังอวตาร กด \`สแกนไฟล์\` แล้วกด \`เลือกใช้\`
5. กด \`G\` เพื่อเปิดคำสั่ง ท่าทาง หรือสีหน้าที่ Avatar นั้นเตรียมไว้

ถ้ายังเห็นเฉพาะตัวละคร Minecraft พื้นฐาน แปลว่าตัวม็อดติดตั้งสำเร็จแล้วแต่ยังไม่มี Avatar ในคอลเลกชัน ไม่ใช่ข้อผิดพลาดของการติดตั้ง

เมื่อ Avatar ปรากฏในคลังแล้ว กด \`เลือกใช้\` ได้เลย ผู้เล่นไม่ต้องแก้ \`avatar.json\` หรือปรับค่าม็อดก่อนเริ่มใช้งาน

## โฟลเดอร์ Creator

Avatar และ content pack ใช้ตำแหน่งมาตรฐานเดียวกันทั้งสอง Loader:

\`\`\`text
.minecraft/shyne-mods/
├─ avatars/
└─ <content-pack>/
\`\`\`

ดาวน์โหลดรุ่นเผยแพร่จาก [CurseForge Project](https://www.curseforge.com/minecraft/mc-mods/shyne-creator) หรือดูไฟล์ทั้งหมดได้ที่ [CurseForge Files](https://www.curseforge.com/minecraft/mc-mods/shyne-creator/files) และดูซอร์สโค้ดได้ที่ [GitHub](https://github.com/seashyne/Shyne-Creator)
`

export const docs: DocItem[] = [
  { slug: 'overview', title: 'ภาพรวม Shyne Creator', shortTitle: 'ภาพรวม', description: 'ความสามารถ เวอร์ชัน โครงสร้าง และสถานะล่าสุดของม็อด', category: 'เริ่มต้น', icon: 'book', content: overview },
  { slug: 'installation', title: 'ติดตั้ง Shyne Creator', shortTitle: 'การติดตั้ง', description: `ติดตั้งบน Fabric หรือ NeoForge สำหรับ Minecraft ${minecraftVersion}`, category: 'เริ่มต้น', icon: 'download', content: installation },
  { slug: 'player-quickstart', title: 'เริ่มใช้ Shyne ใน 1 นาที', shortTitle: 'เริ่มใช้ Shyne', description: 'ติดตั้งแล้วไปต่ออย่างไร ตั้งแต่เปิดคลังจนเลือกใช้ Avatar', category: 'เริ่มต้น', icon: 'gamepad', content: playerQuickstart },

  // หมวดบทเรียน (Tutorials)
  { slug: 'tutorial-player-basics', title: 'บทเรียนที่ 1: เริ่มต้นสำหรับผู้เล่น', shortTitle: 'บทเรียน 1: ผู้เล่น', description: 'ติดตั้งม็อด, ใส่ Avatar .zip, เลื่อนดูคลัง และตั้งค่าไมค์ให้ปากขยับ', category: 'บทเรียน', icon: 'gamepad', content: tutorial01 },
  { slug: 'tutorial-blockbench-modeling', title: 'บทเรียนที่ 2: โมเดล 3D และ Animation Blockbench', shortTitle: 'บทเรียน 2: Blockbench', description: 'กระดูก Vanilla, หู/หาง/Pivot Points, Emissive Texture เรืองแสง และ Animation', category: 'บทเรียน', icon: 'wrench', content: tutorial02 },
  { slug: 'tutorial-zero-lua', title: 'บทเรียนที่ 3: สร้าง Avatar แบบ Zero-Lua', shortTitle: 'บทเรียน 3: Zero-Lua', description: 'avatar.json, Auto Animations, ปากขยับตามเสียงพูด และ Masking ซ่อนโมเดลเดิม', category: 'บทเรียน', icon: 'sparkles', content: tutorial03 },
  { slug: 'tutorial-lua-scripting', title: 'บทเรียนที่ 4: การเขียนสคริปต์ Lua ตั้งแต่เริ่มต้น', shortTitle: 'บทเรียน 4: เขียน Lua', description: 'Lifecycle Events (TICK, RENDER), สั่งขยับกระดูก, Action Wheel 8 ช่อง และเสียง', category: 'บทเรียน', icon: 'braces', content: tutorial04 },
  { slug: 'tutorial-multiplayer-pings', title: 'บทเรียนที่ 5: ระบบออนไลน์และการซิงก์ Pings RPC', shortTitle: 'บทเรียน 5: ระบบออนไลน์', description: 'ส่งคำสั่งข้ามเครือข่าย, ซิงก์สีหน้าและ Emote ให้เพื่อนทุกคนเห็นพร้อมกัน 100%', category: 'บทเรียน', icon: 'users', content: tutorial05 },
  { slug: 'tutorial-physics-and-polish', title: 'บทเรียนที่ 6: ฟิสิกส์และการตกแต่งขั้นสูง', shortTitle: 'บทเรียน 6: ฟิสิกส์ & HUD', description: 'Spring Physics สำหรับหาง/หู, ปรับแต่งแขน First-Person, Custom HUD และ Profiler', category: 'บทเรียน', icon: 'workflow', content: tutorial06 },
  { slug: 'tutorial-powers-and-skills', title: 'บทเรียนที่ 7: การสร้างพลังและสกิลต่อสู้', shortTitle: 'บทเรียน 7: พลัง & สกิล', description: 'สร้าง Gameplay Pack, สกิลกระสุนเวทมนตร์, มานา, คูลดาวน์ และ Action Wheel', category: 'บทเรียน', icon: 'sparkles', content: tutorial07 },
  { slug: 'tutorial-custom-items', title: 'บทเรียนที่ 8: สร้างไอเท็มและอาวุธ 3D', shortTitle: 'บทเรียน 8: ไอเท็ม 3D', description: 'ปั้นโมเดล 3D ใน Blockbench, ตั้งค่า Presentation ในมือ/กระเป๋า และคลิกขวาร่ายเวท', category: 'บทเรียน', icon: 'wrench', content: tutorial08 },

  // หมวด Blockbench
  { slug: 'first-avatar', title: 'สร้าง Avatar แรก', shortTitle: 'สร้าง Avatar แรก', description: 'สำหรับ Creator: จากโปรเจกต์ Blockbench ไปสู่ Avatar ที่เล่นในเกมได้', category: 'Blockbench', icon: 'sparkles', content: quickstart },
  { slug: 'blockbench-plugin', title: 'เริ่มใช้ Blockbench กับ Shyne', shortTitle: 'คู่มือ Blockbench', description: 'สำหรับมือใหม่: รู้จักหน้าจอ สร้างโมเดล ใส่ Texture ทำ Animation และ Export เข้าเกม', category: 'Blockbench', icon: 'wrench', content: blockbenchPlugin },
  { slug: 'blockbench-animation', title: 'Blockbench Animation Standard', shortTitle: 'Animation Standard', description: 'รูปแบบแอนิเมชัน Expression และค่าที่ runtime รองรับ', category: 'Blockbench', icon: 'play', content: blockbench },

  // หมวดสร้าง Avatar
  { slug: 'standard-2', title: 'Shyne Avatar Standard', shortTitle: 'Shyne Standard', description: 'สัญญา Model-first, profile และ declarative behavior มาตรฐานเดียวทั้งหมด', category: 'สร้าง Avatar', icon: 'box', content: standard },
  { slug: 'avatar-system', title: 'ระบบ Avatar', shortTitle: 'ระบบ Avatar', description: 'ตำแหน่งไฟล์ outfit, palette, client API และขอบเขตความปลอดภัย', category: 'สร้าง Avatar', icon: 'layers', content: avatarSystem },
  { slug: 'rig-api', title: 'Native Rig API 1.3', shortTitle: 'Rig & Physics', description: 'Spring, chain, collision, IK, cosmetic armor และ Custom Avatar', category: 'สร้าง Avatar', icon: 'workflow', content: rig, api: true },
  { slug: 'script-recipes', title: 'รวมตัวอย่างสคริปต์ (Script Recipes)', shortTitle: 'ตัวอย่างสคริปต์', description: 'สูตรโค้ด Lua สำหรับ Creator: ขยับปาก, Action Wheel, Pings ซิงก์ออนไลน์, HUD และปุ่มกด', category: 'สร้าง Avatar', icon: 'braces', content: scriptRecipes, api: true },

  // หมวด API และระบบ
  { slug: 'api-contracts', title: 'API Contracts', shortTitle: 'API Contracts', description: 'ดัชนี contract, schema และ trust boundary ของ Creator API ทุกชุด', category: 'API และระบบ', icon: 'book', content: apiContracts, api: true },
  { slug: 'lua-api', title: 'Shyne Native Lua API Standard', shortTitle: 'Lua API Standard', description: 'API หลักสำหรับโมเดล state, network, event, sound และ input', category: 'API และระบบ', icon: 'braces', content: lua, api: true },
  { slug: 'render-api', title: 'Custom Render API 1.4', shortTitle: 'Render API 1.4', description: 'Primitive, HUD, world task, Script Canvas และ native bone binding', category: 'API และระบบ', icon: 'palette', content: render, api: true },
  { slug: 'gameplay-api', title: 'Shyne Gameplay API', shortTitle: 'Gameplay API', description: 'สร้าง item, skill, power และ combat system ฝั่งเซิร์ฟเวอร์', category: 'API และระบบ', icon: 'gamepad', content: gameplay, api: true },
  { slug: 'asset-package', title: 'Power & Asset Package 1.0', shortTitle: 'Asset Package', description: 'แพ็ก skill/item และ PNG icon สำหรับ native UI โดยไม่มี WebView', category: 'API และระบบ', icon: 'box', content: powerAssets, api: true },
  { slug: 'api-reference', title: 'API Reference ฉบับสมบูรณ์', shortTitle: 'API Reference', description: 'รวม Native Lua, Render, Rig, Gameplay, Cloud และ Figura compatibility', category: 'API และระบบ', icon: 'braces', content: completeApi, api: true },
  { slug: 'globals-reference', title: 'คู่มืออ้างอิง Globals ทั้งหมด', shortTitle: 'Globals Reference', description: 'รวมตัวแปรและออบเจ็กต์ส่วนกลาง (_G) ทั้งหมดของ Figura และ Shyne', category: 'API และระบบ', icon: 'braces', content: globalsReference, api: true },
  { slug: 'cloud', title: 'Shyne Avatar Cloud', shortTitle: 'Avatar Cloud', description: 'Private backup, restore, public share และสถาปัตยกรรม Cloud', category: 'API และระบบ', icon: 'cloud', content: cloud },
  { slug: 'cloud-api', title: 'Avatar Cloud API v2.2', shortTitle: 'Cloud API v2.2', description: 'Authentication, private backup และ Public ZIP endpoints', category: 'API และระบบ', icon: 'cloud', content: cloudApi, api: true },

  // หมวดเผยแพร่และพัฒนา
  { slug: 'security', title: 'Security Policy', shortTitle: 'ความปลอดภัย', description: 'Trust model, permission และวิธีรายงานช่องโหว่', category: 'เผยแพร่และพัฒนา', icon: 'shield', content: security },
  { slug: 'public-share', title: 'Shyne Public ZIP Share', shortTitle: 'Public ZIP Share', description: 'ZIP policy, package hash, permission และขอบเขตการ Revoke', category: 'เผยแพร่และพัฒนา', icon: 'shield', content: publicShare, api: true },
  { slug: 'multiplayer-testing', title: 'Multiplayer Test Matrix', shortTitle: 'ทดสอบ Multiplayer', description: 'รายการตรวจ release และหลักฐานที่ต้องเก็บก่อนเผยแพร่', category: 'เผยแพร่และพัฒนา', icon: 'users', content: multiplayer },
  { slug: 'creator-sdk', title: 'Shyne Creator SDK', shortTitle: 'Creator SDK', description: 'โครงม็อด Gameplay, custom item, Avatar และข้อมูลที่ sync', category: 'เผยแพร่และพัฒนา', icon: 'wrench', content: sdk, api: true },
  { slug: 'architecture', title: 'Shyne Creator Architecture', shortTitle: 'สถาปัตยกรรม', description: 'ขอบเขต common, Fabric, NeoForge และลำดับ renderer', category: 'เผยแพร่และพัฒนา', icon: 'workflow', content: architecture },
  { slug: 'architecture-report', title: 'แผนผังสถาปัตยกรรมและการทำงาน (Architecture Report)', shortTitle: 'แผนผังสถาปัตยกรรม', description: 'รายงานโครงสร้างการทำงานทุกระบบ แผนภาพไดอะแกรม และความปลอดภัยของ Shyne Creator', category: 'เผยแพร่และพัฒนา', icon: 'workflow', content: architectureReport },
  { slug: 'compatibility-matrix', title: 'API Compatibility Matrix', shortTitle: 'Compatibility Matrix', description: 'ระดับการรองรับ API ของ Shyne Creator เทียบกับ Figura และมาตรฐาน Shyne 2.0', category: 'เผยแพร่และพัฒนา', icon: 'workflow', content: compatMatrix, api: true },
  { slug: 'license', title: 'สัญญาอนุญาตและลิขสิทธิ์ (License & Terms)', shortTitle: 'สัญญาอนุญาต', description: 'สัญญาอนุญาต MPL-2.0, ข้อกำหนดเครื่องหมายการค้า และสิทธิ์ของครีเอเตอร์', category: 'เผยแพร่และพัฒนา', icon: 'shield', content: licenseDoc },
  { slug: 'changelog', title: 'ประวัติการอัปเดต (Changelog)', shortTitle: 'ประวัติการอัปเดต', description: 'รายละเอียดฟีเจอร์และการปรับปรุงในแต่ละเวอร์ชันของ Shyne Creator', category: 'เริ่มต้น', icon: 'book', content: changelog },
]

export const exampleDocs = [
  { title: 'Zero-Lua Avatar', description: 'Avatar แบบ Model-first ที่ใช้ Shyne Standard และ Auto Animation โดยไม่ต้องมี script', permission: 'ไม่ต้องใช้ Lua', content: zeroLuaExample },
  { title: 'Lua API 2.0', description: 'Vector, event, scheduled task และการตรวจ permission ก่อนวาด HUD', permission: 'hud_render (optional)', content: luaTwoExample },
  { title: 'Action Wheel Showcase', description: 'วงล้อ Action Wheel 8 ช่อง, เล่นเสียง .ogg, แขนมุมมองบุคคลที่หนึ่ง และ Bone Physics', permission: 'sound, particle, hud_render, camera', content: actionWheelExample },
  { title: 'Advanced Custom Render', description: 'Rect, outline, polyline, render group, responsive HUD และ world-anchored task', permission: 'hud_render + world_render', content: advancedExample, image: 'integration-avatar.png' },
  { title: 'Responsive HUD', description: 'HUD ที่จัดตำแหน่งตามความกว้างหน้าจอและ GUI scale', permission: 'hud_render', content: hudExample },
  { title: 'Render Profiler', description: 'ตัวอย่างตรวจงบ render และวิเคราะห์ task ของ Avatar', permission: 'profiler', content: profilerExample },
  { title: 'Script Canvas UI', description: 'หน้า UI เต็มจอที่วาดและกำหนดปุ่มคลิกเองทั้งหมดด้วย Lua', permission: 'hud_render', content: scriptCanvasExample },
  { title: 'Aether Power & Item Showcase', description: 'พลังที่ใช้ mana, ไอเท็มคลิกขวา, PNG icon และ Lua effect ฝั่ง server', permission: 'server gameplay pack', content: aetherShowcaseExample },
]

export const fileToSlug: Record<string, string> = {
  'README.md': 'overview', 'PLAYER_QUICKSTART_TH.md': 'player-quickstart', 'CREATOR_QUICKSTART_TH.md': 'first-avatar', 'SHYNE_STANDARD_2_TH.md': 'standard-2',
  'API_CONTRACTS_TH.md': 'api-contracts',
  'tools/blockbench/README_TH.md': 'blockbench-plugin',
  'AVATAR_SYSTEM.md': 'avatar-system', 'BLOCKBENCH_ANIMATION_STANDARD.md': 'blockbench-animation', 'RIG_API_TH.md': 'rig-api',
  'SHYNE_LUA_API_TH.md': 'lua-api', 'CUSTOM_RENDER_API_TH.md': 'render-api', 'SHYNE_GAMEPLAY_API_TH.md': 'gameplay-api', 'POWER_ASSET_PACKAGE_TH.md': 'asset-package', 'SHYNE_API_COMPLETE_TH.md': 'api-reference',
  'AVATAR_CLOUD.md': 'cloud', 'CLOUD_API.md': 'cloud-api', 'SECURITY.md': 'security', 'PUBLIC_SHARE.md': 'public-share',
  'MULTIPLAYER_TESTING.md': 'multiplayer-testing', 'CREATOR_SDK_TH.md': 'creator-sdk', 'ARCHITECTURE_TH.md': 'architecture',
  'ARCHITECTURE_REPORT_TH.md': 'architecture-report', 'LICENSE_TH.md': 'license',
  'API_COMPATIBILITY_MATRIX.md': 'compatibility-matrix', 'CHANGELOG.md': 'changelog', 'SCRIPT_RECIPES_TH.md': 'script-recipes',
  '01_PLAYER_BASICS_TH.md': 'tutorial-player-basics',
  '02_BLOCKBENCH_MODELING_TH.md': 'tutorial-blockbench-modeling',
  '03_ZERO_LUA_AVATAR_TH.md': 'tutorial-zero-lua',
  '04_LUA_SCRIPTING_TH.md': 'tutorial-lua-scripting',
  '05_MULTIPLAYER_AND_PINGS_TH.md': 'tutorial-multiplayer-pings',
  '06_PHYSICS_AND_POLISH_TH.md': 'tutorial-physics-and-polish',
  '07_POWERS_AND_SKILLS_TH.md': 'tutorial-powers-and-skills',
  '08_CUSTOM_ITEMS_AND_3D_MODELS_TH.md': 'tutorial-custom-items',
  'GLOBALS_REFERENCE_TH.md': 'globals-reference',
}
