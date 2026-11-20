"""The شاینینگ wordmark as brush strokes, in an upright design space
(baseline y=0, x runs right to left as the word is written). build() returns
a brush.Pen; export.py slants, renders and traces it."""
from brush import Pen

W = 8.0
SP = 8.5
TW = 0.8
TH = 15.0


def build(**o):
    W_ = o.get('W', 10.0); sp = o.get('sp', 9.5); tw = o.get('tw', TW); th = o.get('th', TH)
    bowl = o.get('bowl', 2.2)
    p = Pen(slant_deg=o.get('slant', 22), rot_deg=o.get('rot', -8), nib_deg=35, nib_ratio=0.62)
    W = W_

    def sig(x, top, w, base=1.5):
        p.stroke([(x - 1.8, top - 4.5, 0.8), (x - 0.2, top + .2, w * .6), (x + .6, top - .8, w * .85), (x + .1, top / 2, w * .72), (x - .3, base, w * .52)])

    def tooth(x, top, w, base=1.5):
        p.stroke([(x + .9, top + .3, 1.0), (x + .6, top - 1.0, w * .95), (x, top / 2 + 1, w * .85), (x - .5, base, w * .62)], ratio=.45)

    def dab(x, y, s, ang_dx=1.6, ang_dy=1.2):
        """slanted brush press: thin entry lower-left, fat body, quick lift upper-right"""
        p.stroke([(x - ang_dx, y - ang_dy, s * .35), (x - ang_dx * .3, y - ang_dy * .3, s * .95), (x + ang_dx * .4, y + ang_dy * .4, s * .8), (x + ang_dx, y + ang_dy, s * .3)], ratio=.55)

    def dash(x, y, L=7.0, s=W * .78):
        p.stroke([(x - L / 2, y - 1.1, s * .35), (x - L / 5, y - .3, s * .95), (x + L / 5, y + .3, s * .75), (x + L / 2, y + 1.3, s * .25)], ratio=.55)

    def caret(x, y, s=W * .66):
        k = 0.404  # tan(22deg)
        V = [(-4.6, -5.4, .3), (-2.6, -2.2, .8), (-0.3, 0.9, .72), (0.6, 0.7, .66), (2.6, -2.2, .58), (4.6, -5.4, .15)]
        p.stroke([(x + vx - k * vy, y + vy, s * w) for vx, vy, w in V], ratio=.55)

    # ---------------- ش
    x1 = 100 + 2 * sp
    t = [x1, x1 - sp * .9, x1 - sp * 1.8]
    sig(t[0], th + 1.0, W * .95)
    tooth(t[1], th - 2.5, W * tw)
    tooth(t[2], th - 1.5, W * tw)
    ax = t[2] - sp * 1.25
    p.stroke([(t[0] - 0.8, 1.4, W * .5), (t[1], 0.4, W * .72), (t[2], 0.0, W * .8), ((t[2] + ax) / 2, -0.2, W * .85), (ax + 1.5, 0.6, W * .72), (ax - 0.2, 2.0, W * .55)])
    sd = o.get('shdots', 'tri')
    if sd == 'caret':
        caret(t[1] + 0.3, th + 9.6)
    elif sd == 'caret_s':
        caret(t[1] + 0.3, th + 8.6, s=W * .58)
    elif sd == 'tri':
        ds = W * o.get('dsz', .64); cx_ = t[1] + o.get('dcx', 1.4); hs = o.get('dhs', 3.8); vy = o.get('dvy', 6.2); by = o.get('dby', 6.3)
        tilt = hs * 0.14   # undo the -8deg rotation so the pair sits level
        dab(cx_ - hs, th + by - tilt, ds); dab(cx_ + hs, th + by + tilt, ds)
        dab(cx_ - 0.404 * vy * o.get('dsh', 1.0), th + by + vy, ds)   # partially undo the shear
    elif sd == 'tri_tight':
        ds = W * .7
        dab(t[1] - 2.4, th + 5.2, ds); dab(t[1] + 2.4, th + 5.2, ds); dab(t[1] + 0.2, th + 10.2, ds)

    # ---------------- ا  (pointed head with a small notch, like the original's alef)
    A = o.get('alef', 37.0)
    p.stroke([(ax - 2.2, A - 6.5, 0.6), (ax + 0.2, A - 1.2, W * .6), (ax + 2.2, A, W * .95), (ax + 3.2, A - 2.2, W * 1.0), (ax + 2.4, A * .62, W * .95), (ax + 0.6, 8, W * .8), (ax, 1.5, W * .6)])

    # ---------------- ینین
    y0 = ax - sp * 1.0
    yt = [y0 - i * sp for i in range(4)]
    sig(yt[0], th + 1.0, W * .92)
    tooth(yt[1], th - 2.5, W * tw)
    tooth(yt[2], th - 0.5, W * tw)
    tooth(yt[3], th - 2.5, W * tw)
    gx = yt[3] - o.get('gap', 18.5)
    # base: through the teeth, dipping into a bowl under the gaf, then a tapering upturn
    p.stroke([(yt[0] - 0.8, 1.4, W * .45), (yt[1], 0.5, W * .65), (yt[2], 0.1, W * .72), (yt[3], -0.2, W * .8),
              (gx + 7, -0.8 - bowl * .45, W * .92), (gx - 0.5, -0.9 - bowl * .75, W * .98),
              (gx - 5.4, 1.8, W * .8), (gx - 6.8, 5.6, W * .62), (gx - 7.0, 9.0, W * .42), (gx - 6.4, 12.0, W * .14)])
    if o.get('yedots', 'dash') == 'dash':
        dash(yt[0] - 3.2, -8.4)
        dash(yt[2] - 3.2, -8.4)
    else:
        ds2 = W * o.get('ysz', .6); hs2 = o.get('yhs', 2.6)
        for xx in (yt[0] - 3.0, yt[2] - 3.0):
            dab(xx - hs2, -8.0 - hs2 * .14, ds2); dab(xx + hs2, -8.0 + hs2 * .14, ds2)
    dab(yt[1] - 0.6, th + 6.0, W * .9)
    dab(yt[3] + 0.2, th + 5.5, W * .9)

    # ---------------- گ  main diagonal + a true parallel slash
    import math
    if o.get('spike', 1):
        # Kalleh-style stepped tip: thick body stops, a thin spike carries on from its upper edge
        p.stroke([(gx, 0.0, W * .72), (gx + 1.4, 7, W * .9), (gx + 5.2, 16.5, W * .98), (gx + 9.4, 22.6, W * .86), (gx + 11.4, 25.0, W * .55)])
        sx, sy = o.get('spx', -1.0), o.get('spy', 0.6)
        p.stroke([(gx + 8.0 + sx, 21.5 + sy, W * .2), (gx + 10.0 + sx, 24.2 + sy, W * .5), (gx + 13.0 + sx, 28.0 + sy, W * .32), (gx + 15.6 + sx, 31.2 + sy, W * .06)])
    else:
        p.stroke([(gx, 0.0, W * .72), (gx + 1.4, 7, W * .9), (gx + 5.2, 16.5, W * .98), (gx + 10.2, 23.8, W * .8), (gx + 12.6, 26.9, W * .45), (gx + 14.0, 28.7, W * .12)])
    d0, d1 = (gx + 5.2, 16.5), (gx + 13.4, 27.8)
    L = math.hypot(d1[0] - d0[0], d1[1] - d0[1]); ux_, uy_ = (d1[0] - d0[0]) / L, (d1[1] - d0[1]) / L
    off = o.get('goff', W * 0.62 + 4.2)
    nx, ny = -uy_ * off, ux_ * off
    t0, t1 = o.get('gt0', 0.12), o.get('gt1', 1.08)
    a0 = (d0[0] + (d1[0] - d0[0]) * t0 + nx, d0[1] + (d1[1] - d0[1]) * t0 + ny)
    a1 = (d0[0] + (d1[0] - d0[0]) * t1 + nx, d0[1] + (d1[1] - d0[1]) * t1 + ny)
    lerp = lambda k: (a0[0] + (a1[0] - a0[0]) * k, a0[1] + (a1[1] - a0[1]) * k)
    p.stroke([(*lerp(0), W * .25), (*lerp(.18), W * .68), (*lerp(.55), W * .62), (*lerp(.85), W * .42), (*lerp(1), W * .12)])
    return p

