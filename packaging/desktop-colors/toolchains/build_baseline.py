#!/usr/bin/env python3
"""Prepare pinned private SDKs only when --build is explicitly requested on Linux."""
import hashlib
import json
import os
import pathlib
import platform
import shutil
import subprocess
import sys
import tarfile
import urllib.request
import tomllib
import tempfile

HERE = pathlib.Path(__file__).resolve().parent


def verify_archive(archive, expected):
    with archive.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").hexdigest()
    if digest != expected:
        raise ValueError(f"SHA256 mismatch for {archive.name}: expected {expected}, got {digest}")


def plan(target, root, manifest):
    qt = "qt5" if target.endswith("5") else "kde6-qt" if target == "kde6" else "qt6"
    keys = [qt]
    if target.startswith("kde"):
        keys += manifest["groups"]["kf" + target[-1]]
    result = []
    for key in keys:
        source = manifest["sources"][key]
        prefix = root / "prefixes" / (qt if key == qt else "kf" + target[-1])
        paths = [root / "prefixes" / qt]
        if target.startswith("kde"):
            paths.insert(0, root / "prefixes" / ("kf" + target[-1]))
        result.append(dict(source, key=key, prefix=str(prefix), qt_prefix=str(root / "prefixes" / qt),
            build=str(root / "build" / qt / key),
            cmake_prefix_path=";".join(map(str, paths)),
            patches=[manifest['patches'][patch] for patch in source.get('patches', [])]))
    return result


def step_environment(step, inherited):
    env = dict(inherited)
    for key in ("CMAKE_PREFIX_PATH", "Qt5_DIR", "Qt6_DIR", "QTDIR", "QT_PLUGIN_PATH",
                "QT_QPA_PLATFORM_PLUGIN_PATH", "LD_LIBRARY_PATH"):
        env.pop(key, None)
    env['PATH'] = str(pathlib.Path(step['qt_prefix']) / 'bin') + os.pathsep + inherited.get('PATH', '')
    return env


def check_tools(steps, search_path=None):
    required = ['cmake', 'ninja', 'cc', 'c++', 'pkg-config', 'make', 'perl', 'patch']
    required += [tool for step in steps for tool in step.get('build_tools', [])]
    for command in dict.fromkeys(required):
        if not shutil.which(command, path=search_path):
            raise ValueError(f"missing build dependency: {command}")


def check_pkg_config(steps, env):
    for requirement in dict.fromkeys(module for step in steps for module in step.get('pkg_config', [])):
        result = subprocess.run(['pkg-config', '--exists', requirement], env=env,
                                capture_output=True, text=True)
        if result.returncode:
            raise ValueError(f"missing build dependency: pkg-config {requirement}")


def cmake_options(step):
    options = ["-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_INSTALL_LIBDIR=lib",
        "-DCMAKE_INSTALL_PREFIX=" + step['prefix'],
        "-DCMAKE_PREFIX_PATH=" + step['cmake_prefix_path'],
        "-DCMAKE_FIND_USE_PACKAGE_REGISTRY=OFF", "-DCMAKE_FIND_USE_SYSTEM_PACKAGE_REGISTRY=OFF"]
    if step['name'] == 'qtbase':
        options += ["-DQT_BUILD_TESTS=OFF", "-DQT_BUILD_EXAMPLES=OFF",
            "-DFEATURE_xcb=ON", "-DFEATURE_opengl=OFF", "-DINPUT_opengl=no", "-DFEATURE_dbus=ON"]
    else:
        options += ["-DBUILD_TESTING=OFF", "-DBUILD_QCH=OFF", "-DBUILD_DOC=OFF",
            "-DQT_MAJOR_VERSION=" + ("5" if step['version'].startswith('5') else "6")]
    return options + step.get('cmake_options', [])


def checked_path(root, relative):
    path = (root / relative).resolve()
    if not path.is_relative_to(root.resolve()):
        raise ValueError(f"patch path escapes source: {relative}")
    return path


def apply_patches(source, patches, patch_root=HERE):
    for spec in patches:
        patch = checked_path(patch_root, spec['file'])
        verify_archive(patch, spec['sha256'])
        files = spec['files']
        current = {file['path']: checked_path(source, file['path']).read_bytes() for file in files}
        digests = {name: hashlib.sha256(data).hexdigest() for name, data in current.items()}
        if all(digests[file['path']] == file['after'] for file in files):
            continue
        if not all(digests[file['path']] == file['before'] for file in files):
            raise ValueError(f"source precondition failed for {spec['file']}; expected original or fully patched files")
        # Apply to an isolated copy; rejected hunks/result hashes never mutate SDK sources.
        with tempfile.TemporaryDirectory(prefix='jasper-sdk-patch-') as directory:
            stage = pathlib.Path(directory)
            for name, data in current.items():
                target = checked_path(stage, name)
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
            result = subprocess.run(['patch', '-p1', '-F', '0', '-f', '-i', str(patch)],
                                    cwd=stage, capture_output=True, text=True)
            if result.returncode:
                raise ValueError(f"patch failed for {spec['file']}: {result.stdout}{result.stderr}")
            for file in files:
                data = checked_path(stage, file['path']).read_bytes()
                if hashlib.sha256(data).hexdigest() != file['after']:
                    raise ValueError(f"patched result SHA256 mismatch for {file['path']}")
            for file in files:
                checked_path(source, file['path']).write_bytes(checked_path(stage, file['path']).read_bytes())


def run(command, log, env, cwd=None):
    log.write("$ " + json.dumps(list(map(str, command))) + "\n")
    log.flush()
    subprocess.run(list(map(str, command)), check=True, env=env, cwd=cwd,
                   stdout=log, stderr=subprocess.STDOUT)


def build(steps, root, jobs):
    if platform.system() != "Linux":
        raise ValueError("--build requires Linux; run in the authorized baseline userspace")
    check_tools(steps)
    check_pkg_config(steps, step_environment(steps[0], os.environ))
    root.mkdir(parents=True, exist_ok=True)
    for directory in ("downloads", "src", "logs"):
        (root / directory).mkdir(exist_ok=True)
    for step in steps:
        archive = root / "downloads" / step["url"].rsplit("/", 1)[1]
        if not archive.exists():
            partial = archive.with_suffix(archive.suffix + ".part")
            urllib.request.urlretrieve(step["url"], partial)
            verify_archive(partial, step["sha256"])
            partial.replace(archive)
        verify_archive(archive, step["sha256"])
        source = root / "src" / step["source_dir"]
        if not source.exists():
            with tarfile.open(archive) as tar:
                # Reject traversal and unsafe links before extracting upstream sources.
                tar.extractall(root / "src", filter="data")
        apply_patches(source, step.get('patches', []))
        build_dir = pathlib.Path(step["build"])
        build_dir.mkdir(parents=True, exist_ok=True)
        prefix = pathlib.Path(step["prefix"])
        env = step_environment(step, os.environ)
        log_path = root / "logs" / (step["key"] + ".log")
        print(f"desktop-colors: preparing {step['key']}; log={log_path}", flush=True)
        with log_path.open("w") as log:
            log.write(json.dumps(step, indent=2) + "\n")
            for command in (["uname", "-a"], ["cmake", "--version"], ["c++", "--version"],
                            ["ldd", "--version"]):
                run(command, log, env)
            if step["key"] == "qt5":
                run([source / "configure", "-prefix", prefix, "-opensource", "-confirm-license",
                     "-release", "-nomake", "tests", "-nomake", "examples", "-no-opengl",
                     "-xcb", "-dbus-linked", "-no-icu"] + step.get('configure_options', []), log, env, cwd=build_dir)
                run(["make", "-C", build_dir, f"-j{jobs}"], log, env)
                run(["make", "-C", build_dir, "install"], log, env)
            else:
                run(["cmake", "-S", source, "-B", build_dir, "-G", "Ninja"] + cmake_options(step), log, env)
                run(["cmake", "--build", build_dir, "--parallel", str(jobs)], log, env)
                run(["cmake", "--install", build_dir], log, env)
    (root / "logs" / "resolved-plan.json").write_text(json.dumps(steps, indent=2) + "\n")


def main():
    if len(sys.argv) != 4 or sys.argv[1] not in ("--plan", "--build"):
        raise ValueError("usage: build-baseline.sh --plan|--build qt5|qt6|kde5|kde6 /absolute/work-area")
    mode, target, directory = sys.argv[1:]
    if target not in ("qt5", "qt6", "kde5", "kde6"):
        raise ValueError(f"unknown target: {target}")
    root = pathlib.Path(directory)
    if not root.is_absolute() or root == pathlib.Path("/"):
        raise ValueError("work area must be an absolute dedicated directory, not /")
    manifest = tomllib.loads((HERE / "manifest.toml").read_text())
    steps = plan(target, root, manifest)
    if mode == "--plan":
        print(json.dumps(steps, indent=2))
    else:
        jobs = int(os.environ.get("DC_BUILD_JOBS", "2"))
        if not 1 <= jobs <= 64:
            raise ValueError("DC_BUILD_JOBS must be in 1..64")
        build(steps, root, jobs)


if __name__ == "__main__":
    try:
        main()
    except (ValueError, OSError, subprocess.CalledProcessError) as error:
        sys.exit(f"desktop-colors: {error}")
