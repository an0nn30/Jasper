#!/usr/bin/env python3
"""Run the Windows check with bounded JVM snapshots before the job deadline."""
import argparse
import os
import signal
from pathlib import Path
import subprocess
import sys
import time


def capture(directory, label):
    path = directory / (label + '.txt')
    with path.open('w', encoding='utf-8') as output:
        try:
            listing = subprocess.run(['jcmd', '-l'], capture_output=True, text=True, timeout=5)
            output.write('jcmd -l:\n' + listing.stdout + listing.stderr + '\n')
            # Keep collection bounded even if a runner has unexpected extra JVMs.
            for line in listing.stdout.splitlines()[:8]:
                pid = line.split(maxsplit=1)[0]
                if not pid.isdigit() or 'sun.tools.jcmd.JCmd' in line:
                    continue
                output.write('\n--- PID ' + pid + ' ---\n')
                output.flush()
                try:
                    dump = subprocess.run(['jcmd', pid, 'Thread.print', '-l'],
                                          capture_output=True, text=True, timeout=5)
                    output.write(dump.stdout + dump.stderr)
                except (OSError, subprocess.TimeoutExpired) as error:
                    output.write('dump failed: ' + str(error) + '\n')
        except (OSError, subprocess.TimeoutExpired) as error:
            output.write('jcmd listing failed: ' + str(error) + '\n')
    print('JVM snapshot:', path, flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--timeout', type=float, required=True)
    parser.add_argument('--capture-at', type=float, action='append', default=[])
    parser.add_argument('--diagnostics', type=Path, required=True)
    parser.add_argument('--success-marker', type=Path)
    parser.add_argument('--windows-batch', action='store_true')
    parser.add_argument('command', nargs=argparse.REMAINDER)
    args = parser.parse_args()
    command = args.command[1:] if args.command[:1] == ['--'] else args.command
    if not command or args.timeout <= 0:
        parser.error('a command and positive timeout are required')
    args.diagnostics.mkdir(parents=True, exist_ok=True)
    env = os.environ.copy()
    if args.success_marker:
        marker = args.success_marker.resolve()
        marker.unlink(missing_ok=True)
        env['JASPER_CI_SUCCESS_MARKER'] = str(marker)
    if args.windows_batch:
        if os.name != 'nt':
            parser.error('--windows-batch requires Windows')
        command = [os.environ.get('COMSPEC', 'cmd.exe'), '/c', *command]
    process = subprocess.Popen(command, env=env, start_new_session=os.name != 'nt')
    start = time.monotonic()
    captures = iter(sorted(t for t in args.capture_at if 0 < t < args.timeout))
    next_capture = next(captures, args.timeout)
    while True:
        try:
            result = process.wait(timeout=max(0.01, start + next_capture - time.monotonic()))
            if result == 0 and args.success_marker:
                try:
                    valid_marker = marker.read_bytes() == b'Gradle build completed successfully\n'
                except OSError:
                    valid_marker = False
                if not valid_marker:
                    print('Gradle completion marker missing or invalid; check did not complete', flush=True)
                    return 1
            return result
        except subprocess.TimeoutExpired:
            if next_capture == args.timeout:
                print('Check timed out; collecting JVM diagnostics', flush=True)
                capture(args.diagnostics, 'timeout')
                if os.name == 'nt':
                    subprocess.run(['taskkill', '/PID', str(process.pid), '/T', '/F'],
                                   timeout=10, check=False)
                else:
                    os.killpg(process.pid, signal.SIGKILL)
                process.wait(timeout=10)
                return 124
            capture(args.diagnostics, 'at-' + str(int(next_capture)))
            next_capture = next(captures, args.timeout)


if __name__ == '__main__':
    sys.exit(main())
