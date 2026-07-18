local _shyne = {
  command_handlers = {},
  key_handlers = {},
  event_handlers = {
    server_start = {},
    server_stop = {},
    player_join = {},
    player_leave = {},
    chat = {},
    tick = {},
    skill_key = {}
  },
  skills = {},
  models = {}
}

local function merge_into(dst, src)
  if type(src) ~= "table" then return end
  for k, v in pairs(src) do dst[k] = v end
end

local function current_ctx(ctx)
  return ctx or _G.ctx or {}
end

local function current_player(ctx)
  local c = current_ctx(ctx)
  return c.player or c.entity or c.owner
end

local function resolve_action(spec, ctx)
  if spec == nil then return end
  if type(spec) == "function" then
    return spec(current_ctx(ctx))
  end
  if type(spec) == "string" then
    return cast(spec, current_player(ctx))
  end
  if type(spec) ~= "table" then return end

  local kind = spec.type or spec.kind or spec.action
  if kind == "cast" then
    return cast(spec.skill, spec.player or spec.entity or current_player(ctx))
  elseif kind == "aura" then
    return aura(spec.model, spec)
  elseif kind == "projectile" then
    local payload = {}
    merge_into(payload, spec)
    payload.owner = payload.owner or current_player(ctx)
    return projectile.launch(payload)
  elseif kind == "summon" then
    local payload = {}
    merge_into(payload, spec)
    payload.owner = payload.owner or current_player(ctx)
    return familiar.spawn(payload)
  elseif kind == "command" then
    return cmd(spec.command)
  elseif kind == "message" then
    return say(spec.text or spec.message, spec.player or current_player(ctx))
  end
end

function models(list)
  _shyne.models = list or {}
end

function commands(tbl)
  merge_into(_shyne.command_handlers, tbl or {})
end

function keys(tbl)
  merge_into(_shyne.key_handlers, tbl or {})
end

function skill(id, def)
  _shyne.skills[id] = def or {}
end

function on(event_name, fn)
  local list = _shyne.event_handlers[event_name]
  if list then table.insert(list, fn) end
end

local function dispatch(event_name, ctx)
  local list = _shyne.event_handlers[event_name]
  if not list then return end
  for _, fn in ipairs(list) do
    local ok, err = pcall(fn, current_ctx(ctx))
    if not ok then log_error("lua event '" .. tostring(event_name) .. "' failed: " .. tostring(err)) end
  end
end

function say(text, player)
  local target = player or current_player()
  if target then send_message({ text = text, player = target }) else broadcast(text) end
end

function cmd(command)
  run_command({ command = command })
end

function wait_ticks(delay, hook)
  schedule({ hook = hook or "on_tick", delay_ticks = delay or 1 })
end

function aura(model, opts)
  opts = opts or {}
  local entity = opts.entity or opts.player or current_player(opts)
  local payload = {
    entity = entity,
    model = model or opts.model,
    offset_x = opts.x or opts.offset_x or 0.0,
    offset_y = opts.y or opts.offset_y or 1.0,
    offset_z = opts.z or opts.offset_z or 0.0,
    scale = opts.scale or 1.0,
    anchor_bone = opts.anchor_bone or ""
  }
  attach_model(payload)
  local animation = opts.animation or opts.anim
  if animation then
    play_animation_entity({ entity = entity, model = payload.model, animation = animation })
  end
  local sound = opts.sound
  if sound then
    play_sound({ player = entity, sound = sound, source = opts.source or "master" })
  end
end

function unaura(entity)
  local target = entity or current_player()
  stop_animation_entity({ entity = target })
  detach_model({ entity = target })
end

projectile = {}
function projectile.launch(opts)
  opts = opts or {}
  opts.owner = opts.owner or current_player(opts)
  opts.entity_type = opts.entity_type or "minecraft:armor_stand"
  opts.speed = opts.speed or 1.0
  opts.damage = opts.damage or 4.0
  opts.hitbox_radius = opts.hitbox_radius or 1.0
  opts.duration_ticks = opts.duration_ticks or 40
  opts.scale = opts.scale or 1.0
  opts.tag = opts.tag or "projectile"
  opts.homing = opts.homing == true
  opts.homing_strength = opts.homing_strength or 0.0
  opts.pierce = opts.pierce or 0
  opts.explode_radius = opts.explode_radius or 0.0
  launch_projectile(opts)
end
function projectile.skill(opts)
  return function(ctx)
    local payload = {}
    merge_into(payload, opts or {})
    payload.owner = payload.owner or current_player(ctx)
    projectile.launch(payload)
  end
end

familiar = {}
function familiar.spawn(opts)
  opts = opts or {}
  opts.owner = opts.owner or current_player(opts)
  opts.entity_type = opts.entity_type or "minecraft:armor_stand"
  opts.duration_ticks = opts.duration_ticks or opts.duration or 120
  opts.offset_x = opts.offset_x or opts.x or 0.0
  opts.offset_y = opts.offset_y or opts.y or 1.2
  opts.offset_z = opts.offset_z or opts.z or 0.0
  opts.scale = opts.scale or 1.0
  opts.tag = opts.tag or "familiar"
  summon_entity(opts)
end
function familiar.clear(owner)
  despawn_owner_summons({ owner = owner or current_player() })
end

player = {}
function player.id(ctx) return current_player(ctx) end
function player.say(text, ctx) say(text, current_player(ctx)) end
function player.mana(amount, max_mana, ctx)
  set_mana({ entity = current_player(ctx), mana = amount, max_mana = max_mana })
end
function player.team(name, ctx)
  set_team({ entity = current_player(ctx), team = name })
end
function player.xp(amount, ctx)
  grant_xp({ entity = current_player(ctx), amount = amount })
end

skills = {}
function skills.cast(id, ctx)
  local target = type(ctx) == "string" and ctx or current_player(ctx)
  local def = _shyne.skills[id]
  if def and def.cast then
    return def.cast(current_ctx(type(ctx) == "table" and ctx or { player = target }))
  end
  cast_skill({ entity = target, skill = id })
end
function skills.unlock(id, ctx)
  unlock_skill({ entity = current_player(ctx), skill = id })
end
function skills.equip(slot, id, ctx)
  equip_skill({ entity = current_player(ctx), slot = slot, skill = id })
end

weapon = {}
function weapon.equip(slot, id, ctx)
  equip_weapon({ entity = current_player(ctx), slot = slot, weapon = id })
end

item = {}
function item.give(id, count, ctx)
  give_shyne_item({ player = current_player(ctx), item = id, count = count or 1 })
end

function cast(id, ctx)
  return skills.cast(id, ctx)
end

fx = {
  aura = aura,
  attach = aura,
  clear = unaura,
  sound = function(sound, player_or_ctx, source)
    local target = type(player_or_ctx) == "table" and current_player(player_or_ctx) or player_or_ctx or current_player()
    play_sound({ player = target, sound = sound, source = source or "master" })
  end
}

presets = {}
presets.fireball = function(opts)
  return projectile.skill(opts)
end
presets.shield = function(opts)
  return function(ctx)
    local payload = { animation = opts.animation or "pulse" }
    merge_into(payload, opts or {})
    payload.entity = payload.entity or current_player(ctx)
    aura(opts.model, payload)
  end
end
presets.familiar = function(opts)
  return function(ctx)
    local payload = {}
    merge_into(payload, opts or {})
    payload.owner = payload.owner or current_player(ctx)
    familiar.spawn(payload)
  end
end

function on_server_start(ctx)
  for _, path in ipairs(_shyne.models) do load_bbmodel({ path = path }) end
  dispatch("server_start", ctx)
end

function on_server_stop(ctx) dispatch("server_stop", ctx) end
function on_player_join(ctx) dispatch("player_join", ctx) end
function on_player_leave(ctx) dispatch("player_leave", ctx) end
function on_tick(ctx) dispatch("tick", ctx) end

function on_chat(ctx)
  local handler = _shyne.command_handlers[current_ctx(ctx).message]
  if handler ~= nil then resolve_action(handler, ctx) end
  dispatch("chat", ctx)
end

function on_skill_key(ctx)
  local data = current_ctx(ctx)
  local key = data.skill or data.skill_key or data.key
  local handler = _shyne.key_handlers[key]
  if handler ~= nil then resolve_action(handler, ctx) end
  dispatch("skill_key", ctx)
end


pet = familiar

-- Shyne Lua API Standard 1.0 (trusted gameplay/server runtime).
-- Uses the same top-level names as Avatar Lua where the concepts overlap.
events = {}
function events.on(name, callback) return on(name, callback) end

minecraft = {
  player = player,
  world = {},
  item = {},
  sound = {},
  task = {}
}
function minecraft.command(command) return cmd(command) end
function minecraft.message(text, target) return say(text, target) end
function minecraft.world.set_block(x, y, z, block)
  return set_block({ x = x, y = y, z = z, block = block })
end
function minecraft.item.give(id, count, target)
  return give_item({ player = target or current_player(), item = id, count = count or 1 })
end
function minecraft.item.give_shyne(id, count, target)
  return give_shyne_item({ player = target or current_player(), item = id, count = count or 1 })
end
function minecraft.sound.play(id, target, source)
  return play_sound({ player = target or current_player(), sound = id, source = source or "master" })
end
function minecraft.task.after(ticks, hook) return wait_ticks(ticks, hook) end

model = {}
function model.load(path) return load_bbmodel({ path = path }) end
function model.attach(path, options) return aura(path, options or {}) end
function model.detach(target) return unaura(target) end
function model.play(path, animation, target)
  return play_animation_entity({ entity = target or current_player(), model = path, animation = animation })
end
function model.stop(target) return stop_animation_entity({ entity = target or current_player() }) end
