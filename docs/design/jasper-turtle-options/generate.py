"""Generate editable, self-contained SVG mascot studies. No raster dependencies."""
from pathlib import Path
import subprocess
import xml.etree.ElementTree as ET

OUT = Path(__file__).resolve().parent
INK = '/Applications/Inkscape.app/Contents/MacOS/inkscape'
INK_COLOR = '#332f27'

def path(d, fill='none', stroke=INK_COLOR, width=10):
    return f'<path d="{d}" fill="{fill}" stroke="{stroke}" stroke-width="{width}" stroke-linecap="round" stroke-linejoin="round"/>'

def ellipse(x,y,rx,ry,fill,stroke=INK_COLOR,width=10):
    return f'<ellipse cx="{x}" cy="{y}" rx="{rx}" ry="{ry}" fill="{fill}" stroke="{stroke}" stroke-width="{width}"/>'

def rect(x,y,w,h,r,fill,stroke=INK_COLOR,width=10):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" stroke="{stroke}" stroke-width="{width}"/>'

def head():
    return '<g id="face">'+path('M280 539 C281 480 243 440 228 392 C206 323 230 259 293 243 C356 226 409 269 412 333 C416 398 361 425 360 479 L379 565 Z','#a7ae70')+path('M257 314 C270 278 304 265 335 273',stroke='#c8cc93',width=18)+ellipse(267,337,31,42,'#f7efd7')+ellipse(355,330,34,46,'#f7efd7')+ellipse(278,343,13,24,INK_COLOR,'none')+ellipse(363,337,15,27,INK_COLOR,'none')+ellipse(280,332,5,8,'#fff','none')+ellipse(366,325,6,9,'#fff','none')+ellipse(263,340,47,56,'none',width=9)+ellipse(356,333,51,60,'none',width=9)+path('M310 329 Q321 318 307 327',width=8)+path('M406 321 L416 317',width=8)+path('M254 405 Q302 435 352 396',width=8)+path('M346 391 Q358 390 364 401',width=6)+ellipse(237,381,5,4,INK_COLOR,'none')+'</g>'

def explorer():
    s='<g id="jasper-explorer">'
    s+=path('M753 610 Q826 603 842 565 Q851 622 796 644 Z','#929a5d')
    for x,y in [(403,629),(716,631),(332,662),(652,666)]:
        s+=path(f'M{x-39} {y-51} Q{x-58} {y+1} {x-45} {y+37} Q{x} {y+61} {x+52} {y+34} L{x+42} {y-38} Z','#929a5d')
        s+=path(f'M{x-23} {y+28} V{y+40} M{x} {y+30} V{y+44} M{x+23} {y+26} V{y+40}',stroke='#d8ce9c',width=9)
    s+=path('M323 573 C326 407 419 329 564 331 C719 329 793 438 793 580 Q785 645 577 656 Q395 658 323 610 Z','#d8c49c')
    s+=path('M358 522 Q376 380 553 366 Q719 357 757 526 L752 580 Q589 633 357 590 Z','#eee0bd',width=9)
    s+=path('M521 431 L655 451 L714 535 L663 572 L502 578 L420 499 Z','#ddcba5',stroke='#8e7b5f',width=7)
    s+=path('M550 366 L521 431 M693 405 L655 451 M367 484 L420 499 M757 526 L714 535 M495 609 L502 578 M681 598 L663 572',stroke='#8e7b5f',width=7)
    s+=head()+path('M287 520 Q265 593 291 642 Q330 670 392 627 L377 558','#a7ae70')
    s+=path('M304 608 Q325 621 345 605',stroke='#7f894e',width=8)
    return s+'</g>'

def portrait():
    s='<g id="jasper-portrait">'
    s+=path('M263 675 C240 512 324 390 512 388 C700 390 784 512 761 675 Q517 758 263 675 Z','#d8c49c')
    s+=path('M277 558 L352 581 M354 441 L403 493 M670 441 L621 493 M747 558 L672 581',stroke='#8e7b5f',width=9)
    s+=path('M412 471 L404 545 L620 545 L612 471','#a7ae70')
    s+=path('M344 336 C344 241 413 214 512 214 C611 214 680 241 680 336 C680 423 613 483 512 483 C411 483 344 423 344 336 Z','#a7ae70')
    s+=path('M380 280 Q424 238 487 247',stroke='#c9ce93',width=20)
    for x in (432,592):
        s+=ellipse(x,332,43,57,'#f7efd7')+ellipse(x+5,340,18,30,INK_COLOR,'none')+ellipse(x+10,325,7,10,'#fff','none')+ellipse(x,334,68,72,'none',width=11)
    s+=path('M500 323 Q512 314 524 323',width=11)+path('M364 315 L347 307 M660 315 L677 307',width=10)
    s+=path('M451 421 Q512 464 573 421',width=9)+ellipse(481,398,5,4,INK_COLOR,'none')+ellipse(544,398,5,4,INK_COLOR,'none')
    s+=path('M411 514 Q512 545 613 514 Q667 582 635 684 Q512 740 389 684 Q357 582 411 514 Z','#eee0bd')
    s+=path('M512 542 V709 M387 596 Q512 627 637 596 M384 650 Q512 682 640 650',stroke='#ab9670',width=7)
    s+=ellipse(348,598,43,57,'#a7ae70')+ellipse(676,598,43,57,'#a7ae70')
    s+=path('M335 580 L352 586 M334 601 L351 607 M671 586 L688 580 M672 607 L689 601',stroke='#7f894e',width=6)
    return s+'</g>'

def compact():
    s='<g id="jasper-pocket">'
    s+=path('M716 628 L796 585 Q800 642 744 659 Z','#929a5d')
    s+=rect(334,613,89,82,31,'#929a5d')+rect(629,613,89,82,31,'#929a5d')
    s+=path('M337 581 C337 424 435 350 568 350 C701 350 767 447 767 591 L751 644 L359 644 Z','#d8c49c')
    s+=path('M543 417 L654 440 L704 528 L647 593 L500 585 L445 500 Z','#e6d4af',stroke='#8e7b5f',width=9)
    s+=path('M571 352 L543 417 M692 392 L654 440 M360 460 L445 500 M763 526 L704 528 M692 644 L647 593 M460 644 L500 585',stroke='#8e7b5f',width=9)
    s+=path('M285 582 L284 464 C241 453 216 416 216 369 C216 295 258 266 319 266 C382 266 419 309 413 370 Q408 428 363 448 L377 578 Z','#a7ae70')
    s+=ellipse(270,355,32,46,'#f7efd7')+ellipse(360,355,34,46,'#f7efd7')
    for x in (270,360):
        s+=ellipse(x+6,361,13,24,INK_COLOR,'none')+ellipse(x+10,351,5,7,'#fff','none')+ellipse(x,358,45,54,'none',width=10)
    s+=path('M315 348 Q322 340 315 348',width=10)+path('M274 421 Q315 445 355 414',width=8)
    s+=path('M286 551 Q264 603 286 657 Q331 687 378 655 L373 588','#a7ae70')
    return s+'</g>'

def svg(body,title):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024"><title>{title}</title><desc>Original Jasper turtle mascot study. Editable vector shapes; no embedded images, fonts, filters, or external resources.</desc>{body}</svg>'

names=['01-explorer','02-desk-buddy','03-pocket']
art=[explorer(),portrait(),compact()]
cells=[]
for i,(name,drawing) in enumerate(zip(names,art)):
    for platform in ('macos','windows'):
        if platform=='macos':
            body=rect(104,113,816,816,182,'#d1c7b5','none')+rect(104,96,816,816,182,'#f4ecd9','none')+path('M170 227 Q191 127 299 127 H718',stroke='#fffaf0',width=8)
            body+='<g transform="translate(-10 -22) scale(1.02)">'+drawing+'</g>'
        else:
            body='<g transform="translate(-111 -162) scale(1.23)">'+drawing+'</g>'
        target=OUT/f'{name}-{platform}.svg'
        target.write_text(svg(body,f'Jasper / {name} / {platform}'))
        ET.parse(target)
        subprocess.run([INK,str(target),'--export-type=png',f'--export-filename={target.with_suffix(".png")}','--export-width=512'],check=True,capture_output=True)
        col=i; row=0 if platform=='macos' else 1
        cells.append(f'<g transform="translate({col*400} {row*470+65})"><svg width="400" height="400" viewBox="0 0 1024 1024">{body}</svg><text x="200" y="425" text-anchor="middle" font-family="sans-serif" font-size="19" fill="#332f27">{name[3:].replace("-"," ").title()} · {platform}</text></g>')
sheet='<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="1020" viewBox="0 0 1200 1020"><rect width="1200" height="1020" fill="#e5e4df"/><text x="600" y="45" text-anchor="middle" font-family="sans-serif" font-size="28" fill="#332f27">Jasper — editable vector icon studies</text>'+''.join(cells)+'</svg>'
(OUT/'comparison.svg').write_text(sheet)
subprocess.run([INK,str(OUT/'comparison.svg'),'--export-type=png',f'--export-filename={OUT / "comparison.png"}'],check=True,capture_output=True)
print('Created and rendered six standalone SVGs and comparison sheet in',OUT)
