#!/usr/bin/env python3
import json, sys
from pathlib import Path

def main():
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path('.')
    mod = root / 'mod.json'
    errors = []
    if not mod.exists():
        errors.append('missing mod.json')
    else:
        try:
            obj = json.loads(mod.read_text(encoding='utf-8'))
            if not obj.get('id'):
                errors.append('mod.json must include id')
            if obj.get('script_engine') not in (None, 'lua'):
                errors.append('script_engine must be lua')
            entry = obj.get('entry') or 'main.lua'
            if not (root / entry).is_file():
                errors.append(f'missing script entry: {entry}')
        except Exception as e:
            errors.append(f'invalid mod.json: {e}')
    if errors:
        print('INVALID')
        for e in errors: print('-', e)
        raise SystemExit(1)
    print('VALID')

if __name__ == '__main__':
    main()
