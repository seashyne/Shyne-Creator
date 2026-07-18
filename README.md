# Shyne Creator Multi-loader

โปรเจกต์หลักสำหรับ Build Shyne Creator บน Minecraft 26.2 ทั้ง Fabric และ NeoForge จาก source ชุดเดียว

## Version

- Shyne Creator: `2.7.24-alpha-performance-26.2`
- Minecraft: `26.2`
- Java: `25`
- Fabric Loader: `0.19.3+`
- Fabric API: `0.155.2+26.2`
- NeoForge: `26.2.0.25-beta+`
- Gradle: `9.5.1`

Fabric และ NeoForge ใช้ `mod id`, Shyne API standard และ network protocol version เดียวกัน ส่วนไฟล์ที่ผูกกับ Loader จะถูกแยกออกจากระบบหลัก

## Structure

```text
common/
└─ src/
   ├─ main/java/       # Avatar, Lua API, model, Cloud, security และ gameplay
   ├─ main/resources/  # assets, schemas, Lua libraries และ mixin configs
   └─ test/java/       # unit tests ที่ใช้ร่วมกัน

fabric/
└─ src/main/
   ├─ java/            # Fabric entrypoints/events/network/render adapters
   └─ resources/       # fabric.mod.json

neoforge/
└─ src/main/
   ├─ java/            # NeoForge entrypoints/events/network/registry/render adapters
   └─ templates/       # META-INF/neoforge.mods.toml
```

โค้ดใน `common` ต้องไม่ import Fabric หรือ NeoForge โดยตรง หากต้องเชื่อม Loader ให้สร้าง adapter ชื่อเดียวกันใน `fabric` และ `neoforge`

## Build

Build และตรวจทั้งสอง Loader:

```powershell
.\gradlew.bat buildAll
```

รวบรวมเฉพาะ release JAR ไว้ใน `build/releases`:

```powershell
.\gradlew.bat collectReleaseJars
```

Build แยก Loader:

```powershell
.\gradlew.bat :fabric:build
.\gradlew.bat :neoforge:build
```

ไฟล์ที่ได้:

```text
fabric/build/libs/shyne-creator-fabric-<version>.jar
neoforge/build/libs/shyne-creator-neoforge-<version>.jar
```

อย่าใส่ JAR ทั้งสองตัวใน Minecraft instance เดียว ให้เลือกไฟล์ที่ตรงกับ Loader

## Development runtime

```powershell
.\gradlew.bat :fabric:runClient
.\gradlew.bat :fabric:runServer
.\gradlew.bat :neoforge:runClient
.\gradlew.bat :neoforge:runServer
```

Avatar และ gameplay packs ยังใช้โฟลเดอร์มาตรฐานเดียวกัน:

```text
.minecraft/shyne-mods/
├─ avatars/
└─ <content-pack>/
```

Core JAR ไม่บรรจุ Avatar, model หรือ gameplay pack ตัวอย่าง ดูรูปแบบไฟล์ใน `AVATAR_SYSTEM.md`, Lua API ใน `SHYNE_LUA_API_TH.md` และการทดสอบหลายผู้เล่นใน `MULTIPLAYER_TESTING.md`

## License

Copyright (C) 2026 seashyne

Shyne Creator เผยแพร่ภายใต้ `GNU General Public License v3.0 only` (`GPL-3.0-only`) ดูข้อความฉบับเต็มใน `LICENSE`
