# Shyne Creator contribution rules

## Versioning

Every bug fix that changes player or creator behavior must increase the patch
version before it is committed and pushed. Apply the same rule to every
supported Minecraft branch that receives the fix.

Use `tools/release_build.ps1` on the branch being released. It increments the
patch number and keeps the mod metadata, runtime version, website content, kit
documentation, JAR names, and checksums in sync.

Example: `2.10.0-alpha-26.3` becomes `2.10.1-alpha-26.3`.

