#!/usr/bin/env python3
"""Runs the interleaved benchmarks on Windows, then on WSL, then evaluates them.

The two platforms run strictly one after the other (WSL2 shares the CPU with Windows). The
results go to ``results/<name>/``: ``windows.csv``/``windows.log``, ``wsl.csv``/``wsl.log``, the
environment of each platform (``*-env.txt``), and the report of ``analyze.py`` (``report.md``
and plots).

Usage (on Windows, after ``mvnw -B package``)::

    python scripts/run_bench.py                         # full run, name = timestamp
    python scripts/run_bench.py --name smoke -- --rounds=1 --sizes=1000 --wi=1 --w=200 --i=1 --r=200
    python scripts/run_bench.py --platforms=windows --out=results/x -- --rounds=3 --impls=stencil,fastutil

Arguments after ``--`` go to ``net.collections.stencil.bench.InterleavedRunner`` on both
platforms. The Windows JDK comes from JAVA_HOME (or --java-home; JDK 25 or later). The WSL run
uses ``scripts/run-bench-wsl.sh`` with the Linux JDK of the distribution (override there with
JAVA=/path/to/bin/java). ``analyze.py`` needs pandas and matplotlib in the Python that runs it
(default: the Python running this script).
"""

import argparse
import datetime
import os
import platform
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCRIPTS = ROOT / 'scripts'
JAR = ROOT / 'bench' / 'target' / 'benchmarks.jar'
RUNNER = 'net.collections.stencil.bench.InterleavedRunner'

# --add-opens lets the footprint measurement walk the nodes of java.util.TreeMap and of the
# concurrent maps (nodes of ConcurrentHashMap, locks of the segments).
JVM_ARGS = [
    '-Duser.language=en', '-Duser.country=US', '--sun-misc-unsafe-memory-access=allow',
    '--add-opens=java.base/java.util=ALL-UNNAMED',
    '--add-opens=java.base/java.util.concurrent=ALL-UNNAMED',
    '--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED',
    '--add-opens=java.base/java.util.concurrent.atomic=ALL-UNNAMED',
]


def warn(message):
    print('warning: ' + message, file=sys.stderr, flush=True)


def now():
    return datetime.datetime.now().isoformat(timespec='seconds')


def cpu_name():
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_LOCAL_MACHINE,
                            r'HARDWARE\DESCRIPTION\System\CentralProcessor\0') as key:
            return winreg.QueryValueEx(key, 'ProcessorNameString')[0].strip()
    except OSError:
        return platform.processor()


def power_scheme():
    try:
        result = subprocess.run(['powercfg', '/getactivescheme'], capture_output=True, text=True)
        return ' '.join(result.stdout.split())
    except OSError:
        return 'unknown'


def run_windows(out, java_home, runner_args):
    """Runs the benchmarks with the Windows JDK; returns the exit code of the runner."""
    java = Path(java_home) / 'bin' / 'java.exe'
    if not java.is_file():
        warn(f'java.exe not found: {java}')
        return 1
    env_file = out / 'windows-env.txt'
    env_file.write_text(
        f'CPU:   {cpu_name()}\n'
        f'OS:    {platform.system()} {platform.release()} {platform.version()}\n'
        f'Power: {power_scheme()}\n'
        f'JDK:   {java_home}\n'
        f'Start: {now()}\n', encoding='ascii', errors='replace')
    print(f'Windows benchmark -> {out} (progress: {out / "windows.log"})', flush=True)
    # The runner writes windows.csv and windows.log itself; console output is discarded.
    cmd = [str(java)] + JVM_ARGS + ['-cp', str(JAR), RUNNER, f'--out={out}', '--label=windows'] + runner_args
    rc = subprocess.run(cmd, stdout=subprocess.DEVNULL).returncode
    with env_file.open('a', encoding='ascii') as f:
        f.write(f'End:   {now()}  exit={rc}\n')
    return rc


def wsl_path(path):
    """E:\\x\\y -> /mnt/e/x/y"""
    full = str(Path(path).resolve())
    return '/mnt/' + full[0].lower() + full[2:].replace('\\', '/')


def run_wsl(out, distro, runner_args):
    """Runs the benchmarks inside WSL (run-bench-wsl.sh); returns its exit code."""
    cmd = ['wsl.exe', '-d', distro, '--', 'bash', wsl_path(SCRIPTS / 'run-bench-wsl.sh'), wsl_path(out)]
    return subprocess.run(cmd + runner_args).returncode


def main():
    argv = sys.argv[1:]
    runner_args = []
    if '--' in argv:
        i = argv.index('--')
        argv, runner_args = argv[:i], argv[i + 1:]

    parser = argparse.ArgumentParser(
        description='Runs the interleaved benchmarks on Windows, then on WSL, then evaluates them. '
                    'Arguments after -- go to the InterleavedRunner.')
    parser.add_argument('--name', default=datetime.datetime.now().strftime('%Y%m%d-%H%M'),
                        help='run name: results/<name> (default: timestamp)')
    parser.add_argument('--out', help='output directory (overrides --name)')
    parser.add_argument('--platforms', default='windows,wsl',
                        help='comma-separated: windows, wsl (default: both)')
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'),
                        help='Windows JDK, 25 or later (default: JAVA_HOME)')
    parser.add_argument('--distro', default='Ubuntu', help='WSL distribution (default: Ubuntu)')
    parser.add_argument('--python', default=sys.executable,
                        help='Python with pandas and matplotlib for analyze.py (default: this Python)')
    parser.add_argument('--no-analyze', action='store_true', help='skip analyze.py')
    args = parser.parse_args(argv)

    platforms = [p.strip() for p in args.platforms.split(',') if p.strip()]
    unknown = set(platforms) - {'windows', 'wsl'}
    if unknown or not platforms:
        parser.error(f'unknown platform(s): {", ".join(sorted(unknown)) or "none given"}')
    if os.name != 'nt':
        parser.error('this script runs on Windows (inside WSL use scripts/run-bench-wsl.sh)')
    if 'windows' in platforms and not args.java_home:
        parser.error('no JDK: set JAVA_HOME or pass --java-home')
    if not JAR.is_file():
        parser.error(f'benchmark jar not found: {JAR} (run: mvnw -B package)')

    out = Path(args.out) if args.out else ROOT / 'results' / args.name
    out.mkdir(parents=True, exist_ok=True)
    out = out.resolve()

    if 'windows' in platforms:
        rc = run_windows(out, args.java_home, runner_args)
        if rc != 0:
            warn(f'Windows run failed (exit {rc})')
    if 'wsl' in platforms:
        rc = run_wsl(out, args.distro, runner_args)
        if rc != 0:
            warn(f'WSL run failed (exit {rc})')
    if not args.no_analyze:
        rc = subprocess.run([args.python, str(SCRIPTS / 'analyze.py'), str(out)]).returncode
        if rc != 0:
            warn(f'analyze.py failed (exit {rc})')
            sys.exit(rc)


if __name__ == '__main__':
    main()
