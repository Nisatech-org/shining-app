"""Final export: render the lettering finely, trace it, bake every transform
into absolute path coordinates, and write a clean 40x40 SVG."""
import os, re, sys, tempfile
import numpy as np
from brush import trace
import lettering

HERE = os.path.dirname(os.path.abspath(__file__))
# The oval, ring and swooshes come straight from the original artwork.
_P = re.findall(r'<path d="([^"]+)" fill="([^"]+)"/>', open(os.path.join(HERE, 'original.svg')).read())
RING, RED, G1, G2, G3 = [p[0] for p in _P[:5]]
RED_ELLIPSE = RED.split('ZM14.9555')[0] + 'Z'   # the red oval without the original lettering cut out of it

PARAMS = dict(slant=25, rot=-10)
WIDTH, CX, CY = 30.2, 19.85, 20.15
SCALE = 100


def tokens(d):
    for m in re.finditer(r'[MmLlCcZz]|-?\d*\.?\d+(?:e-?\d+)?', d):
        yield m.group(0)


def bake(d, f):
    """potrace path (M/m, l, c, z with relative coords) -> absolute path with affine f applied."""
    out, cur, start, cmd = [], (0.0, 0.0), (0.0, 0.0), None
    toks = list(tokens(d)); i = 0
    num = lambda: float(toks[i])
    fmt = lambda p: '%s %s' % tuple(('%.3f' % v).rstrip('0').rstrip('.') for v in f(*p))
    while i < len(toks):
        t = toks[i]
        if t in 'MmLlCcZz':
            cmd = t; i += 1
            if t in 'Zz':
                out.append('Z'); cur = start
            continue
        if cmd in 'Mm':
            x, y = float(toks[i]), float(toks[i + 1]); i += 2
            if cmd == 'm' and out:
                x, y = cur[0] + x, cur[1] + y
            cur = start = (x, y); out.append('M' + fmt(cur))
            cmd = 'l' if cmd == 'm' else 'L'
        elif cmd in 'Ll':
            x, y = float(toks[i]), float(toks[i + 1]); i += 2
            if cmd == 'l':
                x, y = cur[0] + x, cur[1] + y
            cur = (x, y); out.append('L' + fmt(cur))
        elif cmd in 'Cc':
            v = [float(toks[i + k]) for k in range(6)]; i += 6
            if cmd == 'c':
                v = [v[k] + cur[k % 2] for k in range(6)]
            pts = [(v[0], v[1]), (v[2], v[3]), (v[4], v[5])]
            out.append('C' + ' '.join(fmt(p) for p in pts)); cur = pts[2]
    return ''.join(out)


def build_svg(params=PARAMS, width=WIDTH, cx=CX, cy=CY, scale=SCALE):
    pen = lettering.build(**params)
    img, box = pen.render(scale=scale)
    a = np.array(img) > 127
    ys, xs = np.nonzero(a)
    ix0, ix1, iy0, iy1 = xs.min(), xs.max(), ys.min(), ys.max()
    with tempfile.TemporaryDirectory() as tmp:
        tr, d, (Wpx, Hpx) = trace(img, box, os.path.join(tmp, 'lettering'), turdsize=40, alphamax=1.1, opttol=0.2)
    m = re.match(r'translate\(([-\d.]+),([-\d.]+)\) scale\(([-\d.]+),([-\d.]+)\)', tr)
    ptx, pty, psx, psy = map(float, m.groups())
    s = width / (ix1 - ix0)
    tx = cx - s * (ix0 + ix1) / 2
    ty = cy - s * (iy0 + iy1) / 2
    f = lambda x, y: (s * (ptx + psx * x) + tx, s * (pty + psy * y) + ty)
    text_d = bake(d, f)
    return f'''<svg width="40" height="40" viewBox="0 0 40 40" fill="none" xmlns="http://www.w3.org/2000/svg">
<path d="{RING}" fill="#FEFEFE"/>
<path d="{RED_ELLIPSE}" fill="#E60002"/>
<path d="{G1}" fill="#047804"/>
<path d="{G2}" fill="#047804"/>
<path d="{G3}" fill="#059352"/>
<path d="{text_d}" fill="#FEFDFD"/>
</svg>
'''


if __name__ == '__main__':
    out = sys.argv[1] if len(sys.argv) > 1 else os.path.join(HERE, '..', 'shining-logo.svg')
    svg = build_svg()
    open(out, 'w').write(svg)
    print(out, len(svg), 'bytes')
