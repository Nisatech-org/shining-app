"""Tiny brush-lettering engine.

Strokes are lists of control points (x, y, w) in an upright design space
(y up, baseline 0). Centerlines are Catmull-Rom splines; width is
interpolated along them. The centerline is sheared/rotated into the final
slanted space, then an elliptical nib is stamped along it. The raster is
traced with potrace to get clean bezier outlines.
"""
import math, subprocess, re, os
import numpy as np
from PIL import Image, ImageDraw


def catmull(pts, n=40):
    """Centripetal-ish Catmull-Rom through pts (list of tuples of any length)."""
    P = [np.array(p, float) for p in pts]
    if len(P) == 2:
        return [P[0] + (P[1] - P[0]) * t for t in np.linspace(0, 1, n)]
    P = [2 * P[0] - P[1]] + P + [2 * P[-1] - P[-2]]
    out = []
    for i in range(1, len(P) - 2):
        p0, p1, p2, p3 = P[i - 1], P[i], P[i + 1], P[i + 2]
        for t in np.linspace(0, 1, n, endpoint=(i == len(P) - 3)):
            t2, t3 = t * t, t * t * t
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    return out


class Pen:
    def __init__(self, slant_deg=22, rot_deg=-8, nib_deg=35, nib_ratio=0.6):
        self.slant = math.tan(math.radians(slant_deg))
        r = math.radians(rot_deg)
        self.cr, self.sr = math.cos(r), math.sin(r)
        self.nib = math.radians(nib_deg)
        self.ratio = nib_ratio
        self.strokes = []   # list of (pts, opts)
        self.holes = []

    def xf(self, x, y):
        x = x + y * self.slant
        # rotate (y up): positive rot = counter-clockwise
        return (x * self.cr - y * self.sr, x * self.sr + y * self.cr)

    def stroke(self, pts, ratio=None, nib=None, upright=False):
        self.strokes.append((pts, dict(ratio=ratio, nib=nib, upright=upright)))

    def dab(self, x, y, size, angle=60, length=None, ratio=None):
        """A brush dot: short stroke swelling in the middle (teardrop/diamond)."""
        L = length if length is not None else size * 0.9
        a = math.radians(angle)
        dx, dy = math.cos(a) * L / 2, math.sin(a) * L / 2
        self.stroke([(x - dx, y - dy, size * 0.35), (x, y, size), (x + dx, y + dy, size * 0.55)], ratio=ratio)

    # ------------------------------------------------------------------
    def samples(self):
        """Yield per-stroke arrays of (X, Y, w) in final (slanted) space."""
        res = []
        for pts, o in self.strokes:
            curve = catmull(pts, n=60)
            arr = []
            for c in curve:
                X, Y = self.xf(c[0], c[1])
                arr.append((X, Y, max(c[2], 0.0)))
            res.append((np.array(arr), o))
        return res

    def bounds(self, pad=1.5):
        allp = np.vstack([a for a, _ in self.samples()])
        r = allp[:, 2] / 2 + pad
        return (allp[:, 0] - r).min(), (allp[:, 1] - r).min(), (allp[:, 0] + r).max(), (allp[:, 1] + r).max()

    def render(self, scale=40, box=None, guides=False, bg=0, fg=255):
        x0, y0, x1, y1 = box or self.bounds()
        W, H = int((x1 - x0) * scale), int((y1 - y0) * scale)
        img = Image.new('L', (W, H), bg)
        d = ImageDraw.Draw(img)
        to_px = lambda X, Y: ((X - x0) * scale, (y1 - Y) * scale)
        for arr, o in self.samples():
            ratio = o['ratio'] if o['ratio'] is not None else self.ratio
            nib = math.radians(o['nib']) if o['nib'] is not None else self.nib
            # resample densely by arc length
            seg = np.hypot(np.diff(arr[:, 0]), np.diff(arr[:, 1]))
            s = np.concatenate([[0], np.cumsum(seg)])
            n = max(int(s[-1] * scale * 2), 2)
            ss = np.linspace(0, s[-1], n)
            X = np.interp(ss, s, arr[:, 0]); Y = np.interp(ss, s, arr[:, 1]); Wd = np.interp(ss, s, arr[:, 2])
            ca, sa = math.cos(nib), math.sin(nib)
            th = np.linspace(0, 2 * math.pi, 48, endpoint=False)
            ex, ey = np.cos(th), np.sin(th)
            for xi, yi, wi in zip(X, Y, Wd):
                if wi <= 0.01:
                    continue
                a, b = wi / 2, wi / 2 * ratio
                px = xi + a * ex * ca - b * ey * sa
                py = yi + a * ex * sa + b * ey * ca
                d.polygon([to_px(u, v) for u, v in zip(px, py)], fill=fg)
        if guides:
            img = img.convert('RGB')
            d = ImageDraw.Draw(img)
            for arr, _ in self.samples():
                d.line([to_px(u, v) for u, v, _ in arr], fill=(0, 160, 255), width=2)
            for pts, _ in self.strokes:
                for (x, y, w) in pts:
                    X, Y = self.xf(x, y)
                    px, py = to_px(X, Y)
                    d.ellipse([px - 5, py - 5, px + 5, py + 5], outline=(255, 80, 0), width=2)
            # baseline and x-height guides
            for gy, col in ((0, (0, 200, 0)), (10, (200, 200, 0)), (34, (200, 0, 200))):
                a = to_px(*self.xf(-20, gy)); b = to_px(*self.xf(140, gy))
                d.line([a, b], fill=col, width=1)
        return img, (x0, y0, x1, y1, scale)


def trace(img, box, out_prefix, turdsize=20, alphamax=1.0, opttol=0.2):
    """Trace a white-on-black raster to an SVG path string in final coords."""
    x0, y0, x1, y1, scale = box
    bw = img.point(lambda v: 0 if v > 127 else 255).convert('1')  # potrace traces black
    bw.save(out_prefix + '.pbm')
    subprocess.run(['potrace', out_prefix + '.pbm', '-s', '-o', out_prefix + '.trace.svg',
                    '-t', str(turdsize), '-a', str(alphamax), '-O', str(opttol), '--flat'], check=True)
    s = open(out_prefix + '.trace.svg').read()
    g = re.search(r'<g transform="([^"]+)"[^>]*>(.*?)</g>', s, re.S)
    tr, body = g.group(1), g.group(2)
    ds = re.findall(r'd="([^"]+)"', body)
    return tr, ' '.join(d.replace('\n', ' ') for d in ds), (img.width, img.height)
