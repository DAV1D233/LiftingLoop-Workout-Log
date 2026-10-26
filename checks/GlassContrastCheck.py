"""Check readable glass text cores against worst-case black/white backdrops, not screenshots."""
import pathlib
import re

root = pathlib.Path(__file__).resolve().parents[1]
theme = (root / "app/src/main/java/com/fitlog/app/ui/theme/Theme.kt").read_text(encoding="utf-8-sig")
style = (root / "app/src/main/java/com/fitlog/app/ui/glass/GlassStyle.kt").read_text(encoding="utf-8-sig")
colors = {name: tuple(int(rgb[i:i+2], 16) / 255 for i in (0, 2, 4))
          for name, rgb in re.findall(r"val (\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)", theme)}
colors["Paper"] = (1, 1, 1)
colors["White"] = (1, 1, 1)
alphas = [float(a) for a in re.findall(r"(?:LIGHT|STANDARD|EMPHASIZED)\((\.[0-9]+)f", style)]

def mix(fg, bg, a):
    return tuple(x*a + y*(1-a) for x, y in zip(fg, bg))

def luminance(rgb):
    linear = [x/12.92 if x <= .04045 else ((x+.055)/1.055)**2.4 for x in rgb]
    return sum(x*w for x, w in zip(linear, (.2126, .7152, .0722)))

text_core_alpha = float(re.search(r"GlassTextCoreAlpha = (\.[0-9]+)f", style).group(1))
checks = 0
lowest = 100
for bg, fg, dark in [("Paper", "Ink", False), ("Paper", "Muted", False),
                     ("GreenTint", "Green", False), ("OrangeTint", "Orange", False),
                     ("OrangeTint", "Ink", False), ("GreenTint", "Ink", False),
                     ("NightSurface", "NightText", True), ("NightSurface", "NightMuted", True),
                     ("NightSurface", "Lime", True), ("RestSurface", "RestText", True), ("Lime", "Night", False),
                     ("Green", "White", True), ("Orange", "White", True)]:
    for alpha in alphas:
        for backdrop in ((0, 0, 0), (1, 1, 1)):
            core = mix(colors[bg], mix(colors[bg], backdrop, alpha), text_core_alpha)
            # Check both unlit and maximum press-highlight portions of the text core.
            for highlight in (0, .04 if dark else .16):
                surface = mix((1, 1, 1), core, highlight)
                a, b = sorted((luminance(surface), luminance(colors[fg])))
                contrast = (b+.05)/(a+.05)
                assert contrast >= 4.5, (bg, fg, alpha, highlight, contrast)
                lowest = min(lowest, contrast)
                checks += 1
assert len(alphas) == 3
print(f"Glass text core contrast: {checks} cases passed; minimum {lowest:.2f}:1")

# Test the new clear Dock at the corners of a 28dp icon enlarged to 1.18x,
# including the worst-case edge of its radial contrast-protection core.
dock_alpha = float(re.search(r"DOCK\((\.[0-9]+)f", style).group(1))
core_alpha = float(re.search(r"DockIconCoreAlpha = (\.[0-9]+)f", style).group(1))
core_radius = float(re.search(r"DockIconCoreRadius = (\.[0-9]+)f", style).group(1))
dock_lowest = 100
dock_checks = 0
dock_source = (root / "app/src/main/java/com/fitlog/app/ui/home/HomeDock.kt").read_text(encoding="utf-8-sig")
colors.update({name: tuple(int(rgb[i:i+2], 16) / 255 for i in (0, 2, 4))
               for name, rgb in re.findall(r"val (Dock\w+) = Color\(0xFF([0-9A-Fa-f]{6})\)", dock_source)})
for bg, fg in [("DockGreenTint", "DockGreenInk"), ("DockOrangeTint", "DockOrangeInk")]:
    for scale in (1, 1.18, 1.18/.96):
        for radius in (0, 14 * scale, 14 * scale * 2**.5):
            t = radius / (64 * .48)
            protection = core_alpha if t <= core_radius else core_alpha * (1-t) / (1-core_radius)
            for shade in (0, .25, .5, .75, 1):
                core = mix(colors[bg], mix(colors[bg], (shade,)*3, dock_alpha), protection)
                for highlight in (0, .16):
                    surface = mix((1, 1, 1), core, highlight)
                    a, b = sorted((luminance(surface), luminance(colors[fg])))
                    contrast = (b+.05)/(a+.05)
                    assert contrast >= 4.5, ("Dock", bg, fg, scale, radius, contrast)
                    dock_lowest = min(dock_lowest, contrast)
                    dock_checks += 1
print(f"Dock icon core contrast: {dock_checks} cases passed; minimum {dock_lowest:.2f}:1")

# Scrolling card material is intentionally lightweight, without any live backdrop shader.
card_alpha = float(re.search(r"GlassCardAlpha = (\.[0-9]+)f", style).group(1))
card_checks = 0
card_minimum = 100
for bg, fg in [("CardFill", "Ink"), ("CardFill", "Muted"), ("CardFill", "Green"), ("CardFill", "Orange")]:
    for shade in (0, .25, .5, .75, 1):
        surface = mix(colors[bg], (shade,)*3, card_alpha)
        a, b = sorted((luminance(surface), luminance(colors[fg])))
        contrast = (b+.05)/(a+.05)
        assert contrast >= 4.5, ("Card", bg, fg, shade, contrast)
        card_checks += 1
        card_minimum = min(card_minimum, contrast)
print(f"Glass card contrast: {card_checks} cases passed; minimum {card_minimum:.2f}:1")

# Global button material: check its text protection across extreme backgrounds and press gloss.
control_checks = 0
control_minimum = 100
light_alpha = float(re.search(r"GlassControlLightAlpha = (\.[0-9]+)f", style).group(1))
dark_alpha = float(re.search(r"GlassControlDarkAlpha = (\.[0-9]+)f", style).group(1))
for bg, fg, dark in [("Paper", "Ink", False), ("Paper", "Green", False), ("Paper", "Muted", False),
                     ("GreenTint", "Green", False), ("OrangeTint", "Orange", False),
                     ("FieldFill", "Muted", False), ("NightSurface", "Lime", True),
                     ("NightSurface", "NightText", True), ("RestSurface", "RestText", True)]:
    for shade in (0, .25, .5, .75, 1):
        for gloss in ((.025, .04) if dark else (.08, .16)):
            surface = mix(colors[bg], (shade,)*3, dark_alpha if dark else light_alpha)
            surface = mix((1, 1, 1), surface, gloss)
            a, b = sorted((luminance(surface), luminance(colors[fg])))
            contrast = (b+.05)/(a+.05)
            assert contrast >= 4.5, ("Control", bg, fg, shade, gloss, contrast)
            control_checks += 1
            control_minimum = min(control_minimum, contrast)
print(f"Liquid button contrast: {control_checks} cases passed; minimum {control_minimum:.2f}:1")
