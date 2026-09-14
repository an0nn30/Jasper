"""Make background-only Desk Buddy variants from the existing editable SVG."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parent
OUT = ROOT / 'motif-colors'
OUT.mkdir(exist_ok=True)
INK = '/Applications/Inkscape.app/Contents/MacOS/inkscape'
ET.register_namespace('', 'http://www.w3.org/2000/svg')
source = ET.parse(ROOT / '02-desk-buddy-macos.svg').getroot()
mascot = ET.tostring(source.find('{http://www.w3.org/2000/svg}g'), encoding='unicode')
colors = [
    ('01-motif-gray', 'Motif gray', '#aeb2c3', 'control'),
    ('02-silver', 'Silver', '#dcdee5', 'controlHighlight'),
    ('03-graphite', 'Graphite', '#63656f', 'controlShadow'),
    ('04-desktop-teal', 'Desktop teal', '#005c5c', 'desktop'),
    ('05-caption-navy', 'Caption navy', '#000080', 'activeCaption'),
    ('06-soft-slate', 'Soft slate', '#7c8296', 'derived variation'),
]
cells = []
for i, (slug, label, color, role) in enumerate(colors):
    tile = f'<g id="background"><rect x="104" y="113" width="816" height="816" rx="182" fill="{color}"/><rect x="104" y="113" width="816" height="816" rx="182" fill="#000000" opacity="0.22"/><rect x="104" y="96" width="816" height="816" rx="182" fill="{color}"/><path d="M170 227 Q191 127 299 127 H718" fill="none" stroke="#ffffff" stroke-opacity="0.32" stroke-width="8" stroke-linecap="round"/></g>'
    body = tile + mascot
    doc = f'<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024"><title>Jasper Desk Buddy — {label}</title><desc>Background {color}, Motif role: {role}. Editable vector paths. Turtle unchanged from approved Desk Buddy study.</desc>{body}</svg>'
    target = OUT / f'{slug}.svg'
    target.write_text(doc)
    parsed = ET.parse(target).getroot()
    assert parsed.find('{http://www.w3.org/2000/svg}g[@id="jasper-portrait"]') is None
    assert ET.tostring(parsed.findall('{http://www.w3.org/2000/svg}g')[1], encoding='unicode') == mascot
    subprocess.run([INK, str(target), '--export-type=png', f'--export-filename={target.with_suffix(".png")}', '--export-width=512'], check=True, capture_output=True)
    x,y=(i%3)*400,(i//3)*475+60
    cells.append(f'<g transform="translate({x} {y})"><svg width="400" height="400" viewBox="0 0 1024 1024">{body}</svg><text x="200" y="420" text-anchor="middle" font-family="sans-serif" font-size="21" fill="#252932">{i+1}. {label}</text><text x="200" y="448" text-anchor="middle" font-family="monospace" font-size="16" fill="#525866">{color} · {role}</text></g>')
sheet='<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="1030" viewBox="0 0 1200 1030"><rect width="1200" height="1030" fill="#f0f1f4"/><text x="600" y="43" text-anchor="middle" font-family="sans-serif" font-size="28" fill="#252932">Jasper — Motif background colors</text>'+''.join(cells)+'</svg>'
(OUT/'comparison.svg').write_text(sheet)
subprocess.run([INK, str(OUT/'comparison.svg'), '--export-type=png', f'--export-filename={OUT / "comparison.png"}'], check=True, capture_output=True)
with zipfile.ZipFile(OUT/'jasper-motif-colors.zip', 'w', zipfile.ZIP_DEFLATED) as archive:
    for slug, *_ in colors:
        archive.write(OUT/f'{slug}.svg', f'{slug}.svg')
print('Rendered six SVG variants; verified unchanged mascot shapes; packaged SVG ZIP.')
