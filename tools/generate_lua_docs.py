#!/usr/bin/env python3
from pathlib import Path
DOC = """# Shyne Creator Lua Avatar API\n\n## Globals\n- avatar.id()\n- state.get(key)\n- state.set(key, value)\n- models.model.<Part>:visible(bool)\n- models.model.<Part>:rot(x, y, z)\n- models.model.<Part>:pos(x, y, z)\n- models.model.<Part>:scale(x, y, z)\n- vanilla_model.PLAYER:visible(bool)\n- anim.play(name)\n- anim.stop(name)\n- shyne_palette:newPage()\n- shyne_palette:newAction():title(text):onLeftClick(fn)\n\n## Events\n- function events.ENTITY_INIT() end\n- function events.TICK() end\n- function events.RENDER() end\n"""
Path('docs/LUA_AVATAR_AUTOCOMPLETE_TH.md').write_text(DOC)
print('wrote docs/LUA_AVATAR_AUTOCOMPLETE_TH.md')
