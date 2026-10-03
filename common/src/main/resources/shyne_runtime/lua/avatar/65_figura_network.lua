-- Shyne Avatar Network Channel API (network, net, server_packets)
-- Managed Shyne Packet Channel with permission gating and typed payloads
-- ระบบ Network Channel ที่ผ่านการอนุมัติสิทธิ์และมี payload ปลอดภัย

---@class ShyneNetwork
network = network or {}

local _network_listeners = {}

---Sends a typed JSON payload over an authorized Shyne network channel.
---ส่งข้อมูล JSON ผ่าน channel ที่ได้รับอนุญาต
---@param channel string The channel identifier in 'namespace:name' format
---@param data any The data table or primitive to send
---@return boolean success Whether the packet was sent successfully
function network.send(channel, data)
  if type(channel) ~= "string" or channel == "" then return false end
  local json_str = "{}"
  if type(data) == "string" then
    json_str = data
  elseif _avatar_json_encode then
    local ok, encoded = pcall(_avatar_json_encode, data)
    if ok and encoded then json_str = encoded end
  else
    json_str = tostring(data or "{}")
  end
  return _avatar_net_send(channel, json_str) == true
end

---Registers a callback for packets received on a specific channel.
---ลงทะเบียน callback เมื่อได้รับ packet ใน channel นั้น
---@param channel string The channel identifier
---@param callback fun(data: any, sender: string)
function network.on(channel, callback)
  if type(channel) ~= "string" or type(callback) ~= "function" then return end
  local list = _network_listeners[channel]
  if not list then
    list = {}
    _network_listeners[channel] = list
  end
  table.insert(list, callback)
end

---Alias for network.on
network.listen = network.on

---Checks if a specific channel is authorized by the client/server policy.
---ตรวจสอบว่า channel นี้ได้รับอนุญาตหรือไม่
---@param channel string The channel identifier
---@return boolean allowed
function network.is_allowed(channel)
  if type(channel) ~= "string" then return false end
  return _avatar_net_is_allowed(channel) == true
end

---Registers a custom channel as locally allowed for this avatar session.
---ลงทะเบียน channel เองให้ได้รับการอนุญาตใน session นี้
---@param channel string The channel identifier
---@return boolean
function network.allow_channel(channel)
  if type(channel) ~= "string" then return false end
  return _avatar_net_allow_channel(channel) == true
end

---Checks whether the avatar is connected to a multiplayer server.
---ตรวจสอบว่ากำลังเชื่อมต่อกับ server multiplayer หรือไม่
---@return boolean
function network.is_connected()
  return _avatar_net_connected() == true
end

-- ------------------------------------------------------------------------------
-- Figura Compatibility Aliases (net, server_packets)
-- ------------------------------------------------------------------------------

---@class FiguraNet
net = net or {}
net.send = network.send
net.is_allowed = network.is_allowed
net.allow_channel = network.allow_channel

---@class FiguraServerPackets
server_packets = server_packets or {}
server_packets.send = network.send

-- ------------------------------------------------------------------------------
-- Event Receiver
-- ------------------------------------------------------------------------------

events.on("channel_packet", function(payload)
  if type(payload) ~= "table" or type(payload.channel) ~= "string" then return end
  local listeners = _network_listeners[payload.channel]
  if not listeners or #listeners == 0 then return end

  local data = payload.data
  if type(data) == "string" and _avatar_json_decode then
    local ok, parsed = pcall(_avatar_json_decode, data)
    if ok and parsed ~= nil then
      data = parsed
    end
  end

  for _, cb in ipairs(listeners) do
    pcall(cb, data, payload.sender or "")
  end
end)
