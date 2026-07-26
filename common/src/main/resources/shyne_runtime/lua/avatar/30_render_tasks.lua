-- Render options are retained in Lua so a task can be updated without rebuilding
-- every field. Only the resolved, permission-checked task crosses into Java.
local function render_copy(value)
  local result = {}
  for key, item in pairs(value or {}) do result[key] = item end
  return result
end
local function render_merge(target, patch)
  local result = render_copy(target)
  for key, value in pairs(patch or {}) do result[key] = value end
  return result
end
local function render_number(value, fallback)
  value = tonumber(value)
  if value == nil then return fallback end
  return value
end
local function render_group_options(options)
  local result = render_copy(options)
  local position = result.position or result.pos or result.from or {}
  local destination = result.to or result.destination or {}
  local x = render_number(position.x or result.x, 0)
  local y = render_number(position.y or result.y, 0)
  local z = render_number(position.z or result.z, 0)
  local x2 = render_number(destination.x or result.x2, 0)
  local y2 = render_number(destination.y or result.y2, 0)
  local z2 = render_number(destination.z or result.z2, 0)
  local width = render_number(result.width, 1)
  local height = render_number(result.height, 16)
  local scale = render_number(result.scale, 1)
  local opacity = render_number(result.opacity, 1)
  local z_index = render_number(result.z_index or result.layer, 0)
  local group_id = result.group or result.parent_group
  local visited = {}
  local depth = 0

  -- Groups are resolved from child to parent with a depth/cycle guard.
  while group_id ~= nil and render._groups[group_id] ~= nil and depth < 16 and not visited[group_id] do
    visited[group_id] = true
    depth = depth + 1
    local group = render._groups[group_id]
    local group_position = group.position or group.pos or {}
    local gx = render_number(group_position.x or group.x, 0)
    local gy = render_number(group_position.y or group.y, 0)
    local gz = render_number(group_position.z or group.z, 0)
    local group_scale = render_number(group.scale, 1)
    local sx = render_number(group.scale_x, group_scale)
    local sy = render_number(group.scale_y, group_scale)
    local sz = render_number(group.scale_z, group_scale)
    x, y, z = gx + x * sx, gy + y * sy, gz + z * sz
    x2, y2, z2 = gx + x2 * sx, gy + y2 * sy, gz + z2 * sz
    width, height = width * math.abs(sx), height * math.abs(sy)
    scale = scale * math.max(math.abs(sx), math.abs(sy))
    opacity = opacity * render_number(group.opacity, 1)
    z_index = z_index + render_number(group.z_index or group.layer, 0)
    if group.visible == false then result.visible = false end
    group_id = group.group or group.parent_group
  end

  result.position, result.pos, result.from = nil, nil, nil
  result.to, result.destination = nil, nil
  result.x, result.y, result.z = x, y, z
  result.x2, result.y2, result.z2 = x2, y2, z2
  result.width, result.height, result.scale = width, height, scale
  result.opacity, result.z_index = opacity, z_index
  return result
end
local function render_push(id, kind, options)
  local resolved = render_group_options(options)
  local attached_to = resolved.attach or resolved.bone
  local attachment_path = ""
  local local_offset = vector.new(resolved.local_offset or { x = 0, y = 0, z = 0 })
  local local_to = vector.new(resolved.local_to or { x = 0, y = 0, z = 0 })
  local has_local_to = resolved.local_to ~= nil
  if attached_to ~= nil and tostring(attached_to) ~= "" then
    if type(attached_to) == "table" and type(attached_to.path) == "string" then
      attachment_path = attached_to.path
    else
      attachment_path = model.part(tostring(attached_to)).path
    end
    local offset = vector.new(resolved.offset or { x = resolved.x or 0, y = resolved.y or 0, z = resolved.z or 0 })
    resolved.world = true
    -- Java resolves the current renderer matrix at submission time. This keeps
    -- attachments on the same frame as the bone and can inherit its rotation.
    resolved.x, resolved.y, resolved.z = offset.x, offset.y, offset.z
  end
  local billboard = resolved.billboard
  if billboard == nil then
    billboard = not (attachment_path ~= "" and (kind == "item" or kind == "block"))
  end
  local ok = _shyne_render_task(id, kind, resolved.world == true,
    resolved.text or resolved.content or "", resolved.texture or resolved.item or resolved.block or resolved.resource or "",
    resolved.x or 0, resolved.y or 0, resolved.z or 0,
    resolved.x2 or 0, resolved.y2 or 0, resolved.z2 or 0,
    resolved.width or 1, resolved.height or 16, resolved.thickness or resolved.scale or 1,
    resolved.color or 0xFFFFFFFF, resolved.shadow == true, resolved.visible ~= false,
    resolved.max_distance or 128, resolved.z_index or 0, resolved.opacity or 1,
    attachment_path, local_offset.x, local_offset.y, local_offset.z,
    local_to.x, local_to.y, local_to.z, has_local_to, billboard,
    resolved.fullbright == true or resolved.light == "fullbright")
  if ok then return id end
  return false
end
local function render_task(id, kind, options)
  local stored = render_copy(options)
  render._tasks[id] = { kind = kind, options = stored }
  local result = render_push(id, kind, stored)
  if result == false then render._tasks[id] = nil end
  return result
end
local function render_refresh_all()
  for id, task in pairs(render._tasks) do render_push(id, task.kind, task.options) end
end
function render.text(id, options) return render_task(id, "text", options) end
function render.item(id, options) return render_task(id, "item", options) end
function render.block(id, options) return render_task(id, "block", options) end
function render.sprite(id, options) return render_task(id, "sprite", options) end
function render.line(id, options) return render_task(id, "line", options) end
function render.rect(id, options) return render_task(id, "rect", options) end
function render.outline(id, options) return render_task(id, "outline", options) end
function render.world(id, options)
  local world_options = render_copy(options)
  world_options.world = true
  return render_task(id, world_options.type or world_options.kind or "text", world_options)
end
