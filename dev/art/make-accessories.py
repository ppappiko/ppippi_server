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
SWATCH = {'gold': (0, 0), 'amber': (4, 0), 'silver': (8, 0), 'amethyst': (12, 0), 'darkgold': (0, 4),
          'obsidian': (4, 4), 'sculk': (8, 4), 'sculkdark': (12, 4),
          'pale': (0, 8), 'tear': (4, 8), 'violet': (8, 8), 'crimson': (12, 8),
          'ruby': (0, 12), 'paleshade': (4, 12), 'glow': (8, 12)}
COLORS = {'gold': (217, 164, 65), 'amber': (255, 150, 30), 'silver': (205, 212, 220), 'amethyst': (155, 93, 229), 'darkgold': (140, 100, 32),
          'obsidian': (38, 40, 54), 'sculk': (22, 150, 165), 'sculkdark': (14, 72, 88), 'glow': (130, 245, 235),  # 1티어 깊은 어둠
          'pale': (222, 228, 242), 'paleshade': (140, 148, 170), 'tear': (120, 180, 245), 'violet': (128, 82, 210),  # 2티어 통곡의 왕좌
          'crimson': (190, 24, 48), 'ruby': (250, 100, 110)}                                                    # 보스 전용 비탄의 군주


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


def band(y0, y1, o, mat, sfx=''):
    """Ring around the right wrist (front / back / outer / inner sides)."""
    return [
        box('band_front' + sfx + '_rarm', [-.4 - o, y0, 10.0 + o], [4.4 + o, y1, 10.4 + o], mat),
        box('band_back' + sfx + '_rarm', [-.4 - o, y0, 5.6 - o], [4.4 + o, y1, 6.0 - o], mat),
        box('band_out' + sfx + '_rarm', [-.4 - o, y0, 6.0 - o], [0.0 - o, y1, 10.0 + o], mat),
        box('band_in' + sfx + '_rarm', [4.0 + o, y0, 6.0 - o], [4.4 + o, y1, 10.0 + o], mat),
    ]


def chain(f, top, mat, thick=.5):
    """V chain on the chest plus the straps over both shoulders."""
    return [
        box('chain_l', [5.0, 22.4, f], [8.0, 22.4 + thick, f + .5], mat, ('z', -25, [8, 22.4, f])),
        box('chain_r', [8.0, 22.4, f], [11.0, 22.4 + thick, f + .5], mat, ('z', 25, [8, 22.4, f])),
        box('strap_l', [5.2, top, 6.4], [5.7, top + .4, f + .3], mat),
        box('strap_r', [10.3, top, 6.4], [10.8, top + .4, f + .3], mat),
    ]


# ---- 1티어 · 깊은 어둠 (워든): 흑요석 사슬 + 스컬크 메아리 조각

def necklace_deep(armored):
    """Obsidian chain, sculk setting with tendrils and a glowing echo shard."""
    f = 11.05 if armored else 10.0
    top = 25.0 if armored else 24.0
    return chain(f, top, 'obsidian') + [
        box('setting', [7.35, 21.0, f + .1], [8.65, 22.2, f + .6], 'sculkdark'),
        box('tendril_l', [6.8, 21.35, f + .1], [7.35, 21.65, f + .45], 'sculk'),
        box('tendril_r', [8.65, 21.35, f + .1], [9.2, 21.65, f + .45], 'sculk'),
        box('shard', [7.5, 19.6, f + .45], [8.5, 20.6, f + .95], 'sculk', ('z', 45, [8, 20.1, f])),
        box('core', [7.8, 19.9, f + .9], [8.2, 20.3, f + 1.1], 'glow', ('z', 45, [8, 20.1, f])),
    ]


def earring_deep(armored):
    """Obsidian hook with a sculk shard and a glowing tip."""
    dx = -.95 if armored else 0.0
    left = [
        box('stud_l_head', [3.55 + dx, 25.2, 7.6], [4.0 + dx, 25.8, 8.4], 'obsidian'),
        box('hook_l_head', [3.6 + dx, 24.4, 7.8], [3.95 + dx, 25.2, 8.2], 'obsidian'),
        box('shard_l_head', [3.5 + dx, 23.5, 7.6], [4.05 + dx, 24.3, 8.4], 'sculk', ('x', 45, [3.775 + dx, 23.9, 8.0])),
        box('tip_l_head', [3.62 + dx, 22.9, 7.88], [3.93 + dx, 23.3, 8.12], 'glow'),
    ]
    return left + [mirror_x(e, '_l_', '_r_') for e in left]


def bracelet_deep(armored):
    """Obsidian band with a sculk sensor node in front and one on the outside."""
    o = 1.0 if armored else 0.0
    y0, y1 = 12.6, 13.6
    return band(y0, y1, o, 'obsidian') + [
        box('node_rarm', [1.3, y0 - .15, 10.3 + o], [2.7, y1 + .15, 10.8 + o], 'sculk'),
        box('node_core_rarm', [1.7, y0 + .2, 10.75 + o], [2.3, y1 - .2, 10.95 + o], 'glow'),
        box('node_out_rarm', [-.85 - o, y0 + .1, 7.4], [-.35 - o, y1 - .1, 8.6], 'sculk'),
    ]


# ---- 2티어 · 통곡의 왕좌: 창백한 은 + 눈물 보석

def necklace_sorrow(armored):
    """Pale silver chain with a teardrop pendant and two small tears on the chain."""
    f = 11.05 if armored else 10.0
    top = 25.0 if armored else 24.0
    side = box('side_tear_l', [5.85, 22.55, f + .2], [6.25, 23.05, f + .65], 'violet')
    return chain(f, top, 'pale') + [side, mirror_x(side, '_l', '_r')] + [
        box('clasp', [7.4, 21.6, f + .1], [8.6, 22.2, f + .6], 'paleshade'),
        box('tear_top', [7.75, 20.9, f + .3], [8.25, 21.6, f + .8], 'tear'),
        box('tear', [7.45, 19.7, f + .3], [8.55, 20.9, f + .9], 'tear'),
        box('tear_tip', [7.65, 19.4, f + .4], [8.35, 19.7, f + .8], 'violet'),
        box('shine', [7.55, 20.4, f + .85], [7.85, 20.75, f + .95], 'pale'),
    ]


def earring_sorrow(armored):
    """Long pale drop ending in a tear gem."""
    dx = -.95 if armored else 0.0
    left = [
        box('stud_l_head', [3.55 + dx, 25.2, 7.6], [4.0 + dx, 25.8, 8.4], 'pale'),
        box('drop_l_head', [3.65 + dx, 23.6, 7.85], [3.9 + dx, 25.2, 8.15], 'pale'),
        box('tear_l_head', [3.52 + dx, 22.95, 7.7], [4.03 + dx, 23.6, 8.3], 'tear'),
        box('tip_l_head', [3.62 + dx, 22.6, 7.82], [3.93 + dx, 22.95, 8.18], 'violet'),
    ]
    return left + [mirror_x(e, '_l_', '_r_') for e in left]


def bracelet_sorrow(armored):
    """Pale silver band with a thin rim, a tear gem in front and a violet stone outside."""
    o = 1.0 if armored else 0.0
    y0, y1 = 12.6, 13.6
    return band(y0, y1, o, 'pale') + band(y1, y1 + .25, o + .02, 'paleshade', '_rim') + [
        box('tear_rarm', [1.4, y0 - .1, 10.3 + o], [2.6, y1 + .1, 10.8 + o], 'tear'),
        box('tear_core_rarm', [1.75, y0 + .25, 10.75 + o], [2.25, y1 - .25, 10.95 + o], 'violet'),
        box('stone_out_rarm', [-.85 - o, y0 + .15, 7.5], [-.35 - o, y1 - .15, 8.5], 'violet'),
    ]


# ---- 보스 전용 · 비탄의 군주: 금 + 핏빛 영혼석, 왕관 장식

def necklace_lord(armored):
    """Heavy gold chain, crowned setting with a large crimson soul gem and a hanging drop."""
    f = 11.05 if armored else 10.0
    top = 25.0 if armored else 24.0
    side = box('side_gem_l', [5.8, 22.5, f + .15], [6.3, 23.1, f + .7], 'crimson')
    return chain(f, top, 'gold', .65) + [side, mirror_x(side, '_l', '_r')] + [
        box('setting', [7.1, 20.0, f + .1], [8.9, 22.2, f + .7], 'darkgold'),
        box('gem', [7.4, 20.3, f + .6], [8.6, 21.9, f + 1.1], 'crimson'),
        box('shine', [7.55, 21.3, f + 1.05], [7.95, 21.75, f + 1.15], 'ruby'),
        box('spike_l', [7.15, 22.2, f + .2], [7.5, 22.8, f + .6], 'gold'),
        box('spike_m', [7.8, 22.2, f + .2], [8.2, 23.05, f + .6], 'gold'),
        box('spike_r', [8.5, 22.2, f + .2], [8.85, 22.8, f + .6], 'gold'),
        box('drop', [7.8, 19.5, f + .3], [8.2, 20.0, f + .7], 'gold'),
        box('drop_gem', [7.85, 19.05, f + .35], [8.15, 19.5, f + .65], 'crimson'),
    ]


def earring_lord(armored):
    """Gold hoop with a crimson gem hanging from it."""
    dx = -.95 if armored else 0.0
    left = [
        box('hoop_top_l_head', [3.55 + dx, 25.2, 7.45], [4.0 + dx, 25.6, 8.55], 'gold'),
        box('hoop_front_l_head', [3.55 + dx, 23.6, 8.25], [4.0 + dx, 25.2, 8.55], 'gold'),
        box('hoop_back_l_head', [3.55 + dx, 23.6, 7.45], [4.0 + dx, 25.2, 7.75], 'gold'),
        box('hoop_bottom_l_head', [3.55 + dx, 23.3, 7.45], [4.0 + dx, 23.6, 8.55], 'gold'),
        box('gem_l_head', [3.58 + dx, 22.55, 7.75], [3.97 + dx, 23.3, 8.25], 'crimson'),
    ]
    return left + [mirror_x(e, '_l_', '_r_') for e in left]


def bracelet_lord(armored):
    """Double gold band joined by a plate with a crimson soul gem, gold spikes outside."""
    o = 1.0 if armored else 0.0
    return band(12.2, 12.75, o, 'gold', '_lo') + band(13.45, 14.0, o, 'gold', '_hi') + [
        box('plate_rarm', [0.8, 12.2, 10.25 + o], [3.2, 14.0, 10.6 + o], 'darkgold'),
        box('gem_rarm', [1.35, 12.5, 10.55 + o], [2.65, 13.7, 11.0 + o], 'crimson'),
        box('shine_rarm', [1.5, 13.25, 10.95 + o], [1.9, 13.6, 11.08 + o], 'ruby'),
        box('spike_a_rarm', [-.95 - o, 12.85, 7.0], [-.4 - o, 13.35, 7.6], 'gold'),
        box('spike_b_rarm', [-.95 - o, 12.85, 8.4], [-.4 - o, 13.35, 9.0], 'gold'),
    ]


MODELS = {
    'acc_necklace_basic': necklace_basic,
    'acc_earring_basic': earring_basic,
    'acc_bracelet_basic': bracelet_basic,
    'acc_necklace_deep': necklace_deep,       # 1단계 장신구 (깊은 어둠)
    'acc_earring_deep': earring_deep,
    'acc_bracelet_deep': bracelet_deep,
    'acc_necklace_sorrow': necklace_sorrow,   # 2단계 장신구 (통곡의 왕좌)
    'acc_earring_sorrow': earring_sorrow,
    'acc_bracelet_sorrow': bracelet_sorrow,
    'acc_necklace_lord': necklace_lord,       # 보스 전용 장신구 (비탄의 군주)
    'acc_earring_lord': earring_lord,
    'acc_bracelet_lord': bracelet_lord,
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


# ---- themed icons: 1티어 deep (깊은 어둠), 2티어 sorrow (통곡의 왕좌), 보스 전용 lord (비탄의 군주)
INK.update({
    'K': (10, 14, 22), 'o': (34, 37, 50), 'O': (70, 76, 96),                               # obsidian outline / dark / light
    'e': (12, 70, 86), 'c': (24, 150, 166), 'C': (130, 245, 235),                          # sculk dark / mid / glow
    'q': (120, 128, 152), 'p': (206, 212, 228), 'P': (250, 252, 255),                       # pale silver dark / mid / light
    'u': (40, 54, 110), 't': (110, 170, 240), 'T': (205, 232, 255), 'v': (118, 76, 200),   # tear outline / mid / light, violet
    'x': (70, 8, 20), 'r': (180, 22, 44), 'R': (245, 90, 100), 'Y': (255, 210, 210),       # crimson outline / mid / light / shine
})

NECKLACE_DEEP = [
    '................',
    '.o...........o..',
    '.O...........O..',
    '.o...........o..',
    '..O.........O...',
    '..o.........o...',
    '...O.......O....',
    '....o.....o.....',
    '.....O.c.O......',
    '....c.oeo.c.....',
    '.......K........',
    '......KCK.......',
    '.....KCccK......',
    '....KCccceK.....',
    '.....KcceK......',
    '......KeK.......',
]

EARRING_DEEP = [
    '................',
    '......oOOo......',
    '.....o....o.....',
    '.....O....O.....',
    '......O..o......',
    '.......oo.......',
    '.......Oo.......',
    '.......oO.......',
    '.......KK.......',
    '......KCcK......',
    '.....KCcceK.....',
    '....KCcccceK....',
    '.....KccceK.....',
    '......KceK......',
    '.......KC.......',
    '.......C........',
]

NECKLACE_SORROW = [
    '................',
    '.q...........q..',
    '.p...........p..',
    '.P...........P..',
    '..p.........p...',
    '..v.........v...',
    '...p.......p....',
    '....p.....p.....',
    '.....q...q......',
    '......qPq.......',
    '.......u........',
    '......uTu.......',
    '.....uTttu......',
    '....uTtttvu.....',
    '....utttvvu.....',
    '.....uvvvu......',
]

EARRING_SORROW = [
    '......qPPq......',
    '.....q....q.....',
    '.....p....p.....',
    '......p..p......',
    '.......pp.......',
    '.......p........',
    '.......P........',
    '.......p........',
    '.......q........',
    '.......u........',
    '......uTu.......',
    '.....uTttu......',
    '....uTtttvu.....',
    '....utttvvu.....',
    '.....uvvvu......',
    '......uuu.......',
]

NECKLACE_LORD = [
    '.d...........d..',
    '.g...........g..',
    '.G...........G..',
    '..g.........g...',
    '..r.........r...',
    '...g.......g....',
    '....g..G..g.....',
    '.....dGgGd......',
    '....dGgggGd.....',
    '....kxxxxxk.....',
    '....xRRrrrx.....',
    '....xRYrrrx.....',
    '....xrrrrxx.....',
    '.....xrrrx......',
    '......dGd.......',
    '.......r........',
]

EARRING_LORD = [
    '................',
    '......dGGd......',
    '.....g....g.....',
    '....G......G....',
    '....g......g....',
    '....g......g....',
    '....d......d....',
    '.....g....g.....',
    '......dGGd......',
    '.......kk.......',
    '......xRrx......',
    '.....xRYrrx.....',
    '.....xRrrrx.....',
    '.....xrrrxx.....',
    '......xrrx......',
    '.......xx.......',
]


def ring_icon(dark, mid, light, gem):
    """Band seen from slightly above (an elliptical ring) in the given inks, with gem pixels {(y, x): ink} on top."""
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
                row += dark
            elif fy > 3.2:
                row += light if abs(fx) < 4 else mid
            else:
                row += mid
        rows.append(list(row))
    for (y, x), c in gem.items():
        rows[y][x] = c
    return [''.join(r) for r in rows]


BRACELET_DEEP = ring_icon('K', 'o', 'O', {
    (10, 7): 'K', (10, 8): 'K', (11, 6): 'K', (11, 7): 'C', (11, 8): 'c', (11, 9): 'K',
    (12, 6): 'K', (12, 7): 'c', (12, 8): 'e', (12, 9): 'K', (13, 7): 'K', (13, 8): 'K',
    (8, 1): 'c', (9, 1): 'C', (8, 14): 'c', (9, 14): 'C'})
BRACELET_SORROW = ring_icon('q', 'p', 'P', {
    (9, 7): 'u', (9, 8): 'u', (10, 6): 'u', (10, 7): 'T', (10, 8): 't', (10, 9): 'u',
    (11, 6): 'u', (11, 7): 't', (11, 8): 't', (11, 9): 'u', (12, 6): 'u', (12, 7): 't', (12, 8): 'v', (12, 9): 'u',
    (13, 7): 'u', (13, 8): 'u', (8, 1): 'v', (8, 14): 'v'})
BRACELET_LORD = ring_icon('d', 'g', 'G', {
    (9, 6): 'x', (9, 7): 'x', (9, 8): 'x', (9, 9): 'x',
    (10, 5): 'x', (10, 6): 'R', (10, 7): 'Y', (10, 8): 'r', (10, 9): 'r', (10, 10): 'x',
    (11, 5): 'x', (11, 6): 'R', (11, 7): 'r', (11, 8): 'r', (11, 9): 'r', (11, 10): 'x',
    (12, 6): 'x', (12, 7): 'r', (12, 8): 'r', (12, 9): 'x', (13, 7): 'x', (13, 8): 'x',
    (4, 3): 'G', (3, 4): 'G', (4, 12): 'G', (3, 11): 'G', (7, 0): 'G', (7, 15): 'G'})

ICONS = {'necklace_basic': NECKLACE_ICON, 'earring_basic': EARRING_ICON, 'bracelet_basic': bracelet_icon(),
         'necklace_deep': NECKLACE_DEEP, 'earring_deep': EARRING_DEEP, 'bracelet_deep': BRACELET_DEEP,
         'necklace_sorrow': NECKLACE_SORROW, 'earring_sorrow': EARRING_SORROW, 'bracelet_sorrow': BRACELET_SORROW,
         'necklace_lord': NECKLACE_LORD, 'earring_lord': EARRING_LORD, 'bracelet_lord': BRACELET_LORD}
for _name, _grid in ICONS.items():
    assert len(_grid) == 16 and all(len(r) == 16 for r in _grid), _name
    assert all(c == '.' or c in INK for r in _grid for c in r), _name
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
