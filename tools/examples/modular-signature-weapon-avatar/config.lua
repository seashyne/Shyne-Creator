-- ==============================================================================
-- Modular Showcase: Configuration Module (config.lua)
-- ==============================================================================

local Config = {
  mana = {
    max = 100,
    regen_base = 0.5,     -- ปกติฟื้นฟู 10 MP/วินาที
    regen_sprint = 0.0,   -- ขณะวิ่งเร็วจะไม่ฟื้นฟูมานา
    regen_meditate = 3.0, -- ขณะทำสมาธิ ฟื้นฟู 60 MP/วินาที
  },
  skills = {
    super_jump = {
      cost = 15,
      power = 1.1,        -- ความสูงที่พุ่งขึ้นไป
      cooldown = 20,      -- 1 วินาที
    },
    flight_dash = {
      cost = 25,
      speed = 1.6,        -- ความเร็วพุ่งบินตามแนวสายตา
      cooldown = 40,      -- 2 วินาที
    },
    hover = {
      cost_per_tick = 0.4,-- ใช้มานาขณะลอยตัวเคว้งกลางอากาศ
    },
    blade_beam = {
      cost = 30,
      cooldown = 50,      -- 2.5 วินาที
    }
  },
  colors = {
    mana_bar = 0xFF00E1FF,
    stamina_bar = 0xFFFFBB00,
    health_bar = 0xFFFF3355,
    speed_text = 0xFF55FFFF,
    debug_tag = 0xFFFFAA00
  },
  debug = {
    infinite_mana = false,
    show_metrics = true
  }
}

return Config
