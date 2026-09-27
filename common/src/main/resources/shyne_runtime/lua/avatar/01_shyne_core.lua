-- ==============================================================================
-- Shyne Avatar Runtime: Engine Metadata & State (avatar/01_shyne_core.lua)
-- Manages API capability negotiation, result wrapping, permissions, and persistent/synced state.
-- ==============================================================================

local function bool(value) return value and true or false end

-- ------------------------------------------------------------------------------
-- API CAPABILITY NEGOTIATION
-- ------------------------------------------------------------------------------
shyne = { api = {}, result = {}, permissions = {} }
shyne.api.version = SHYNE_API_VERSION or "2.0"
shyne.api.automatic = SHYNE_API_AUTOMATIC and true or false
shyne.api.modules = _shyne_api_modules()

--- Checks whether the current Shyne Core version satisfies a module requirement.
---@param module string Target module name (e.g. "render", "vector", "rig")
---@param requirement string Semantic version constraint (e.g. ">=1.3", "^1.0", "*")
---@return boolean
function shyne.api.supports(module, requirement)
  return _shyne_api_supports(tostring(module or ""), tostring(requirement or "*"))
end

--- Requires that the host supports a specific module capability; throws error if unsupported.
---@param module string Target module name
---@param requirement string Semantic version constraint
---@return boolean
function shyne.api.require(module, requirement)
  if not shyne.api.supports(module, requirement) then
    error("unsupported Shyne API requirement: " .. tostring(module) .. " " .. tostring(requirement or "*"), 2)
  end
  return true
end

-- ------------------------------------------------------------------------------
-- RESULT WRAPPERS (Safe Execution & Error Handling)
-- ------------------------------------------------------------------------------
result = shyne.result

--- Wraps a successful value.
---@param value any
---@return table { ok = true, value = value }
function result.ok(value) return { ok = true, value = value } end

--- Wraps an error with error code, message, and optional details.
---@param code string
---@param message string
---@param details any
---@return table { ok = false, error = { code, message, details } }
function result.error(code, message, details)
  return { ok = false, error = { code = tostring(code or "runtime_error"), message = tostring(message or ""), details = details } }
end

--- Executes a function inside a protected call and returns a result table.
---@param callback function
---@return table Result object
function result.try(callback, ...)
  if type(callback) ~= "function" then return result.error("invalid_callback", "result.try requires a function") end
  local ok, value = pcall(callback, ...)
  if ok then return result.ok(value) end
  return result.error("lua_error", tostring(value))
end

-- ------------------------------------------------------------------------------
-- PERMISSIONS (Avatar Sandboxed Security)
-- ------------------------------------------------------------------------------
permissions = shyne.permissions

--- Checks if a specific permission is currently granted to this avatar.
---@param name string Permission name (e.g. "custom_sounds", "palette_actions")
---@return boolean
function permissions.has(name) return _shyne_permission_allowed(tostring(name or "")) end

--- Checks if a specific permission has been requested by this avatar.
---@param name string
---@return boolean
function permissions.requested(name) return _shyne_permission_requested(tostring(name or "")) end

--- Lists all granted permissions for this avatar.
---@return table
function permissions.list() return _shyne_permissions() end

--- Requires that a permission is granted, throwing an error otherwise.
---@param name string
---@return boolean
function permissions.require(name)
  if not permissions.has(name) then error("Shyne permission not granted: " .. tostring(name), 2) end
  return true
end

-- ------------------------------------------------------------------------------
-- STATE & STORAGE (Local & Synced Variables)
-- ------------------------------------------------------------------------------
state = {}

--- Reads a runtime state value.
---@param key string
---@param fallback any
---@return any
function state.get(key, fallback)
  local value = _avatar_state_get(key)
  if value == nil then return fallback end
  return value
end

--- Writes a runtime state value.
---@param key string
---@param value any
---@return any
function state.set(key, value) _avatar_state_set(key, value) return value end

--- Reads or writes networked synced state variables broadcasted to other players.
---@param key string
---@param value any|nil
---@return any
function state.sync(key, value)
  if value ~= nil then _avatar_synced_set(key, value) end
  return _avatar_synced_get(key)
end

--- Persistent local storage saved to avatar config directory.
storage = {}
function storage.get(key, fallback)
  local value = _avatar_local_get(tostring(key or ""))
  return value == nil and fallback or value
end
function storage.set(key, value)
  _avatar_local_set(tostring(key or ""), value)
  return value
end
function storage.delete(key) _avatar_local_set(tostring(key or ""), nil) end

-- Backward compatibility aliases
state.local_value = storage.get
state["local"] = storage.get
state.save = storage.set

function state.remote(player_id, key) return _avatar_remote_synced_get(player_id, key) end
function state.schema(path) _avatar_schema_set(path or "") end
function state.validate(key, value) return _avatar_schema_validate(key, value) end
