import overview from '../../README.md?raw'
import quickstart from '../../CREATOR_QUICKSTART_TH.md?raw'
import standard from '../../SHYNE_STANDARD_2_TH.md?raw'
import blockbench from '../../BLOCKBENCH_ANIMATION_STANDARD.md?raw'
import avatarSystem from '../../AVATAR_SYSTEM.md?raw'
import rig from '../../RIG_API_TH.md?raw'
import lua from '../../SHYNE_LUA_API_TH.md?raw'
import render from '../../CUSTOM_RENDER_API_TH.md?raw'
import gameplay from '../../SHYNE_GAMEPLAY_API_TH.md?raw'
import cloud from '../../AVATAR_CLOUD.md?raw'
import cloudApi from '../../CLOUD_API.md?raw'
import secureShare from '../../SECURE_PUBLIC_SHARE.md?raw'
import security from '../../SECURITY.md?raw'
import multiplayer from '../../MULTIPLAYER_TESTING.md?raw'
import sdk from '../../CREATOR_SDK_TH.md?raw'
import architecture from '../../ARCHITECTURE_TH.md?raw'
import advancedExample from '../../tools/examples/advanced-render-avatar/README_TH.md?raw'
import hudExample from '../../tools/examples/responsive-hud-avatar/README_TH.md?raw'
import profilerExample from '../../tools/examples/render-profiler-avatar/README_TH.md?raw'

export type DocIcon = 'book' | 'download' | 'sparkles' | 'box' | 'play' | 'layers' | 'braces' | 'palette' | 'gamepad' | 'cloud' | 'shield' | 'users' | 'wrench' | 'workflow'
export type DocItem = {
  slug: string
  title: string
  shortTitle: string
  description: string
  category: 'เริ่มต้น' | 'สร้าง Avatar' | 'API และระบบ' | 'เผยแพร่และพัฒนา'
  icon: DocIcon
  content: string
  api?: boolean
}

const installation = `# ติดตั้ง Shyne Creator

Shyne Creator **2.8.0-alpha-26.2** รองรับ Minecraft **26.2** บน Fabric และ NeoForge โดยม็อดทั้งสอง Loader ใช้ Mod ID \`shyne_creator\`, Shyne API Standard และ network protocol ชุดเดียวกัน

> ไฟล์ Fabric และ NeoForge เป็นคนละไฟล์ ห้ามใส่ทั้งสองไฟล์ใน Minecraft instance เดียวกัน

## ความต้องการของระบบ

| รายการ | เวอร์ชัน |
| --- | --- |
| Minecraft | 26.2 |
| Java | 25 ขึ้นไป |
| Fabric Loader | 0.19.3 ขึ้นไป |
| Fabric API | 0.155.2+26.2 |
| NeoForge | 26.2.0.25-beta ขึ้นไป |

## ติดตั้งบน Fabric

1. ติดตั้ง Fabric Loader สำหรับ Minecraft 26.2
2. ใส่ Fabric API และไฟล์ \`shyne-creator-fabric-2.8.0-alpha-26.2.jar\` ลงในโฟลเดอร์ \`.minecraft/mods/\`
3. เปิดเกมแล้วตรวจว่าเมนู Shyne Creator ปรากฏขึ้น

## ติดตั้งบน NeoForge

1. ติดตั้ง NeoForge สำหรับ Minecraft 26.2
2. ใส่ไฟล์ \`shyne-creator-neoforge-2.8.0-alpha-26.2.jar\` ลงในโฟลเดอร์ \`.minecraft/mods/\`
3. เปิดเกมด้วยโปรไฟล์ NeoForge

## โฟลเดอร์ Creator

Avatar และ content pack ใช้ตำแหน่งมาตรฐานเดียวกันทั้งสอง Loader:

\`\`\`text
.minecraft/shyne-mods/
├─ avatars/
└─ <content-pack>/
\`\`\`

ดาวน์โหลดรุ่นเผยแพร่จาก [CurseForge](https://www.curseforge.com/minecraft/mc-mods/shyne-creator) และดูซอร์สโค้ดได้ที่ [GitHub](https://github.com/seashyne/ShyneCore)
`

export const docs: DocItem[] = [
  { slug: 'overview', title: 'ภาพรวม Shyne Creator', shortTitle: 'ภาพรวม', description: 'ความสามารถ เวอร์ชัน โครงสร้าง และสถานะล่าสุดของม็อด', category: 'เริ่มต้น', icon: 'book', content: overview },
  { slug: 'installation', title: 'ติดตั้ง Shyne Creator', shortTitle: 'การติดตั้ง', description: 'ติดตั้งบน Fabric หรือ NeoForge สำหรับ Minecraft 26.2', category: 'เริ่มต้น', icon: 'download', content: installation },
  { slug: 'first-avatar', title: 'สร้าง Avatar แรก', shortTitle: 'Avatar แรก', description: 'จากโปรเจกต์ Blockbench ไปสู่ Avatar ที่เล่นในเกมได้', category: 'เริ่มต้น', icon: 'sparkles', content: quickstart },
  { slug: 'standard-2', title: 'Shyne Avatar Standard 2.0', shortTitle: 'Standard 2.0', description: 'สัญญา Model-first, profile และ declarative behavior', category: 'สร้าง Avatar', icon: 'box', content: standard },
  { slug: 'avatar-system', title: 'ระบบ Avatar', shortTitle: 'ระบบ Avatar', description: 'ตำแหน่งไฟล์ outfit, palette, client API และขอบเขตความปลอดภัย', category: 'สร้าง Avatar', icon: 'layers', content: avatarSystem },
  { slug: 'blockbench-animation', title: 'Blockbench Animation Standard', shortTitle: 'Blockbench & Animation', description: 'รูปแบบแอนิเมชัน Expression และค่าที่ runtime รองรับ', category: 'สร้าง Avatar', icon: 'play', content: blockbench },
  { slug: 'rig-api', title: 'Native Rig API 1.3', shortTitle: 'Rig & Physics', description: 'Spring, chain, collision, IK, cosmetic armor และ Merling', category: 'สร้าง Avatar', icon: 'workflow', content: rig, api: true },
  { slug: 'lua-api', title: 'Shyne Lua API Standard 1.3', shortTitle: 'Lua API', description: 'API หลักสำหรับโมเดล state, network, event, sound และ input', category: 'API และระบบ', icon: 'braces', content: lua, api: true },
  { slug: 'render-api', title: 'Custom Render API 1.1', shortTitle: 'Render API', description: 'Primitive, HUD, world task, permission และ performance budget', category: 'API และระบบ', icon: 'palette', content: render, api: true },
  { slug: 'gameplay-api', title: 'Shyne Gameplay API', shortTitle: 'Gameplay API', description: 'สร้าง item, skill, power และ combat system ฝั่งเซิร์ฟเวอร์', category: 'API และระบบ', icon: 'gamepad', content: gameplay, api: true },
  { slug: 'cloud', title: 'Shyne Avatar Cloud', shortTitle: 'Avatar Cloud', description: 'Private backup, restore, public share และสถาปัตยกรรม Cloud', category: 'API และระบบ', icon: 'cloud', content: cloud },
  { slug: 'cloud-api', title: 'Avatar Cloud API v2.1', shortTitle: 'Cloud API v2.1', description: 'Authentication, storage, backup และ public-share endpoints', category: 'API และระบบ', icon: 'cloud', content: cloudApi, api: true },
  { slug: 'security', title: 'Security Policy', shortTitle: 'ความปลอดภัย', description: 'Trust model, permission และวิธีรายงานช่องโหว่', category: 'เผยแพร่และพัฒนา', icon: 'shield', content: security },
  { slug: 'secure-share', title: 'Secure Public Share (.sc v2)', shortTitle: 'Secure Public Share', description: 'รูปแบบแพ็กแชร์สาธารณะและ security model', category: 'เผยแพร่และพัฒนา', icon: 'shield', content: secureShare, api: true },
  { slug: 'multiplayer-testing', title: 'Multiplayer Test Matrix', shortTitle: 'ทดสอบ Multiplayer', description: 'รายการตรวจ release และหลักฐานที่ต้องเก็บก่อนเผยแพร่', category: 'เผยแพร่และพัฒนา', icon: 'users', content: multiplayer },
  { slug: 'creator-sdk', title: 'Shyne Creator SDK', shortTitle: 'Creator SDK', description: 'โครงม็อด Gameplay, custom item, Avatar และข้อมูลที่ sync', category: 'เผยแพร่และพัฒนา', icon: 'wrench', content: sdk, api: true },
  { slug: 'architecture', title: 'Shyne Creator Architecture', shortTitle: 'สถาปัตยกรรม', description: 'ขอบเขต common, Fabric, NeoForge และลำดับ renderer', category: 'เผยแพร่และพัฒนา', icon: 'workflow', content: architecture },
]

export const exampleDocs = [
  { title: 'Advanced Custom Render', description: 'Rect, outline, polyline, render group, responsive HUD และ world-anchored task', permission: 'hud_render + world_render', content: advancedExample, image: 'integration-avatar.png' },
  { title: 'Responsive HUD', description: 'HUD ที่จัดตำแหน่งตามความกว้างหน้าจอและ GUI scale', permission: 'hud_render', content: hudExample },
  { title: 'Render Profiler', description: 'ตัวอย่างตรวจงบ render และวิเคราะห์ task ของ Avatar', permission: 'profiler', content: profilerExample },
]

export const fileToSlug: Record<string, string> = {
  'README.md': 'overview', 'CREATOR_QUICKSTART_TH.md': 'first-avatar', 'SHYNE_STANDARD_2_TH.md': 'standard-2',
  'AVATAR_SYSTEM.md': 'avatar-system', 'BLOCKBENCH_ANIMATION_STANDARD.md': 'blockbench-animation', 'RIG_API_TH.md': 'rig-api',
  'SHYNE_LUA_API_TH.md': 'lua-api', 'CUSTOM_RENDER_API_TH.md': 'render-api', 'SHYNE_GAMEPLAY_API_TH.md': 'gameplay-api',
  'AVATAR_CLOUD.md': 'cloud', 'CLOUD_API.md': 'cloud-api', 'SECURITY.md': 'security', 'SECURE_PUBLIC_SHARE.md': 'secure-share',
  'MULTIPLAYER_TESTING.md': 'multiplayer-testing', 'CREATOR_SDK_TH.md': 'creator-sdk', 'ARCHITECTURE_TH.md': 'architecture',
}

export const version = '2.8.0-alpha-26.2'
