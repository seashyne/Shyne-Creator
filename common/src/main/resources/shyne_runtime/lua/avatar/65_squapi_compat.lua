-- ==============================================================================
-- Shyne Avatar Runtime: SquAPI 100% Compatibility Layer (avatar/65_squapi_compat.lua)
-- Provides full compatibility for Figura avatars using SquAPI on Shyne Core.
-- Coordinates squapi_math, squapi_springs, squapi_locomotion, and squapi_features.
-- ==============================================================================

local function try_load(path)
  local ok, mod = pcall(require, path)
  if ok and type(mod) == "table" then return mod end
  return nil
end

local squapi_math = try_load("compat.squapi.squapi_math") or try_load("squapi_math")
local squapi_springs = try_load("compat.squapi.squapi_springs") or try_load("squapi_springs")
local squapi_locomotion = try_load("compat.squapi.squapi_locomotion") or try_load("squapi_locomotion")
local squapi_features = try_load("compat.squapi.squapi_features") or try_load("squapi_features")
local squapi_core = try_load("compat.squapi.squapi_core") or try_load("squapi_core")

if squapi_core and squapi_math then
  squapi = squapi_core.create(squapi_math, squapi_springs, squapi_locomotion, squapi_features)
  SquAPI = squapi
end
