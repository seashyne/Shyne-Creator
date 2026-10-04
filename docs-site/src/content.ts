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
import cloud from '../../docs/architecture/AVATAR_CLOUD.md?raw'
import cloudApi from '../../docs/api/CLOUD_API.md?raw'
import publicShare from '../../docs/guides/PUBLIC_SHARE.md?raw'
import security from '../../docs/legal/SECURITY.md?raw'
import multiplayer from '../../docs/architecture/MULTIPLAYER_TESTING.md?raw'
import sdk from '../../docs/standards/CREATOR_SDK_TH.md?raw'
import architecture from '../../docs/architecture/ARCHITECTURE_TH.md?raw'
import compatMatrix from '../../docs/api/API_COMPATIBILITY_MATRIX.md?raw'
import changelog from '../../CHANGELOG.md?raw'
import zeroLuaExample from '../../tools/examples/zero-lua-avatar/README.md?raw'
import luaTwoExample from '../../tools/examples/lua-api-2.0-avatar/README.md?raw'
import advancedExample from '../../tools/examples/advanced-render-avatar/README_TH.md?raw'
import hudExample from '../../tools/examples/responsive-hud-avatar/README_TH.md?raw'
import profilerExample from '../../tools/examples/render-profiler-avatar/README_TH.md?raw'
import actionWheelExample from '../../tools/examples/action-wheel-showcase-avatar/README.md?raw'
import scriptCanvasExample from '../../tools/examples/script-canvas-ui-avatar/README_TH.md?raw'
import aetherShowcaseExample from '../../tools/examples/aether-showcase-pack/README_TH.md?raw'

export type DocIcon = 'book' | 'download' | 'sparkles' | 'box' | 'play' | 'layers' | 'braces' | 'palette' | 'gamepad' | 'cloud' | 'shield' | 'users' | 'wrench' | 'workflow'
export type DocItem = {
  slug: string
  title: string
  shortTitle: string
  description: string
  category: 'เริ่มต้น' | 'Blockbench' | 'สร้าง Avatar' | 'API และระบบ' | 'เผยแพร่และพัฒนา'
  icon: DocIcon
  content: string
  api?: boolean
}

export const version = '2.14.0'
export const minecraftVersion = '26.3'

const installation = `# ติดตั้ง Shyne Creator

Shyne Creator **${version}** รองรับ Minecraft **${minecraftVersion}** บน Fabric และ NeoForge โดยม็อดทั้งสอง Loader ใช้ Mod ID \`shyne_creator\`, Shyne API Standard และ network protocol ชุดเดียวกัน

> รุ่นนี้แก้ปัญหาเกมเด้งตอนเริ่มต้นบน Minecraft 26.3 ที่เกิดจากการเชื่อมระบบ renderer มุมมองบุคคลที่หนึ่ง

> Shyne Creator เป็นระบบสำหรับใช้ Avatar และ Content Pack ไม่ใช่ม็อดที่เพิ่ม Avatar หรือสกิลมาให้ทันทีหลังติดตั้ง หากต้องการเริ่มเล่น ให้ติดตั้งตัวม็อดแล้วอ่าน [เริ่มใช้ Shyne ใน 1 นาที](PLAYER_QUICKSTART_TH.md)

> ไฟล์ Fabric และ NeoForge เป็นคนละไฟล์ ห้ามใส่ทั้งสองไฟล์ใน Minecraft instance เดียวกัน

## เลือกไฟล์ให้ถูก

| คุณต้องการทำอะไร | ไฟล์ที่ต้องใช้ |
| --- | --- |
| เล่นและใช้ Avatar บน Fabric | \`shyne-creator-fabric-2.14.0.jar\` พร้อม Fabric API |
| เล่นและใช้ Avatar บน NeoForge | \`shyne-creator-neoforge-2.14.0.jar\` |
| สร้าง Avatar ด้วย Blockbench | Shyne Creator Kit — **ห้ามนำไปใส่ในโฟลเดอร์ mods** |

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
2. ใส่ Fabric API และไฟล์ \`shyne-creator-fabric-2.14.0.jar\` ลงในโฟลเดอร์ \`.minecraft/mods/\`
3. เปิดเกมแล้วตรวจว่ามีโลโก้ Shyne ในหน้าเมนูหลัก หรือเมนู \`Esc → อวตาร\`

## ติดตั้งบน NeoForge

1. ติดตั้ง NeoForge สำหรับ Minecraft ${minecraftVersion}
2. ใส่ไฟล์ \`shyne-creator-neoforge-2.14.0.jar\` ลงในโฟลเดอร์ \`.minecraft/mods/\`
3. เปิดเกมด้วยโปรไฟล์ NeoForge แล้วตรวจว่ามีเมนู \`Esc → อวตาร\`

## ติดตั้งเสร็จแล้วทำอะไรต่อ

1. เข้าโลก Minecraft แล้วกด \`G\` จากนั้นเลือก \`จัดการอวตาร\` หรือเปิด \`Esc → อวตาร\`
2. กด \`เปิดโฟลเดอร์\` เพื่อติดตั้ง Avatar ที่มีอยู่ หรือเปิด Cloud ในการตั้งค่าเพื่อค้นหา Avatar
3. กลับมาหน้าคลังอวตาร กด \`สแกนไฟล์\` แล้วกด \`เลือกใช้\`
4. กด \`G\` เพื่อเปิดคำสั่ง ท่าทาง หรือสีหน้าที่ Avatar นั้นเตรียมไว้

ถ้ายังเห็นเฉพาะตัวละคร Minecraft พื้นฐาน แปลว่าตัวม็อดติดตั้งสำเร็จแล้วแต่ยังไม่มี Avatar ในคอลเลกชัน ไม่ใช่ข้อผิดพลาดของการติดตั้ง

เมื่อ Avatar ปรากฏในคลังแล้ว กด \`เลือกใช้\` ได้เลย ผู้เล่นไม่ต้องแก้ \`avatar.json\` หรือปรับค่าม็อดก่อนเริ่มใช้งาน

## โฟลเดอร์ Creator

Avatar และ content pack ใช้ตำแหน่งมาตรฐานเดียวกันทั้งสอง Loader:

\`\`\`text
.minecraft/shyne-mods/
├─ avatars/
└─ <content-pack>/
\`\`\`

ดาวน์โหลดรุ่นเผยแพร่จาก [CurseForge](https://www.curseforge.com/minecraft/mc-mods/shyne-creator) และดูซอร์สโค้ดได้ที่ [GitHub](https://github.com/seashyne/Shyne-Creator)
`

export const docs: DocItem[] = [
  { slug: 'overview', title: 'ภาพรวม Shyne Creator', shortTitle: 'ภาพรวม', description: 'ความสามารถ เวอร์ชัน โครงสร้าง และสถานะล่าสุดของม็อด', category: 'เริ่มต้น', icon: 'book', content: overview },
  { slug: 'installation', title: 'ติดตั้ง Shyne Creator', shortTitle: 'การติดตั้ง', description: `ติดตั้งบน Fabric หรือ NeoForge สำหรับ Minecraft ${minecraftVersion}`, category: 'เริ่มต้น', icon: 'download', content: installation },
  { slug: 'player-quickstart', title: 'เริ่มใช้ Shyne ใน 1 นาที', shortTitle: 'เริ่มใช้ Shyne', description: 'ติดตั้งแล้วไปต่ออย่างไร ตั้งแต่เปิดคลังจนเลือกใช้ Avatar', category: 'เริ่มต้น', icon: 'gamepad', content: playerQuickstart },
  { slug: 'first-avatar', title: 'สร้าง Avatar แรก', shortTitle: 'สร้าง Avatar แรก', description: 'สำหรับ Creator: จากโปรเจกต์ Blockbench ไปสู่ Avatar ที่เล่นในเกมได้', category: 'Blockbench', icon: 'sparkles', content: quickstart },
  { slug: 'standard-2', title: 'Shyne Avatar Standard 2.0', shortTitle: 'Standard 2.0', description: 'สัญญา Model-first, profile และ declarative behavior', category: 'สร้าง Avatar', icon: 'box', content: standard },
  { slug: 'api-contracts', title: 'API Contracts', shortTitle: 'API Contracts', description: 'ดัชนี contract, schema และ trust boundary ของ Creator API ทุกชุด', category: 'API และระบบ', icon: 'book', content: apiContracts, api: true },
  { slug: 'blockbench-plugin', title: 'เริ่มใช้ Blockbench กับ Shyne', shortTitle: 'คู่มือ Blockbench', description: 'สำหรับมือใหม่: รู้จักหน้าจอ สร้างโมเดล ใส่ Texture ทำ Animation และ Export เข้าเกม', category: 'Blockbench', icon: 'wrench', content: blockbenchPlugin },
  { slug: 'avatar-system', title: 'ระบบ Avatar', shortTitle: 'ระบบ Avatar', description: 'ตำแหน่งไฟล์ outfit, palette, client API และขอบเขตความปลอดภัย', category: 'สร้าง Avatar', icon: 'layers', content: avatarSystem },
  { slug: 'blockbench-animation', title: 'Blockbench Animation Standard', shortTitle: 'Animation Standard', description: 'รูปแบบแอนิเมชัน Expression และค่าที่ runtime รองรับ', category: 'Blockbench', icon: 'play', content: blockbench },
  { slug: 'rig-api', title: 'Native Rig API 1.3', shortTitle: 'Rig & Physics', description: 'Spring, chain, collision, IK, cosmetic armor และ Custom Avatar', category: 'สร้าง Avatar', icon: 'workflow', content: rig, api: true },
  { slug: 'lua-api', title: 'Shyne Native Lua API — Standard 2.0', shortTitle: 'Lua API 2.0', description: 'API หลักสำหรับโมเดล state, network, event, sound และ input', category: 'API และระบบ', icon: 'braces', content: lua, api: true },
  { slug: 'render-api', title: 'Custom Render API 1.4', shortTitle: 'Render API 1.4', description: 'Primitive, HUD, world task, Script Canvas และ native bone binding', category: 'API และระบบ', icon: 'palette', content: render, api: true },
  { slug: 'gameplay-api', title: 'Shyne Gameplay API', shortTitle: 'Gameplay API', description: 'สร้าง item, skill, power และ combat system ฝั่งเซิร์ฟเวอร์', category: 'API และระบบ', icon: 'gamepad', content: gameplay, api: true },
  { slug: 'asset-package', title: 'Power & Asset Package 1.0', shortTitle: 'Asset Package', description: 'แพ็ก skill/item และ PNG icon สำหรับ native UI โดยไม่มี WebView', category: 'API และระบบ', icon: 'box', content: powerAssets, api: true },
  { slug: 'api-reference', title: 'API Reference ฉบับสมบูรณ์', shortTitle: 'API Reference', description: 'รวม Native Lua, Render, Rig, Gameplay, Cloud และ Figura compatibility', category: 'API และระบบ', icon: 'braces', content: completeApi, api: true },
  { slug: 'cloud', title: 'Shyne Avatar Cloud', shortTitle: 'Avatar Cloud', description: 'Private backup, restore, public share และสถาปัตยกรรม Cloud', category: 'API และระบบ', icon: 'cloud', content: cloud },
  { slug: 'cloud-api', title: 'Avatar Cloud API v2.2', shortTitle: 'Cloud API v2.2', description: 'Authentication, private backup และ Public ZIP endpoints', category: 'API และระบบ', icon: 'cloud', content: cloudApi, api: true },
  { slug: 'security', title: 'Security Policy', shortTitle: 'ความปลอดภัย', description: 'Trust model, permission และวิธีรายงานช่องโหว่', category: 'เผยแพร่และพัฒนา', icon: 'shield', content: security },
  { slug: 'public-share', title: 'Shyne Public ZIP Share', shortTitle: 'Public ZIP Share', description: 'ZIP policy, package hash, permission และขอบเขตการ Revoke', category: 'เผยแพร่และพัฒนา', icon: 'shield', content: publicShare, api: true },
  { slug: 'multiplayer-testing', title: 'Multiplayer Test Matrix', shortTitle: 'ทดสอบ Multiplayer', description: 'รายการตรวจ release และหลักฐานที่ต้องเก็บก่อนเผยแพร่', category: 'เผยแพร่และพัฒนา', icon: 'users', content: multiplayer },
  { slug: 'creator-sdk', title: 'Shyne Creator SDK', shortTitle: 'Creator SDK', description: 'โครงม็อด Gameplay, custom item, Avatar และข้อมูลที่ sync', category: 'เผยแพร่และพัฒนา', icon: 'wrench', content: sdk, api: true },
  { slug: 'architecture', title: 'Shyne Creator Architecture', shortTitle: 'สถาปัตยกรรม', description: 'ขอบเขต common, Fabric, NeoForge และลำดับ renderer', category: 'เผยแพร่และพัฒนา', icon: 'workflow', content: architecture },
  { slug: 'compatibility-matrix', title: 'API Compatibility Matrix', shortTitle: 'Compatibility Matrix', description: 'ระดับการรองรับ API ของ Shyne Creator เทียบกับ Figura และมาตรฐาน Shyne 2.0', category: 'เผยแพร่และพัฒนา', icon: 'workflow', content: compatMatrix, api: true },
  { slug: 'changelog', title: 'ประวัติการอัปเดต (Changelog)', shortTitle: 'ประวัติการอัปเดต', description: 'รายละเอียดฟีเจอร์และการปรับปรุงในแต่ละเวอร์ชันของ Shyne Creator', category: 'เริ่มต้น', icon: 'book', content: changelog },
]

export const exampleDocs = [
  { title: 'Zero-Lua Avatar', description: 'Avatar แบบ Model-first ที่ใช้ Standard 2.0 และ Auto Animation โดยไม่ต้องมี script', permission: 'ไม่ต้องใช้ Lua', content: zeroLuaExample },
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
  'API_COMPATIBILITY_MATRIX.md': 'compatibility-matrix', 'CHANGELOG.md': 'changelog',
}
