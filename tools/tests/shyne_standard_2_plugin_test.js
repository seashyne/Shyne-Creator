'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const pluginPath = path.resolve(__dirname, '..', 'blockbench', 'shyne_standard_2.js');
const source = fs.readFileSync(pluginPath, 'utf8');

function blockbenchType(items = []) {
  function Type() {}
  Type.all = items;
  Type.properties = {};
  return Type;
}

async function run() {
  const actions = {};
  const exports = [];
  const messages = [];
  const archives = [];
  let plugin;

  const textures = [
    { name: 'skin.png', width: 64, height: 64, shyne_export_role: 'model', getBase64: () => 'c2tpbg==' },
    { name: 'library preview.png', width: 128, height: 128, shyne_export_role: 'icon', getBase64: () => 'aWNvbg==' },
    { name: 'formal outfit.png', width: 64, height: 64, shyne_export_role: 'outfit', getBase64: () => 'b3V0Zml0' }
  ];
  const ModelProject = blockbenchType();
  const Group = blockbenchType([{ name: 'BunnyEars', parent_type: 'Head', shyne_role: 'ears' }]);
  const Animation = blockbenchType([]);
  const Texture = blockbenchType(textures);
  const Project = {
    name: 'Untitled',
    shyne_avatar_name: 'Bunny Ears',
    shyne_avatar_id: '',
    shyne_manifest_mode: 'compact',
    shyne_profile: 'accessory',
    shyne_blend_ticks: 5,
    shyne_use_lua: false,
    shyne_main: 'script.lua'
  };

  class Property {
    constructor(target, type, name, options) {
      this.target = target;
      this.name = name;
      target.properties[name] = { type, options };
    }
    delete() { delete this.target.properties[this.name]; }
  }
  class Action {
    constructor(id, options) { this.id = id; Object.assign(this, options); actions[id] = this; }
    delete() { delete actions[this.id]; }
  }
  class JSZip {
    constructor() { this.files = new Map(); archives.push(this); }
    folder(prefix) {
      return { file: (name, content, options) => { this.files.set(`${prefix}/${name}`, { content, options }); return this; } };
    }
    async generateAsync(options) { return { zip: this, options }; }
  }

  const context = {
    console,
    ModelProject,
    Group,
    Animation,
    Texture,
    Project,
    Property,
    Action,
    JSZip,
    MenuBar: { addAction() {} },
    Plugin: { register(id, options) { assert.equal(id, 'shyne_standard_2'); plugin = options; } },
    Codecs: {
      project: {
        compile(options) {
          assert.equal(options.raw, true);
          assert.equal(options.bitmaps, false);
          assert.equal(options.absolute_paths, false);
          return {
            meta: { format_version: '5.0', model_format: 'free' },
            name: 'Bunny Ears',
            resolution: { width: 64, height: 64 },
            elements: [],
            outliner: [],
            textures: textures.map((texture, index) => ({
              id: String(index), name: texture.name, path: `C:/private/${texture.name}`,
              source: 'data:image/png;base64,old', internal: true,
              shyne_export_role: texture.shyne_export_role
            }))
          };
        }
      }
    },
    Blockbench: {
      export(options) { exports.push(options); },
      import(options, callback) {
        assert.equal(options.readtype, 'text');
        callback([{ name: 'custom.lua', content: 'return true' }]);
      },
      showMessageBox(options) { messages.push(options); }
    }
  };

  vm.runInNewContext(source, context, { filename: pluginPath });
  assert.ok(plugin, 'plugin must register');
  plugin.onload();
  assert.equal(plugin.version, '2.2.0');
  assert.ok(ModelProject.properties.shyne_manifest_mode);
  assert.ok(Texture.properties.shyne_export_role);

  actions.shyne_export_lua_starter.click();
  const luaStarter = exports.pop();
  assert.equal(luaStarter.name, 'script');
  assert.match(luaStarter.content, /Idle, Walk and Blink work without a script/);
  assert.equal(Project.shyne_use_lua, true);
  Project.shyne_use_lua = false;

  actions.shyne_export_avatar_manifest.click();
  const manifestExport = exports.pop();
  assert.deepEqual(JSON.parse(manifestExport.content), { name: 'Bunny Ears' });

  await actions.shyne_export_avatar_package.click();
  const zeroLuaArchive = archives.at(-1).files;
  assert.deepEqual(JSON.parse(zeroLuaArchive.get('bunny_ears/avatar.json').content), { name: 'Bunny Ears' });
  const model = JSON.parse(zeroLuaArchive.get('bunny_ears/model.bbmodel').content);
  assert.deepEqual(model.textures.map((texture) => texture.relative_path), [
    'textures/skin.png',
    'textures/library_preview.png',
    'textures/formal_outfit.png'
  ]);
  assert.ok(model.textures.every((texture) => texture.path === undefined && texture.source === undefined));
  assert.ok(zeroLuaArchive.has('bunny_ears/textures/skin.png'));
  assert.ok(zeroLuaArchive.has('bunny_ears/avatar.png'));
  assert.ok(zeroLuaArchive.has('bunny_ears/outfit/formal_outfit.png'));
  assert.equal(exports.at(-1).name, 'bunny_ears');

  Project.shyne_use_lua = true;
  await actions.shyne_export_avatar_package.click();
  const luaArchive = archives.at(-1).files;
  assert.equal(luaArchive.get('bunny_ears/script.lua').content, 'return true');
  assert.deepEqual(JSON.parse(luaArchive.get('bunny_ears/avatar.json').content), {
    name: 'Bunny Ears', main: 'script.lua', api: '2.0'
  });

  plugin.onunload();
  assert.equal(Object.keys(actions).length, 0);
  assert.equal(messages.length, 0);
  process.stdout.write('Shyne Blockbench plugin tests passed.\n');
}

run().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
