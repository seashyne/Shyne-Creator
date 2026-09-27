# Shyne Core & Creator - Project Architecture & Coding Rules

## 1. Modularization & File Size Limits (Single Responsibility Principle)
- **Maximum File Size**: Keep files under **350-400 lines**.
  - Any Lua script or Java class exceeding this threshold, or taking on more than one distinct responsibility, MUST be decomposed into specialized submodules under a dedicated package or directory.
- **Single Responsibility Principle (SRP)**:
  - Each module must own one cohesive domain (e.g., math, spring physics, inverse kinematics, state machine, event bus, rendering tasks, compatibility bridges).
  - Never dump unrelated compatibility systems or utility functions into unrelated core modules (e.g. SquAPI compatibility must not be buried inside native rig scripts).
- **Submodule Organization**:
  - Lua modular scripts must be organized by domain under `shyne_runtime/lua/`:
    * `avatar/`: Core avatar bootstrap sequence (`00_core.lua`, `01_shyne_core.lua`, `05_events_scheduler.lua`, etc.).
    * `rig/`: Native procedural rigging submodules (`rig_math.lua`, `rig_spring.lua`, `rig_ik.lua`, `rig_animation_graph.lua`, `rig_equipment.lua`, `rig_core.lua`).
    * `compat/`: Third-party compatibility bridges (`figura`, `squapi`).
  - Java classes must follow clean package separation under `seashyne.shynecore.client.avatar.*` (`bridge`, `runtime`, `sound`, `ui`, `render`, `input`).

## 2. Comprehensive Documentation & Commenting Standard
- **Lua Scripts**:
  - Every module must have a descriptive header explaining its role, dependencies, and execution lifecycle.
  - Every public function and API method must include EmmyLua documentation annotations:
    * `---@class <ClassName>`
    * `---@param <paramName> <type> <description>`
    * `---@return <type> <description>`
  - Complex physics formulas (springs, BERP integrators, Two-Bone IK trigonometry) must include explanatory comments detailing the math and units (e.g., degrees vs radians, tick rate vs delta time).
- **Java Code**:
  - Every class, interface, record, and public bridge method must include Javadoc comments specifying:
    * Purpose and architectural responsibility.
    * Thread safety and execution context (Render thread, Client tick thread, Worker thread).
    * Coordinate systems and conversion formulas (e.g. Blockbench 1/16th scale, OpenGL/OpenAL coordinates).

## 3. MultiLoader Architecture & Parity Enforcement
- **Zero Loader-Specific Imports in Common**:
  - Code under `common/` must NEVER import Fabric (`net.fabricmc.*`) or NeoForge (`net.neoforged.*`) classes.
  - All platform-specific functionality must be abstracted via interfaces or service loaders in `common`, implemented separately in `fabric` and `neoforge`.
- **100% Loader Parity**:
  - All features present in Fabric must exist identically in NeoForge.
  - Class structures, network packet handlers, renderers, and commands must mirror each other across both loaders.
  - `./gradlew verifyLoaderParity` must ALWAYS pass with 0 errors.

## 4. Backward Compatibility & Stability
- Refactoring and modularization must NEVER break existing user avatars:
  - Blockbench `.bbmodel` animations and custom bones.
  - Shyne Lua API 1.0, 2.0, and Easy API declarations (`shyne.setup`, `anim`, `part`, `on`).
  - Figura avatars and SquAPI scripts (`require("squapi")`).
- All unit and integration tests (`:fabric:test` and `:neoforge:test`) must pass 100% before any change is marked complete.
