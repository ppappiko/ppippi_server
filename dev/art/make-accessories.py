"""Generates worn accessory parts for PipiTravel (AccessoryLayer / PipiArmor.renderAccessory).

Writes, for every accessory model:
  dev/pipi-travel/assets/pipitravel/armor_parts/acc_<slot>_<model>.json          (bare skin)
  dev/pipi-travel/assets/pipitravel/armor_parts/acc_<slot>_<model>_armored.json  (sits just outside armour)
  dev/pipi-travel/assets/pipitravel/textures/models/armor/parts/<same names>.png
Coordinates: full-body mannequin frame (x 0..16 centred on 8, y feet 0 .. head top 32, front = +z, body z 6..10).
Element name suffix picks the bone: _head, _rarm, _larm, _rleg, _lleg, otherwise body.

usage: python dev/art/make-accessories.py
"""
import json
import os
import struct
import zlib

ROOT = os.path.join(os.path.dirname(__file__), '..', 'pipi-travel', 'assets', 'pipitravel')
PARTS = os.path.join(ROOT, 'armor_parts')
TEX = os.path.join(ROOT, 'textures', 'models', 'armor', 'parts')

# 16x16 palette texture, 4x4 swatches: uv rectangles (texture units 0..16) per material
SWATCH = {'gold': (0, 0), 'amber': (4, 0), 'silver': (8, 0), 'amethyst': (12, 0), 'darkgold': (0, 4)}
COLORS = {'gold': (217, 164, 65), 'amber': (255, 150, 30), 'silver': (205, 212, 220), 'amethyst': (155, 93, 229), 'darkgold': (140, 100, 32)}


def uv(mat):
    x, y = SWATCH[mat]
    return [x + .5, y + .5, x + 3.5, y + 3.5]


def box(name, frm, to, mat, rot=None):
    face = {'uv': uv(mat), 'texture': '#0'}
    e = {'name': name, 'from': [round(v, 3) for v in frm], 'to': [round(v, 3) for v in to],
         'faces': {f: dict(face) for f in ('north', 'south', 'east', 'west', 'up', 'down')}}
    if rot:
        axis, angle, origin = rot
        e['rotation'] = {'axis': axis, 'angle': angle, 'origin': [round(v, 3) for v in origin]}
    return e


def mirror_x(e, suffix_from=None, suffix_to=None):
    m = json.loads(json.dumps(e))
    f, t = m['from'], m['to']
    m['from'], m['to'] = [16 - t[0], f[1], f[2]], [16 - f[0], t[1], t[2]]
    if 'rotation' in m:
        m['rotation']['origin'][0] = 16 - m['rotation']['origin'][0]
        if m['rotation']['axis'] in ('y', 'z'):
            m['rotation']['angle'] = -m['rotation']['angle']
    if suffix_from:
        m['name'] = m['name'].replace(suffix_from, suffix_to)
    return m


# ---------------------------------------------------------------- models

def necklace_basic(armored):
    """Gold chain dipping to an amber pendant on the chest."""
    f = 11.05 if armored else 10.0      # chest front (armour outer layer is +1)
    top = 25.0 if armored else 24.0     # shoulder top
    els = [
        box('chain_l', [5.0, 22.4, f], [8.0, 22.9, f + .5], 'gold', ('z', -25, [8, 22.4, f])),
        box('chain_r', [8.0, 22.4, f], [11.0, 22.9, f + .5], 'gold', ('z', 25, [8, 22.4, f])),
        box('strap_l', [5.2, top, 6.4], [5.7, top + .4, f + .3], 'gold'),
        box('strap_r', [10.3, top, 6.4], [10.8, top + .4, f + .3], 'gold'),
        box('setting', [7.3, 20.4, f + .1], [8.7, 22.0, f + .7], 'darkgold'),
        box('gem', [7.55, 20.65, f + .6], [8.45, 21.75, f + 1.0], 'amber'),
    ]
    return els


def earring_basic(armored):
    """Silver drop earrings with an amethyst, hanging under each ear."""
    dx = -.95 if armored else 0.0       # helmet outer layer is 1 px out
    left = [
        box('stud_l_head', [3.55 + dx, 25.2, 7.6], [4.0 + dx, 25.8, 8.4], 'silver'),
        box('drop_l_head', [3.6 + dx, 24.1, 7.75], [3.95 + dx, 25.2, 8.25], 'silver'),
        box('gem_l_head', [3.5 + dx, 23.4, 7.65], [4.05 + dx, 24.1, 8.35], 'amethyst'),
    ]
    return left + [mirror_x(e, '_l_', '_r_') for e in left]


def bracelet_basic(armored):
    """Silver band with an amethyst around the right wrist."""
    o = 1.0 if armored else 0.0         # sleeve outer layer is 1 px out
    y0, y1 = 12.6, 13.6
    return [
        box('band_front_rarm', [-.4 - o, y0, 10.0 + o], [4.4 + o, y1, 10.4 + o], 'silver'),
        box('band_back_rarm', [-.4 - o, y0, 5.6 - o], [4.4 + o, y1, 6.0 - o], 'silver'),
        box('band_out_rarm', [-.4 - o, y0, 6.0 - o], [0.0 - o, y1, 10.0 + o], 'silver'),
        box('band_in_rarm', [4.0 + o, y0, 6.0 - o], [4.4 + o, y1, 10.0 + o], 'silver'),
        box('gem_rarm', [1.5, y0 + .1, 10.3 + o], [2.5, y1 - .1, 10.75 + o], 'amethyst'),
    ]


MODELS = {
    'acc_necklace_basic': necklace_basic,
    'acc_earring_basic': earring_basic,
    'acc_bracelet_basic': bracelet_basic,
}


# ---------------------------------------------------------------- texture

def png(path, w, h, pixel):
    raw = b''.join(b'\x00' + b''.join(struct.pack('BBBB', *pixel(x, y)) for x in range(w)) for y in range(h))

    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    data = b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0)) \
        + chunk(b'IDAT', zlib.compress(raw, 9)) + chunk(b'IEND', b'')
    with open(path, 'wb') as f:
        f.write(data)


def palette_pixel(x, y):
    for mat, (sx, sy) in SWATCH.items():
        if sx <= x < sx + 4 and sy <= y < sy + 4:
            r, g, b = COLORS[mat]
            shade = 1.15 - .1 * ((x - sx) + (y - sy)) / 3   # light from the top-left
            return (min(255, int(r * shade)), min(255, int(g * shade)), min(255, int(b * shade)), 255)
    return (0, 0, 0, 0)


# ---------------------------------------------------------------- inventory icons (16x16 pixel art)
# item icon:   textures/item/accessory/<slot>_<model>.png  (+ models/item/accessory/<slot>_<model>.json)
# empty slot:  textures/gui/accessory/empty_<slot>.png      (grey silhouette, like vanilla's empty armour slots)

ICON_TEX = os.path.join(ROOT, 'textures', 'item', 'accessory')
ICON_MODEL = os.path.join(ROOT, 'models', 'item', 'accessory')
EMPTY_TEX = os.path.join(ROOT, 'textures', 'gui', 'accessory')

INK = {
    'k': (52, 36, 20), 'd': (150, 104, 30), 'g': (222, 170, 62), 'G': (255, 226, 130),     # outline, gold dark/mid/light
    'a': (232, 120, 20), 'A': (255, 196, 90),                                              # amber
    'z': (112, 120, 132), 's': (190, 198, 208), 'S': (245, 248, 252),                       # silver dark/mid/light
    'n': (88, 44, 140), 'm': (150, 90, 222), 'M': (215, 175, 255),                          # amethyst dark/mid/light
}

NECKLACE_ICON = [
    '................',
    '.d...........d..',
    '.g...........g..',
    '.G...........G..',
    '..g.........g...',
    '..d.........d...',
    '...g.......g....',
    '....g.....g.....',
    '.....d...d......',
    '......gGg.......',
    '......dkd.......',
    '.....kAAak......',
    '.....kAaak......',
    '.....kaaak......',
    '......kak.......',
    '.......k........',
]

EARRING_ICON = [
    '................',
    '......zSSz......',
    '.....z....z.....',
    '.....s....s.....',
    '......s..s......',
    '.......ss.......',
    '.......sz.......',
    '.......Sz.......',
    '......kzzk......',
    '.....kMMmmk.....',
    '....kMMmmmnk....',
    '....kMmmmmnk....',
    '....kmmmmnnk....',
    '.....kmmnnk.....',
    '......knnk......',
    '.......kk.......',
]


def bracelet_icon():
    """Silver band seen from slightly above (an elliptical ring), amethyst set in the front."""
    rows = []
    for y in range(16):
        row = ''
        for x in range(16):
            fx, fy = x + .5 - 8, y + .5 - 8.5
            outer = (fx / 7.4) ** 2 + (fy / 5.2) ** 2 <= 1
            inner = (fx / 5.0) ** 2 + ((fy + .9) / 2.9) ** 2 <= 1
            if not outer or inner:
                row += '.'
            elif fy < -1.5:
                row += 'z'                      # back of the band, in shade
            elif fy > 3.2:
                row += 'S' if abs(fx) < 4 else 's'
            else:
                row += 's'
        rows.append(row)
    # (y, x): a 2x2 amethyst with a dark rim, set into the front of the band
    gem = {(10, 7): 'k', (10, 8): 'k', (11, 6): 'k', (11, 7): 'M', (11, 8): 'm', (11, 9): 'k',
           (12, 6): 'k', (12, 7): 'm', (12, 8): 'n', (12, 9): 'k', (13, 7): 'k', (13, 8): 'k'}
    rows = [list(r) for r in rows]
    for (y, x), c in gem.items():
        rows[y][x] = c
    return [''.join(r) for r in rows]


ICONS = {'necklace_basic': NECKLACE_ICON, 'earring_basic': EARRING_ICON, 'bracelet_basic': bracelet_icon()}
EMPTY = {'necklace': NECKLACE_ICON, 'earring': EARRING_ICON, 'bracelet': bracelet_icon()}


def icons():
    for d in (ICON_TEX, ICON_MODEL, EMPTY_TEX):
        os.makedirs(d, exist_ok=True)
    for name, grid in ICONS.items():
        png(os.path.join(ICON_TEX, name + '.png'), 16, 16,
            lambda x, y, g=grid: (*INK[g[y][x]], 255) if g[y][x] != '.' else (0, 0, 0, 0))
        with open(os.path.join(ICON_MODEL, name + '.json'), 'w', encoding='utf-8') as f:
            json.dump({'parent': 'minecraft:item/generated', 'textures': {'layer0': 'pipitravel:item/accessory/' + name}}, f, indent=1)
        print('wrote icon', name)
    for slot, grid in EMPTY.items():
        png(os.path.join(EMPTY_TEX, 'empty_' + slot + '.png'), 16, 16,
            lambda x, y, g=grid: (55, 55, 55, 150) if g[y][x] != '.' else (0, 0, 0, 0))
        print('wrote empty slot', slot)


def main():
    icons()
    os.makedirs(PARTS, exist_ok=True)
    os.makedirs(TEX, exist_ok=True)
    for name, build in MODELS.items():
        for armored in (False, True):
            out = name + ('_armored' if armored else '')
            with open(os.path.join(PARTS, out + '.json'), 'w', encoding='utf-8') as f:
                json.dump({'texture_size': [16, 16], 'elements': build(armored)}, f, indent=1)
            png(os.path.join(TEX, out + '.png'), 16, 16, palette_pixel)
            print('wrote', out)


main()
