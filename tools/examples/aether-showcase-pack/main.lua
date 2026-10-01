-- A complete server-side gameplay showcase.  Skill JSON owns mana/cooldowns;
-- this script supplies the observable world action after the server accepts a cast.

local function launch_bolt(ctx, damage, speed, tag)
  projectile.launch({
    owner = ctx.player,
    entity_type = "minecraft:snowball",
    speed = speed,
    damage = damage,
    hitbox_radius = 0.75,
    duration_ticks = 45,
    tag = tag
  })
end

keys({
  ["aether.aether_bolt"] = function(ctx)
    launch_bolt(ctx, 5, 1.25, "aether_bolt")
    fx.sound("minecraft:entity.allay.item_given", ctx, "players")
  end,
  ["aether.aether_ward"] = function(ctx)
    fx.sound("minecraft:block.amethyst_block.chime", ctx, "players")
    player.say("Aether Ward resonates around you.", ctx)
  end,
  ["aether.aether_starfall"] = function(ctx)
    launch_bolt(ctx, 4, 1.05, "aether_starfall")
    launch_bolt(ctx, 4, 1.25, "aether_starfall")
    launch_bolt(ctx, 4, 1.45, "aether_starfall")
    fx.sound("minecraft:entity.firework_rocket.launch", ctx, "players")
  end
})

function on_item_use(ctx)
  local labels = {
    ["aether.focus"] = "Aether Focus channels Aether Bolt.",
    ["aether.wardstone"] = "Aether Wardstone channels Aether Ward.",
    ["aether.star_shard"] = "Aether Star Shard channels Aether Starfall."
  }
  local message = labels[ctx.item_id]
  if message then player.say(message, ctx) end
end
