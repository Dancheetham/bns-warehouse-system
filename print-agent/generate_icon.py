"""
Generates assets/icon.ico - the system tray / taskbar icon for the Print Agent.

Not something the agent needs to run at startup - this is a one-off build
step. The icon is drawn procedurally (a printer with a little person behind
it) rather than sourced from an image file, so there's nothing external to
track or re-fetch. Re-run this only if the icon design itself needs to
change:

    python generate_icon.py

Produces assets/icon.ico, a multi-resolution .ico (16/32/48/64/256 px) used
both by the running tray icon (pystray) and, at build time, as the .exe's
own icon (see build_exe.bat).
"""

import os

from PIL import Image, ImageDraw

SIZE = 256  # draw large, then downscale for the smaller ICO frames
OUT_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "assets")
OUT_PATH = os.path.join(OUT_DIR, "icon.ico")

PRINTER_GREY = (90, 98, 110, 255)
PRINTER_GREY_DARK = (60, 66, 76, 255)
PAPER_WHITE = (245, 247, 250, 255)
PERSON_BLUE = (37, 99, 235, 255)
PERSON_SKIN = (247, 200, 165, 255)
ACCENT = (34, 197, 94, 255)  # little "ready" light on the printer


def draw_icon():
    img = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # --- the little person, standing behind/beside the printer -------------
    # Drawn first so the printer overlaps its lower half, reading as "behind".
    head_cx, head_cy, head_r = 96, 60, 26
    d.ellipse(
        [head_cx - head_r, head_cy - head_r, head_cx + head_r, head_cy + head_r],
        fill=PERSON_SKIN,
    )
    # body (rounded trapezoid via polygon)
    d.polygon(
        [
            (head_cx - 34, 210),
            (head_cx - 46, 96),
            (head_cx + 46, 96),
            (head_cx + 34, 210),
        ],
        fill=PERSON_BLUE,
    )

    # --- the printer, in front ---------------------------------------------
    body_left, body_top, body_right, body_bottom = 70, 118, 246, 210
    d.rounded_rectangle(
        [body_left, body_top, body_right, body_bottom], radius=14, fill=PRINTER_GREY
    )

    # paper feed slot on top
    d.rounded_rectangle(
        [body_left + 24, body_top - 18, body_right - 24, body_top + 10],
        radius=8,
        fill=PRINTER_GREY_DARK,
    )
    # a sheet of paper poking out the top
    d.rectangle([body_left + 40, body_top - 34, body_right - 40, body_top - 4], fill=PAPER_WHITE)

    # output tray + printed sheet at the front
    d.rounded_rectangle(
        [body_left + 10, body_bottom - 6, body_right - 10, body_bottom + 30],
        radius=6,
        fill=PRINTER_GREY_DARK,
    )
    d.rectangle([body_left + 26, body_bottom + 2, body_right - 26, body_bottom + 26], fill=PAPER_WHITE)

    # control panel: a couple of buttons + a "ready" light
    d.ellipse([body_right - 40, body_top + 16, body_right - 24, body_top + 32], fill=ACCENT)
    d.rounded_rectangle([body_left + 20, body_top + 14, body_left + 60, body_top + 30], radius=6, fill=(200, 206, 214, 255))

    return img


def main():
    os.makedirs(OUT_DIR, exist_ok=True)
    base = draw_icon()
    sizes = [16, 24, 32, 48, 64, 128, 256]
    frames = [base.resize((s, s), Image.LANCZOS) for s in sizes]
    frames[0].save(
        OUT_PATH,
        format="ICO",
        sizes=[(s, s) for s in sizes],
        append_images=frames[1:],
    )
    # also drop a plain PNG for anywhere an .ico isn't convenient (e.g. docs)
    base.save(os.path.join(OUT_DIR, "icon.png"))
    print(f"Wrote {OUT_PATH}")


if __name__ == "__main__":
    main()
