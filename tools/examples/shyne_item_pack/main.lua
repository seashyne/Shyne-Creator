function on_player_join(ctx)
  item.give("aether_crystal", 1, ctx)
end

function on_item_use(ctx)
  if ctx.item_id ~= "aether_crystal" then return end
  player.say("The Aether Crystal answers your call.", ctx)
  fx.sound("minecraft:block.amethyst_block.chime", ctx, "players")
end
