import io
import os
import subprocess
import sys
import xml.etree.ElementTree as ET

SVG_NS = "{http://www.w3.org/2000/svg}"
ARTWORK = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(ARTWORK)
MASTER = os.path.join(ARTWORK, "ic_launcher.svg")
STORE_PNG = os.path.join(
    ROOT, "fastlane", "metadata", "android", "en-US", "images", "icon.png")
FOREGROUND = os.path.join(
    ROOT, "mobile", "src", "main", "res", "drawable", "ic_launcher_foreground.xml")
MONOCHROME = os.path.join(
    ROOT, "mobile", "src", "main", "res", "drawable", "ic_launcher_monochrome.xml")
LEGACY = os.path.join(
    ROOT, "mobile", "src", "main", "res", "mipmap-anydpi", "ic_launcher.xml")

COLOR_NAMES = {"#6C5EB5": "@color/c64_light_blue", "#352879": "@color/c64_blue"}
STORE_PX = 512
STORE_KEYLINE = 0.75
LEGACY_INSET = 18


def read_master():
    root = ET.parse(MASTER).getroot()
    viewport = float(root.get("viewBox").split()[2])
    rects = {}
    for rect in root.iter(SVG_NS + "rect"):
        rects[rect.get("id")] = (
            float(rect.get("x")), float(rect.get("y")),
            float(rect.get("width")), float(rect.get("height")),
            rect.get("fill").upper(),
        )
    return viewport, rects


def artwork_rects(rects):
    return [rects[name] for name in ("screen", "cell1", "cell2", "cell3", "cursor")]


def number(value):
    return str(int(value)) if value == int(value) else repr(value)


def path_data(x, y, w, h):
    return "M%s,%sh%sv%sh-%sz" % (
        number(x), number(y), number(w), number(h), number(w))


def vector(width_dp, viewport, paths):
    lines = [
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
        '    android:width="%ddp"' % width_dp,
        '    android:height="%ddp"' % width_dp,
        '    android:viewportWidth="%s"' % number(viewport),
        '    android:viewportHeight="%s">' % number(viewport),
    ]
    for fill, extra, data in paths:
        lines.append("  <path")
        lines.append('      android:fillColor="%s"' % fill)
        for name, value in extra:
            lines.append('      android:%s="%s"' % (name, value))
        lines.append('      android:pathData="%s" />' % data)
    lines.append("</vector>")
    return "\n".join(lines) + "\n"


def write(path, text):
    io.open(path, "w", encoding="utf-8", newline="\n").write(text)
    print("wrote %s" % os.path.relpath(path, ROOT).replace(os.sep, "/"))


def write_foreground(viewport, rects):
    paths = [(COLOR_NAMES[fill], (), path_data(x, y, w, h))
             for x, y, w, h, fill in artwork_rects(rects)]
    write(FOREGROUND, vector(int(viewport), viewport, paths))


def write_monochrome(viewport, rects):
    data = "".join(path_data(x, y, w, h)
                   for x, y, w, h, _ in artwork_rects(rects))
    write(MONOCHROME,
          vector(int(viewport), viewport,
                 [("#FFFFFF", (("fillType", "evenOdd"),), data)]))


def write_legacy(viewport, rects):
    inner = viewport - 2 * LEGACY_INSET
    paths = [(COLOR_NAMES[rects["background"][4]], (),
              path_data(LEGACY_INSET, LEGACY_INSET, inner, inner))]
    paths += [(COLOR_NAMES[fill], (), path_data(x, y, w, h))
              for x, y, w, h, fill in artwork_rects(rects)]
    write(LEGACY, vector(int(inner), viewport, paths))


def write_store_icon(viewport, rects):
    scale = STORE_PX / viewport
    centre = viewport / 2
    keyline = viewport * STORE_KEYLINE / rects["screen"][2]

    def snap(value):
        return int(round((centre + (value - centre) * keyline) * scale))

    lines = ['<svg xmlns="http://www.w3.org/2000/svg" width="%d" height="%d" '
             'viewBox="0 0 %d %d">' % ((STORE_PX,) * 4),
             '<rect x="0" y="0" width="%d" height="%d" fill="%s"/>'
             % (STORE_PX, STORE_PX, rects["background"][4])]
    for x, y, w, h, fill in artwork_rects(rects):
        left, top = snap(x), snap(y)
        lines.append('<rect x="%d" y="%d" width="%d" height="%d" fill="%s"/>'
                     % (left, top, snap(x + w) - left, snap(y + h) - top, fill))
    lines.append("</svg>")

    scratch = os.path.join(ARTWORK, "_store_icon.svg")
    io.open(scratch, "w", encoding="utf-8", newline="\n").write("\n".join(lines) + "\n")
    try:
        subprocess.run(["magick", "-background", "none",
                        "-size", "%dx%d" % (STORE_PX, STORE_PX),
                        scratch, "PNG32:" + STORE_PNG], check=True)
    finally:
        os.remove(scratch)
    print("wrote %s" % os.path.relpath(STORE_PNG, ROOT).replace(os.sep, "/"))


def main():
    viewport, rects = read_master()
    missing = {"background", "screen", "cell1", "cell2", "cell3", "cursor"} - set(rects)
    if missing:
        sys.exit("%s is missing the rects: %s" % (MASTER, ", ".join(sorted(missing))))
    write_foreground(viewport, rects)
    write_monochrome(viewport, rects)
    write_legacy(viewport, rects)
    write_store_icon(viewport, rects)


main()
