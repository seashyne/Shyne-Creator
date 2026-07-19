-- Deep: ตัวอย่าง Shyne Lua API Standard 1.1
-- script.lua คุมเฉพาะสิ่งที่สั่ง ส่วนที่ไม่ได้สั่งจะใช้ animation ใน bbmodel หรือ Auto Humanoid
local mouth = model.root.Head.mouth_open
local manual_open = false
local voice_open = false

local function update_mouth()
  mouth:visible(manual_open or voice_open)
end

avatar.hide_vanilla(true)
avatar.camera.configure({
  first_person_masking = true,
  hide_head = true,
  local_only = true
})
avatar.texture.sync("manifest")
avatar.network.online(true)
update_mouth()

events.on("microphone", function(mic)
  voice_open = mic.speaking and not mic.muted
  update_mouth()
end)

ui.action({
  id = "deep_toggle_mouth",
  title = "เปิด/ปิดปาก",
  description = "ตัวอย่าง Action ด้วย Shyne Lua API",
  icon = "spark",
  local_only = false,
  close = false,
  on_use = function()
    manual_open = not manual_open
    update_mouth()
  end
})

events.on("avatar_unload", function()
  avatar.hide_vanilla(false)
end)
