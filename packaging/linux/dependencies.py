#!/usr/bin/env python3
"""Mandatory image requirements; exclude only the four optional helper executable paths."""
import argparse, pathlib, re, subprocess

OPTIONAL_HELPERS = frozenset("lib/app/desktop-colors/jasper-desktop-" + name for name in ("qt5", "qt6", "kde5", "kde6"))

def output(*args):
    return subprocess.check_output(args, text=True, stderr=subprocess.STDOUT)

def elf_inputs(image):
    image = pathlib.Path(image)
    all_elf = []
    for path in image.rglob("*"):
        if path.is_file():
            with path.open("rb") as stream:
                if stream.read(4) == b"\x7fELF":
                    all_elf.append(path)
    mandatory = [p for p in all_elf if p.relative_to(image).as_posix() not in OPTIONAL_HELPERS]
    return mandatory, {p.name for p in all_elf}

def requirements(image, kind):
    mandatory, bundled = elf_inputs(image)
    needed = set()
    for binary in mandatory:
        if kind == "rpm":
            needed.update(re.findall(r"\(NEEDED\)\s+Shared library: \[([^\]]+)\]", output("readelf", "--dynamic", "--wide", str(binary))))
        else:
            # The selected JBR's lookup follows ldd's resolved absolute paths. Libraries
            # shipped in this same image are satisfied internally, even if ldd can't locate them.
            linked = subprocess.run(["ldd", str(binary)], capture_output=True, text=True)
            missing = re.findall(r"^\s*(\S+) => not found", linked.stdout, re.M)
            if linked.returncode or any(name not in bundled for name in missing):
                raise ValueError(f"unresolved mandatory image dependency: {binary}: {linked.stdout}{linked.stderr}")
            for library in re.findall(r"(?:=>\s*)?(/[^\s]+)", linked.stdout):
                path = pathlib.Path(library)
                if path.name in bundled:
                    continue
                provider = None
                for candidate in dict.fromkeys((str(path.resolve()), str(path))):
                    queried = subprocess.run(["dpkg-query", "--search", candidate], capture_output=True, text=True)
                    if queried.returncode == 0:
                        # Multiarch suffix is metadata, not part of the Debian package name.
                        provider = {line.split(": ", 1)[0].split(":", 1)[0] for line in queried.stdout.splitlines()}
                        break
                if not provider:
                    raise ValueError(f"no Debian package provides mandatory library {library} ({binary})")
                needed.update(provider)
    if kind == "rpm":
        needed = {name + "()(64bit)" for name in needed - bundled}
    # JBR DesktopIntegration's menu action requires xdg-utils in both package formats.
    return sorted(needed | {"xdg-utils"})

if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("kind", choices=("deb", "rpm"))
    parser.add_argument("image")
    args = parser.parse_args()
    print(", ".join(requirements(args.image, args.kind)))
