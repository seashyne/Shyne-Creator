(function () {
  'use strict';

  const PLUGIN_ID = 'shyne_standard_2';
  const registeredProperties = [];
  let exportAction;
  let packageAction;
  let validateAction;

  function registerProperty(target, type, name, options) {
    if (typeof Property !== 'function' || !target) return;
    if (target.properties && target.properties[name]) return;
    registeredProperties.push(new Property(target, type, name, options));
  }

  function projectValue(name, fallback) {
    return typeof Project !== 'undefined' && Project && Project[name] !== undefined
      ? Project[name]
      : fallback;
  }

  function animationList() {
    if (typeof Animation !== 'undefined' && Array.isArray(Animation.all)) return Animation.all;
    if (typeof Animator !== 'undefined' && Array.isArray(Animator.animations)) return Animator.animations;
    return [];
  }

  function groupList() {
    return typeof Group !== 'undefined' && Array.isArray(Group.all) ? Group.all : [];
  }

  function textureList() {
    return typeof Texture !== 'undefined' && Array.isArray(Texture.all) ? Texture.all : [];
  }

  function cleanId(value) {
    const id = String(value || '')
      .trim()
      .toLowerCase()
      .replace(/[^a-z0-9_.-]+/g, '_')
      .replace(/^[._-]+|[._-]+$/g, '');
    return id || 'avatar';
  }

  function animationName(animation) {
    return String(animation && animation.name ? animation.name : '').trim();
  }

  function buildBehavior() {
    const blend = Math.max(0, Math.min(1200, Number(projectValue('shyne_blend_ticks', 5)) || 0));
    const animations = {};
    const autoplay = [];
    let blink = null;

    animationList().forEach((animation) => {
      const name = animationName(animation);
      if (!name) return;
      const state = String(animation.shyne_state || 'none');
      if (state !== 'none') animations[state] = name;
      if (animation.shyne_autoplay === true) autoplay.push(name);
      if (animation.shyne_blink === true) blink = name;
    });

    const behavior = {};
    if (blend !== 5) behavior.blend_ticks = blend;
    if (Object.keys(animations).length) behavior.animations = animations;
    if (autoplay.length) behavior.autoplay = Array.from(new Set(autoplay));
    if (blink) behavior.blink = { animation: blink, min_ticks: 50, max_ticks: 110 };
    return behavior;
  }

  function buildManifest() {
    const name = String(projectValue('shyne_avatar_name', projectValue('name', 'Shyne Avatar')) || 'Shyne Avatar').trim();
    const selectedProfile = String(projectValue('shyne_profile', 'accessory') || 'accessory').toLowerCase();
    const profile = selectedProfile === 'merling' || selectedProfile === 'aquatic' ? 'custom' : selectedProfile;
    const explicit = String(projectValue('shyne_manifest_mode', 'compact')) === 'explicit';
    const configuredId = String(projectValue('shyne_avatar_id', '') || '').trim();
    const behavior = buildBehavior();
    const manifest = explicit
      ? {
          standard: '2.0',
          id: cleanId(configuredId || name),
          name,
          profile,
          model: 'model.bbmodel',
          behavior: Object.assign({ preset: 'auto' }, behavior)
        }
      : { name };
    if (!explicit) {
      if (configuredId) manifest.id = cleanId(configuredId);
      if (profile !== 'accessory') manifest.profile = profile;
      if (Object.keys(behavior).length) manifest.behavior = behavior;
    }
    if (projectValue('shyne_use_lua', false) === true) {
      manifest.main = baseName(projectValue('shyne_main', 'script.lua') || 'script.lua');
      manifest.api = '2.0';
    }
    return manifest;
  }

  function safeFileName(value, fallback) {
    let name = String(value || fallback || 'texture.png').replace(/\\/g, '/').split('/').pop();
    name = name.replace(/[^A-Za-z0-9_.-]+/g, '_').replace(/^[._-]+/, '');
    if (!name) name = fallback || 'texture.png';
    if (!/\.png$/i.test(name)) name += '.png';
    return name;
  }

  function uniqueFileName(value, used) {
    const source = safeFileName(value, 'texture.png');
    const dot = source.toLowerCase().lastIndexOf('.png');
    const base = dot >= 0 ? source.slice(0, dot) : source;
    let candidate = source;
    let suffix = 2;
    while (used.has(candidate.toLowerCase())) candidate = `${base}_${suffix++}.png`;
    used.add(candidate.toLowerCase());
    return candidate;
  }

  function textureRole(texture) {
    return String(texture && texture.shyne_export_role ? texture.shyne_export_role : 'model');
  }

  function texturePackageInfo() {
    const used = new Set();
    return textureList().map((texture, index) => ({
      texture,
      role: textureRole(texture),
      fileName: uniqueFileName(texture && (texture.name || texture.id), used),
      index
    }));
  }

  function compilePortableModel(textureInfo) {
    if (typeof Codecs === 'undefined' || !Codecs.project || typeof Codecs.project.compile !== 'function') {
      throw new Error('Blockbench project compiler is unavailable.');
    }
    const compiled = Codecs.project.compile({ raw: true, bitmaps: false, absolute_paths: false });
    const model = typeof compiled === 'string' ? JSON.parse(compiled) : JSON.parse(JSON.stringify(compiled));
    if (!model || !Array.isArray(model.textures)) throw new Error('Blockbench did not return a valid .bbmodel project.');
    if (model.textures.length !== textureInfo.length) throw new Error('Texture list changed while the package was being built.');
    model.textures.forEach((texture, index) => {
      texture.relative_path = `textures/${textureInfo[index].fileName}`;
      delete texture.path;
      delete texture.source;
      delete texture.internal;
      delete texture.shyne_export_role;
    });
    return JSON.stringify(model, null, 2);
  }

  function baseName(path) {
    return String(path || '').replace(/\\/g, '/').split('/').pop();
  }

  function selectLuaFiles(mainName) {
    if (typeof Blockbench === 'undefined' || typeof Blockbench.import !== 'function') return Promise.resolve([]);
    return new Promise((resolve) => {
      Blockbench.import({
        resource_id: 'shyne_lua_scripts',
        type: 'Shyne Lua Scripts',
        extensions: ['lua'],
        multiple: true,
        readtype: 'text'
      }, (files) => {
        const selected = Array.isArray(files) ? files : [];
        if (!selected.length) { resolve(null); return; }
        const scripts = selected.map((file) => ({
          name: baseName(file.name || file.path || 'script.lua'),
          content: String(file.content === undefined ? '' : file.content)
        }));
        const expected = baseName(mainName || 'script.lua').toLowerCase();
        const found = scripts.some((script) => script.name.toLowerCase() === expected);
        if (!found && scripts.length === 1) scripts[0].name = baseName(mainName || 'script.lua');
        resolve(scripts);
      });
    });
  }

  function showMessage(title, message) {
    if (typeof Blockbench !== 'undefined' && typeof Blockbench.showMessageBox === 'function') {
      Blockbench.showMessageBox({ title, message });
    }
  }

  function validateProject(showSuccess, packaging) {
    const problems = [];
    const warnings = [];
    const manifest = buildManifest();
    const stateOwners = new Map();
    const textures = texturePackageInfo();

    if (!manifest.name) problems.push('Avatar name is empty.');
    if (manifest.main && !/^[A-Za-z0-9_.-]+\.lua$/i.test(manifest.main)) {
      problems.push('Advanced Lua Main must be a top-level .lua file name, such as script.lua.');
    }
    animationList().forEach((animation) => {
      const name = animationName(animation);
      const state = String(animation.shyne_state || 'none');
      if (state !== 'none') {
        if (stateOwners.has(state)) problems.push(`Animation state '${state}' is assigned to both '${stateOwners.get(state)}' and '${name}'.`);
        else stateOwners.set(state, name);
      }
    });
    groupList().forEach((group) => {
      const role = String(group.shyne_role || '').trim();
      const parent = String(group.parent_type || 'None');
      if (role && (!parent || parent === 'None')) warnings.push(`Group '${group.name}' has role '${role}' but no vanilla attachment.`);
    });
    if (!animationList().length) warnings.push('The model has no animation. Auto Humanoid can still pose standard body bones.');
    if (!textures.length) problems.push('The model has no texture. Import at least one PNG before exporting a package.');
    const icons = textures.filter((entry) => entry.role === 'icon');
    if (icons.length > 1) problems.push('Only one texture can use the Shyne Package Role: Avatar Icon.');
    if (!icons.length && !textures.some((entry) => entry.fileName.toLowerCase() === 'avatar.png')) {
      warnings.push('No Avatar Icon texture is selected. The package will use Shyne\'s letter fallback icon.');
    }
    textures.filter((entry) => entry.role === 'outfit').forEach((entry) => {
      const width = Number(entry.texture.width || entry.texture.uv_width || 0);
      const height = Number(entry.texture.height || entry.texture.uv_height || 0);
      const valid = width === height && width >= 32 && width <= 1024
        || width === height * 2 && width >= 64 && width <= 1024;
      if (!valid) problems.push(`Outfit texture '${entry.fileName}' must be square 32-1024 px or legacy 2:1.`);
    });
    if (packaging && manifest.main && typeof Blockbench !== 'undefined' && typeof Blockbench.import !== 'function') {
      problems.push('This Blockbench build cannot select Lua files for the package.');
    }

    const lines = [];
    if (problems.length) lines.push('Errors:', ...problems.map((value) => `• ${value}`));
    if (warnings.length) lines.push('Warnings:', ...warnings.map((value) => `• ${value}`));
    if (!problems.length && !warnings.length) lines.push('Standard 2.0 metadata is ready to export.');
    if (typeof Blockbench !== 'undefined' && (showSuccess || problems.length)) {
      Blockbench.showMessageBox({
        title: problems.length ? 'Shyne validation failed' : 'Shyne Standard 2.0',
        message: lines.join('\n')
      });
    }
    return { valid: problems.length === 0, manifest, problems, warnings, textures };
  }

  function exportManifest() {
    const result = validateProject(false);
    if (!result.valid) {
      validateProject(true);
      return;
    }
    if (typeof Blockbench === 'undefined' || typeof Blockbench.export !== 'function') return;
    Blockbench.export({
      resource_id: 'shyne_avatar_manifest',
      type: 'Shyne Avatar Manifest',
      extensions: ['json'],
      name: 'avatar',
      content: JSON.stringify(result.manifest, null, 2),
      savetype: 'text'
    });
  }

  async function exportPackage() {
    const result = validateProject(false, true);
    if (!result.valid) {
      validateProject(true, true);
      return;
    }
    if (typeof JSZip !== 'function') {
      showMessage('Shyne package export failed', 'JSZip is unavailable in this Blockbench build.');
      return;
    }
    let scripts = [];
    if (result.manifest.main) {
      scripts = await selectLuaFiles(result.manifest.main);
      if (scripts === null) return;
      const mainName = baseName(result.manifest.main).toLowerCase();
      const names = new Set();
      for (const script of scripts) {
        const key = script.name.toLowerCase();
        if (!script.name || !/\.lua$/i.test(script.name) || names.has(key)) {
          showMessage('Shyne package export failed', `Lua file names must be unique .lua files: ${script.name || '(empty)'}`);
          return;
        }
        if (!script.content.length) {
          showMessage('Shyne package export failed', `Lua file '${script.name}' is empty.`);
          return;
        }
        names.add(key);
      }
      if (!names.has(mainName)) {
        showMessage('Shyne package export failed', `Select the Lua main file '${baseName(result.manifest.main)}'.`);
        return;
      }
    }

    try {
      const archive = new JSZip();
      const packageId = cleanId(projectValue('shyne_avatar_id', '') || result.manifest.name);
      const root = archive.folder(packageId);
      root.file('avatar.json', JSON.stringify(result.manifest, null, 2));
      root.file('model.bbmodel', compilePortableModel(result.textures));

      for (const entry of result.textures) {
        const base64 = entry.texture && typeof entry.texture.getBase64 === 'function'
          ? entry.texture.getBase64()
          : '';
        if (!base64) throw new Error(`Texture '${entry.fileName}' has no PNG data.`);
        root.file(`textures/${entry.fileName}`, base64, { base64: true });
        if (entry.role === 'icon' || (entry.role === 'model' && entry.fileName.toLowerCase() === 'avatar.png')) {
          root.file('avatar.png', base64, { base64: true });
        }
        if (entry.role === 'outfit') root.file(`outfit/${entry.fileName}`, base64, { base64: true });
      }
      scripts.forEach((script) => root.file(script.name, script.content));

      const content = await archive.generateAsync({ type: 'blob' });
      Blockbench.export({
        resource_id: 'shyne_avatar_package',
        type: 'Shyne Avatar Package',
        extensions: ['zip'],
        name: packageId,
        content,
        savetype: 'zip'
      });
    } catch (error) {
      showMessage('Shyne package export failed', error && error.message ? error.message : String(error));
    }
  }

  function installProperties() {
    const projectType = typeof ModelProject !== 'undefined' ? ModelProject : null;
    const groupType = typeof Group !== 'undefined' ? Group : null;
    const animationType = typeof Animation !== 'undefined' ? Animation : null;
    const textureType = typeof Texture !== 'undefined' ? Texture : null;

    registerProperty(projectType, 'string', 'shyne_avatar_name', { label: 'Shyne Avatar Name', default: '' });
    registerProperty(projectType, 'string', 'shyne_avatar_id', { label: 'Shyne Avatar ID', default: '' });
    registerProperty(projectType, 'enum', 'shyne_manifest_mode', {
      label: 'Shyne Manifest Detail',
      default: 'compact',
      options: { compact: 'Compact (recommended)', explicit: 'Explicit defaults' }
    });
    registerProperty(projectType, 'enum', 'shyne_profile', {
      label: 'Shyne Profile',
      default: 'accessory',
      options: { accessory: 'Accessory', full_body: 'Full Body', custom: 'Custom' }
    });
    registerProperty(projectType, 'number', 'shyne_blend_ticks', { label: 'Animation Blend Ticks', default: 5, min: 0, max: 1200 });
    registerProperty(projectType, 'boolean', 'shyne_use_lua', { label: 'Use Advanced Shyne Lua', default: false });
    registerProperty(projectType, 'string', 'shyne_main', { label: 'Advanced Lua Main', default: 'script.lua' });

    registerProperty(textureType, 'enum', 'shyne_export_role', {
      label: 'Shyne Package Role',
      default: 'model',
      options: { model: 'Model Texture', icon: 'Avatar Icon', outfit: 'Wardrobe Outfit' }
    });

    registerProperty(groupType, 'enum', 'parent_type', {
      label: 'Shyne Vanilla Attachment',
      default: 'None',
      options: {
        None: 'None', Head: 'Head', Body: 'Body', LeftArm: 'Left Arm', RightArm: 'Right Arm',
        LeftLeg: 'Left Leg', RightLeg: 'Right Leg'
      }
    });
    registerProperty(groupType, 'string', 'shyne_role', { label: 'Shyne Role', default: '' });
    registerProperty(groupType, 'string', 'shyne_tags', { label: 'Shyne Tags (comma separated)', default: '' });
    registerProperty(groupType, 'enum', 'shyne_physics', {
      label: 'Shyne Physics Preset',
      default: 'none',
      options: { none: 'None', bunny_ears: 'Bunny Ears', tail: 'Tail', hair: 'Hair', cloth: 'Cloth', wings: 'Wings' }
    });

    registerProperty(animationType, 'enum', 'shyne_state', {
      label: 'Shyne Animation State',
      default: 'none',
      options: {
        none: 'None', idle: 'Idle', walk: 'Walk', sprint: 'Sprint', swim: 'Swim',
        crouch: 'Crouch', sleep: 'Sleep', fly: 'Fly', sit: 'Sit'
      }
    });
    registerProperty(animationType, 'boolean', 'shyne_autoplay', { label: 'Shyne Ambient Autoplay', default: false });
    registerProperty(animationType, 'boolean', 'shyne_blink', { label: 'Shyne Blink', default: false });
  }

  function addMenuAction(action, path) {
    if (typeof MenuBar !== 'undefined' && typeof MenuBar.addAction === 'function') MenuBar.addAction(action, path);
  }

  if (typeof Plugin === 'undefined' || typeof Plugin.register !== 'function') return;
  Plugin.register(PLUGIN_ID, {
    title: 'Shyne Standard 2.0',
    author: 'Shyne Creator',
    description: 'Native model-first metadata, validation, compact avatar.json, and one-click Shyne package export. No Figura dependency.',
    icon: 'accessibility_new',
    version: '2.1.0',
    variant: 'both',
    min_version: '4.10.0',
    onload() {
      installProperties();
      if (typeof Action === 'function') {
        validateAction = new Action('shyne_validate_standard_2', {
          name: 'Validate Shyne Standard 2.0', icon: 'fact_check', click() { validateProject(true); }
        });
        exportAction = new Action('shyne_export_avatar_manifest', {
          name: 'Export Shyne avatar.json', icon: 'save', click: exportManifest
        });
        packageAction = new Action('shyne_export_avatar_package', {
          name: 'Export Shyne Avatar Package (.zip)', icon: 'folder_zip', click: exportPackage
        });
        addMenuAction(validateAction, 'tools');
        addMenuAction(exportAction, 'file.export');
        addMenuAction(packageAction, 'file.export');
      }
    },
    onunload() {
      if (validateAction && typeof validateAction.delete === 'function') validateAction.delete();
      if (exportAction && typeof exportAction.delete === 'function') exportAction.delete();
      if (packageAction && typeof packageAction.delete === 'function') packageAction.delete();
      registeredProperties.forEach((property) => {
        if (property && typeof property.delete === 'function') property.delete();
      });
    }
  });
})();
