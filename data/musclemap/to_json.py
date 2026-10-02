"""Turns the vendored MuscleMap diagrams into the JSON the app draws from.

    python data/musclemap/to_json.py

Reads `male-front.ts` and `male-back.ts` beside this file (a snapshot of Jsplice/MuscleMap, see
README.md) and writes `app/src/main/assets/musclemap/{front,back}.json`: the viewBox cropped to the
body, the outline paths, and each muscle path with its MuscleMap group.
"""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, '..', '..', 'app', 'src', 'main', 'assets', 'musclemap')


def entries(source, name):
    block = re.search(r'const ' + name + r'[^=]*=\s*\[(.*?)\n\];', source, re.S).group(1)
    for obj in re.finditer(r'\{(.*?)\}', block, re.S):
        body = obj.group(1)
        d = re.search(r'(?<![A-Za-z])d:\s*"([^"]+)"', body).group(1)
        group = re.search(r'group:\s*"([A-Z_]+)"', body)
        yield {'group': group.group(1) if group else None, 'd': d}


def bounds(paths):
    xs, ys = [], []
    for d in paths:
        numbers = [float(n) for n in re.findall(r'-?\d+(?:\.\d+)?', d)]
        xs += numbers[0::2]
        ys += numbers[1::2]
    return min(xs), min(ys), max(xs), max(ys)


def convert(source_name, out_name):
    source = open(os.path.join(HERE, source_name), encoding='utf-8').read()
    outline = [e['d'] for e in entries(source, 'OUTLINE')]
    muscles = [e for e in entries(source, 'MUSCLES')]
    x0, y0, x1, y1 = bounds(outline)
    pad = 8
    view = [round(x0 - pad), round(y0 - pad), round(x1 - x0 + 2 * pad), round(y1 - y0 + 2 * pad)]
    os.makedirs(OUT, exist_ok=True)
    with open(os.path.join(OUT, out_name), 'w', encoding='utf-8', newline='\n') as f:
        json.dump({'viewBox': view, 'outline': outline, 'muscles': muscles}, f, separators=(',', ':'))
    print(out_name, view, len(outline), 'outline,', len(muscles), 'muscles')


convert('male-front.ts', 'front.json')
convert('male-back.ts', 'back.json')
