# Logo

`shining-logo.svg` is the شاینینگ logo: the red oval, white ring and green swooshes from the original artwork, with hand-lettered brush script in place of the original word. The PNGs are 512 px and 1024 px renders of it.

## Regenerating

The lettering is drawn by code in `source/`:

- `brush.py` is a small brush engine. Strokes are spline centerlines with a width at each point; an elliptical nib is stamped along them and the result is traced with potrace.
- `lettering.py` places every stroke of the word (teeth, alef, گ, dots).
- `export.py` slants and renders the lettering, traces it, bakes all transforms into plain absolute paths and writes the 40x40 SVG.
- `original.svg` is the source artwork the oval, ring and swooshes are taken from.

Needs Python 3 with numpy and Pillow, plus `potrace` (`brew install potrace`).

```bash
python3 docs/logo/source/export.py
```

That overwrites `docs/logo/shining-logo.svg`. To refresh the PNGs:

```bash
rsvg-convert -w 512 -h 512 docs/logo/shining-logo.svg -o docs/logo/shining-logo.png
```

```bash
rsvg-convert -w 1024 -h 1024 docs/logo/shining-logo.svg -o docs/logo/shining-logo@2x.png
```

Slant, rotation, size and position are set at the top of `export.py`. Stroke weight, tooth spacing and the other letter parameters are arguments to `lettering.build()`.
