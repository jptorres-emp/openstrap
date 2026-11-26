"""Keeps the fork's Portuguese (res/values-pt) in step after a merge of the original: drops the Portuguese for any
text the original removed (or marked untranslatable), which lint fails on as ExtraTranslation, and lists the text the
Portuguese is missing or whose English changed since BASE, for the sync's summary. New text shows in English until
it is translated.

Usage: pt_sync.py RES_DIR BASE_REV > summary.md
"""
import os
import re
import subprocess
import sys

NAME = re.compile(r'<(string|plurals)\s+name="([^"]+)"([^>]*)>')


def declared(text):
    """The names a strings file declares, and those marked translatable="false"."""
    names, fixed = set(), set()
    for kind, name, attrs in NAME.findall(text):
        (fixed if 'translatable="false"' in attrs else names).add(name)
    return names, fixed


def english(text):
    """Each string's or plural's text, by name, to see which changed."""
    out = {}
    for m in re.finditer(r'<(string|plurals)\s+name="([^"]+)"[^>]*>(.*?)</\1>', text, re.S):
        out[m.group(2)] = re.sub(r'\s+', ' ', m.group(3)).strip()
    return out


def prune(text, keep):
    """[text] without the strings and plurals whose names aren't in [keep], one block at a time."""
    lines, out, skip = text.split('\n'), [], None
    for line in lines:
        if skip is not None:
            if f'</{skip}>' in line:
                skip = None
            continue
        m = NAME.search(line)
        if m and m.group(2) not in keep:
            if f'</{m.group(1)}>' not in line:
                skip = m.group(1)
            continue
        out.append(line)
    return '\n'.join(out)


def main(res, base):
    values, pt = os.path.join(res, 'values'), os.path.join(res, 'values-pt')
    names, fixed, now = set(), set(), {}
    for f in sorted(os.listdir(values)):
        if f.startswith('strings') and f.endswith('.xml'):
            text = open(os.path.join(values, f), encoding='utf-8').read()
            n, x = declared(text)
            names |= n
            fixed |= x
            now.update(english(text))
    have, dropped = set(), []
    for f in sorted(os.listdir(pt)):
        path = os.path.join(pt, f)
        text = open(path, encoding='utf-8').read()
        n, _ = declared(text)
        gone = sorted(n - names)
        if gone:
            dropped += gone
            text = prune(text, names)
            if not declared(text)[0]:
                os.remove(path)
                continue
            open(path, 'w', encoding='utf-8').write(text)
        have |= n & names
    before = {}
    listed = subprocess.run(['git', 'ls-tree', '--name-only', f'{base}:{os.path.relpath(values)}'], capture_output=True, text=True).stdout
    for f in listed.split():
        if f.startswith('strings'):
            shown = subprocess.run(['git', 'show', f'{base}:{os.path.relpath(values)}/{f}'], capture_output=True, text=True).stdout
            before.update(english(shown))
    missing = sorted(names - have)
    changed = sorted(k for k in names & have if k in before and before[k] != now.get(k))
    print('### Portuguese\n')
    print(f'- Dropped for text the original removed: {len(dropped)}' + (f" ({', '.join(dropped)})" if dropped else ''))
    print(f'- New text still in English: {len(missing)}' + (f" ({', '.join(missing)})" if missing else ''))
    print(f'- English changed since the last sync, Portuguese to check: {len(changed)}' + (f" ({', '.join(changed)})" if changed else ''))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
