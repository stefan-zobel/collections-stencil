"""Evaluates interleaved benchmark runs (InterleavedRunner CSVs).

    python scripts/analyze.py results/<run>

Reads <run>/windows.csv and/or <run>/wsl.csv (plus the optional *-footprint.csv) and writes
  <run>/report.md      tables (median over rounds, paired ratios, drift diagnostics)
  <run>/plots/*.png    ns/op vs. size per op, and drift over time per platform

Statistics: every round has exactly one fork per implementation per cell
(op, dist, size), and these forks run back to back as one block. Ratios are
therefore computed *within* a block (same round, same cell) and summarized over
the rounds. Slow drift such as thermal throttling cancels out of these ratios.
"""
import math
import re
import sys
from pathlib import Path

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import pandas as pd  # noqa: E402

TREE_VARIANTS = []  # experiment variants of the sorted suite, if any
OBJECT_KEY_VARIANTS = []  # experiment variants of the objectint suite, if any
LINKED_VARIANTS = ["stencil-dense", "stencil-links"]  # the two layout experiments in run v17 of the longlinked suite
HEAP_VARIANTS = ["arity2", "arity4", "arity4-bottomup", "arity8-bottomup", "arity4-bottomup-loop", "arity8",
                 "arity16-bottomup"]  # the arity experiments of the longheap suite (runs v22a, v22b)
IMPLS = ["stencil", "stencil-lf50", "stencil-block", "stencil-block-hash", "stencil-compact", *TREE_VARIANTS, *OBJECT_KEY_VARIANTS,
         *LINKED_VARIANTS, *HEAP_VARIANTS, "fastutil", "fastutil-rb", "fastutil-avl", "hppc", "eclipse", "jctools", "speiger", "trivago", "jdk",
         "jdk-adder"]
CANDIDATES = ["stencil", "stencil-lf50", "stencil-block", "stencil-block-hash", "stencil-compact", *TREE_VARIANTS,
              *OBJECT_KEY_VARIANTS, *LINKED_VARIANTS, *HEAP_VARIANTS]
LIBS = ["fastutil", "fastutil-rb", "fastutil-avl", "hppc", "eclipse", "jctools", "speiger", "trivago"]
OPS = ["getHit", "getMiss", "addTo", "containsHit", "containsMiss", "floor", "mixed", "writeHeavy", "build", "churn",
       "computeIfAbsent", "count", "countSpread", "iterate", "rangeScan", "removeRange", "fifo", "touch", "sort", "hold",
       "dijkstra", "timers", "get", "insertRemove", "stack", "window"]  # reduced to the ops in the data
PER_ENTRY_OPS = ["build", "buildPresized", "computeIfAbsent", "iterate", "sort", "dijkstra", "stack"]  # reported per map by JMH, divided by size
SLOT_BYTES = 8  # bytes per table slot: int 8, long 16, intset 4, longset 8, intobj 8, longobj 12, objectint 8, objectintlinked 8, longheap 8, intlongindexedheap 16, longlongindexedheap 16, intlist 4, intdeque 4, intconc 8, longconc 12, intintconc 8, longlongconc 16 (see detect_suite)
SUITE = "int"
# (block maps also count their overflow counters and 8 padding slots: fill is ~1.6 % low there)
PLATFORMS = [("Windows", "windows"), ("WSL", "wsl")]
STYLE = {
    "stencil": dict(color="#d62728", lw=2.4, marker="o"),
    "stencil-lf50": dict(color="#ff7f0e", lw=2.0, marker="o", ls="--"),
    "stencil-block": dict(color="#17becf", lw=2.4, marker="P"),
    "stencil-block-hash": dict(color="#8c564b", lw=2.0, marker="P", ls="--"),
    "stencil-compact": dict(color="#e377c2", lw=2.4, marker="*"),
    "stencil-dense": dict(color="#d62728", lw=2.4, marker="o"),
    "stencil-links": dict(color="#ff7f0e", lw=2.4, marker="P"),
    "fastutil": dict(color="#1f77b4", lw=1.4, marker="s"),
    "hppc": dict(color="#2ca02c", lw=1.4, marker="^"),
    "eclipse": dict(color="#9467bd", lw=1.4, marker="D"),
    "jdk": dict(color="#7f7f7f", lw=1.2, marker="x", ls="--"),
    "fastutil-rb": dict(color="#1f77b4", lw=1.4, marker="s"),
    "fastutil-avl": dict(color="#2ca02c", lw=1.4, marker="^"),
    "jctools": dict(color="#1f77b4", lw=1.4, marker="s"),
    "speiger": dict(color="#2ca02c", lw=1.4, marker="^"),
    "trivago": dict(color="#9467bd", lw=1.4, marker="D"),
    "jdk-adder": dict(color="#7f7f7f", lw=1.2, marker="+", ls=":"),
}


def concurrent() -> bool:
    """Whether the suite measures thread-safe maps with several thread counts."""
    return SUITE.endswith("conc")


# Runs before the project was renamed label the implementations "primcoll", "primcoll-lf50", ...
LEGACY_PREFIX = "primcoll"


def legacy_labels(df: pd.DataFrame) -> pd.DataFrame:
    """Renames the implementation labels of earlier runs to the current ones."""
    df["impl"] = df["impl"].str.replace(f"^{LEGACY_PREFIX}", "stencil", regex=True)
    return df


def load(run_dir: Path) -> pd.DataFrame:
    frames = []
    for platform, stem in PLATFORMS:
        f = run_dir / f"{stem}.csv"
        if f.exists():
            df = legacy_labels(pd.read_csv(f))
            df["platform"] = platform
            frames.append(df)
    if not frames:
        sys.exit(f"no windows.csv / wsl.csv in {run_dir}")
    df = pd.concat(frames, ignore_index=True)
    if "threads" not in df.columns:
        df["threads"] = 1
    if concurrent():
        # every thread count becomes a key distribution of its own, so that all tables and plots
        # show the thread counts side by side ("RANDOM  1t", "RANDOM  6t", "RANDOM 12t")
        df["dist"] = df["dist"] + " " + df["threads"].map(lambda t: f"{t:>2}t")
    per_entry = ["build"] if concurrent() else PER_ENTRY_OPS  # concurrent computeIfAbsent is per operation
    build = df["op"].isin(per_entry)  # reported per map: per entry
    df.loc[build, "score"] /= df.loc[build, "size"]
    df.loc[build, "error"] /= df.loc[build, "size"]
    df["start"] = pd.to_datetime(df["start"], format="ISO8601")
    df["minutes"] = df.groupby("platform")["start"].transform(lambda s: (s - s.min()).dt.total_seconds() / 60)
    return df


def detect_suite(run_dir: Path) -> None:
    """Reads the suite (int, long, intset, longset, intobj, longobj, longsorted, longlongsorted, longsetsorted, longlinked, objectint, objectintlinked, longheap, intlongindexedheap, longlongindexedheap, intlist, intdeque, intconc or longconc) from the first line of the runner log."""
    global SUITE, SLOT_BYTES
    for _, stem in PLATFORMS:
        log = run_dir / f"{stem}.log"
        if log.exists():
            m = re.search(r"\(suite (\w+)\)", log.read_text(encoding="utf-8").splitlines()[0])
            if m:
                SUITE = m.group(1)
            break
    SLOT_BYTES = {"int": 8, "long": 16, "intset": 4, "longset": 8, "intobj": 8, "longobj": 12, "longsorted": 12, "longlongsorted": 16, "longsetsorted": 8, "longlinked": 12,
                  "objectint": 8, "objectintlinked": 8, "longheap": 8, "intlongindexedheap": 16, "longlongindexedheap": 16, "intlist": 4, "intdeque": 4, "intconc": 8, "longconc": 12, "intintconc": 8, "longlongconc": 16}[SUITE]


def subject() -> str:
    """What the suite measures, for titles: "int -> int maps", "int sets" or "int -> Object maps"."""
    if SUITE.endswith("set"):
        return f"{SUITE[:-3]} sets"
    if SUITE.endswith("obj"):
        return f"{SUITE[:-3]} -> Object maps"
    if SUITE == "longlongsorted":
        return "sorted long -> long maps"
    if SUITE == "longsetsorted":
        return "sorted long sets"
    if SUITE.endswith("sorted"):
        return f"sorted {SUITE[:-6]} -> Object maps"
    if SUITE == "objectintlinked":
        return "insertion-ordered String -> int maps"
    if SUITE.endswith("linked"):
        return f"insertion-ordered {SUITE[:-6]} -> Object maps"
    if SUITE == "objectint":
        return "String -> int maps"
    if SUITE == "longheap":
        return "priority queues of long values"
    if SUITE == "intlongindexedheap":
        return "indexed priority queues (int ids, long priorities)"
    if SUITE == "longlongindexedheap":
        return "indexed priority queues (long keys, long priorities)"
    if SUITE == "intlist":
        return "lists of int values"
    if SUITE == "intdeque":
        return "double-ended queues of int values"
    if SUITE in ("intintconc", "longlongconc"):
        return f"thread-safe {SUITE[:-7]} -> {SUITE[:-7]} maps"
    if concurrent():
        return f"thread-safe {SUITE[:-4]} -> Object maps"
    return f"{SUITE} -> {SUITE} maps"


def geomean(xs) -> float:
    xs = [x for x in xs if x > 0 and not math.isnan(x)]
    return math.exp(sum(map(math.log, xs)) / len(xs)) if xs else float("nan")


def fmt(v: float) -> str:
    if math.isnan(v):
        return "–"
    return f"{v:.0f}" if v >= 100 else f"{v:.1f}" if v >= 10 else f"{v:.2f}"


def paired_ratios(df: pd.DataFrame) -> pd.DataFrame:
    """One row per (platform, round, cell): score of every impl, as columns."""
    wide = df.pivot_table(index=["platform", "round", "op", "dist", "size"], columns="impl", values="score")
    return wide.reset_index()


def footprint_section(run_dir: Path) -> list[str]:
    """Table bytes per entry and effective fill, from <stem>-footprint.csv (first platform found)."""
    for platform, stem in PLATFORMS:
        f = run_dir / f"{stem}-footprint.csv"
        if f.exists():
            break
    else:
        return []
    fp = legacy_labels(pd.read_csv(f))
    fp["per_entry"] = fp["table_bytes"] / fp["size"]
    sequence = SUITE in ("intlist", "intdeque")
    if SUITE.endswith("sorted") or SUITE.endswith("linked") or SUITE.endswith("heap") or sequence or SUITE == "objectint" or concurrent():
        impls = [i for i in IMPLS if i in fp["impl"].unique()]
        excluded = ("the `String` keys are not counted, the boxed values of the JDK map are"
                    if SUITE in ("objectint", "objectintlinked") else "the boxed values or entry records of the JDK queue are counted"
                    if SUITE.endswith("heap") else "the boxed values of the JDK collection are counted"
                    if sequence else "the boxed keys and values of the JDK map are counted"
                    if SUITE == "longlongsorted" else "the boxed elements of the JDK set are counted"
                    if SUITE == "longsetsorted" else "boxed keys and values, adders and atomic values are counted"
                    if SUITE in ("intintconc", "longlongconc") else "the shared value objects are not counted, the "
                    "boxed keys of the JDK map are" if concurrent() else "the shared value objects are not counted")
        md = ["## Memory Footprint", "",
              f"Deep bytes per entry after building the map from the default capacity: all objects reachable "
              f"from the map (nodes, boxed keys and values, arrays) with headers and alignment, assuming compressed "
              f"references; {excluded}. Source: `{f.name}` ({platform}; the "
              f"footprint does not depend on the platform).", "",
              "| size | " + " | ".join(impls) + " |", "|---:|" + "---:|" * len(impls)]
        for size, g in fp.groupby("size"):
            g = g.set_index("impl")
            md.append(f"| {size:,} | " + " | ".join(
                f"{g.loc[i, 'per_entry']:.1f} B" if i in g.index else "–" for i in impls) + " |")
        md.append("")
        return md
    # compact layouts spend one control word per block (seven 8-byte slots, or seven slot pairs per
    # 128-byte map block, or fourteen 4-byte elements): 7/8 of the bytes are slots
    slot_share = fp["impl"].map(lambda i: 7 / 8 if i == "stencil-compact" else 1.0)
    fp["fill"] = fp["size"] / (fp["table_bytes"] / SLOT_BYTES * slot_share)
    impls = [i for i in IMPLS if i in fp["impl"].unique()]
    md = ["## Memory Footprint", "",
          f"Table bytes per entry after building the collection from the default capacity, effective fill "
          f"(entries / slots) in parentheses. Array payload only, without object headers; the boxed "
          f"JDK collection is not measured. Source: `{f.name}` ({platform}; the footprint does not depend on the platform).", "",
          "| size | " + " | ".join(impls) + " |", "|---:|" + "---:|" * len(impls)]
    for size, g in fp.groupby("size"):
        g = g.set_index("impl")
        md.append(f"| {size:,} | " + " | ".join(
            f"{g.loc[i, 'per_entry']:.1f} B ({g.loc[i, 'fill']:.2f})" if i in g.index else "–" for i in impls) + " |")
    md.append("")
    return md


def render(df: pd.DataFrame, run_dir: Path) -> str:
    wide = paired_ratios(df)
    platforms = [p for p, _ in PLATFORMS if p in df["platform"].unique()]
    impls = [i for i in IMPLS if i in df["impl"].unique()]
    libs = [i for i in LIBS if i in impls]
    rounds = df.groupby("platform")["round"].max().to_dict()

    md = [f"# Benchmark Report: Interleaved Run ({subject()})", ""]
    for platform, stem in PLATFORMS:
        log = run_dir / f"{stem}.log"
        env = run_dir / f"{stem}-env.txt"
        if log.exists():
            md.append(f"- **{platform}:** {log.read_text(encoding='utf-8').splitlines()[0][10:]}")
        if env.exists():
            for line in env.read_text(encoding="utf-8", errors="replace").splitlines():
                if line.split(":")[0].strip() in ("CPU", "Power", "JDK", "Kernel"):
                    md.append(f"  - {line.strip()}")
    md += ["",
           "All values in **ns per operation** (lower is better). `build` = building the collection with `size` entries "
           "from the default capacity, divided by `size`"
           + ("; `iterate`, `rangeScan` and `removeRange` per visited or removed entry" if SUITE.endswith("sorted")
              else "; `iterate` per visited entry" if SUITE.endswith("linked") else "")
           + ". Each cell (op, dist, size) was measured once per "
           "implementation per round; the forks of a cell ran back to back in random order. "
           "**Ratios are computed within a round (paired)** and then summarized over the rounds, "
           "so thermal drift cancels out.", ""]

    # --- summary
    candidates = [c for c in CANDIDATES if c in impls]
    if candidates:
        md += ["## Summary: stencil / baseline",
               "",
               "Geometric mean over all (dist, size) of the paired median ratios. "
               "< 1 means the stencil variant is faster. In parentheses: share of cells in which it was "
               "faster in **every** round.",
               ""]
        for cand in candidates:
            others = [i for i in impls if i != cand]
            md += [f"**{cand}**", "",
                   "| Platform | Operation | " + " | ".join(f"/ {b}" for b in others) + " |",
                   "|---|---|" + "---:|" * len(others)]
            for platform in platforms:
                for op in OPS:
                    w = wide[(wide["platform"] == platform) & (wide["op"] == op)]
                    if w.empty:
                        continue
                    cells = []
                    for b in others:
                        g = w.assign(ratio=w[cand] / w[b]).groupby(["dist", "size"])["ratio"]
                        med = g.median()
                        always = (g.max() < 1).mean()
                        cells.append(f"{geomean(med):.2f}× ({always:.0%})")
                    md.append(f"| {platform} | {op} | " + " | ".join(cells) + " |")
            md.append("")

    # --- footprint
    md += footprint_section(run_dir)

    # --- throughput over all threads
    if concurrent():
        md += throughput_section(df, platforms, impls)

    # --- detail tables
    for platform in platforms:
        md += [f"## {platform} ({rounds[platform]} rounds)", ""]
        d = df[df["platform"] == platform]
        for op in OPS:
            dop = d[d["op"] == op]
            if dop.empty:
                continue
            md += [f"### {op}", "",
                   "Median over the rounds, min–max in parentheses. Last columns: paired ratio of each stencil "
                   "variant / fastest library (median, min–max over the rounds in parentheses).", "",
                   "| dist | size | " + " | ".join(impls) + " | "
                   + " | ".join(f"{c} / best lib" for c in candidates) + " |",
                   "|---|---:|" + "---:|" * (len(impls) + len(candidates))]
            for (dist, size), cell in dop.groupby(["dist", "size"]):
                stats = cell.groupby("impl")["score"].agg(["median", "min", "max"])
                best_all = stats["median"].min()
                row = [dist, f"{size:,}"]
                for impl in impls:
                    if impl not in stats.index:
                        row.append("–")
                        continue
                    s = stats.loc[impl]
                    txt = f"{fmt(s['median'])} ({fmt(s['min'])}–{fmt(s['max'])})"
                    row.append(f"**{txt}**" if s["median"] == best_all else txt)
                lib_meds = stats.loc[[l for l in libs if l in stats.index], "median"]
                for cand in candidates:
                    if cand in stats.index and not lib_meds.empty:
                        best = lib_meds.idxmin()
                        w = wide[(wide["platform"] == platform) & (wide["op"] == op)
                                 & (wide["dist"] == dist) & (wide["size"] == size)]
                        ratio = w[cand] / w[best]
                        row.append(f"{ratio.median():.2f}× ({ratio.min():.2f}–{ratio.max():.2f}) {best}")
                    else:
                        row.append("–")
                md.append("| " + " | ".join(row) + " |")
            md.append("")

    # --- drift diagnostics
    df = df.copy()
    df["norm"] = df["score"] / df.groupby(["platform", "op", "dist", "size", "impl"])["score"].transform("median")
    md += ["## Drift Diagnostics", "",
           "Each measurement is divided by the median of its (implementation, cell) over all rounds. "
           "1.00 means no drift. Values rising over the rounds mean the machine is getting slower (e.g., throttling). "
           "A dependency on the position within the block would indicate order effects.", "",
           "| Platform | " + " | ".join(f"Round {r}" for r in range(1, max(rounds.values()) + 1)) + " |",
           "|---|" + "---:|" * max(rounds.values())]
    for platform in platforms:
        per_round = df[df["platform"] == platform].groupby("round")["norm"].median()
        md.append(f"| {platform} | " + " | ".join(f"{per_round.get(r, float('nan')):.3f}"
                                                  for r in range(1, max(rounds.values()) + 1)) + " |")
    md += ["", "| Platform | " + " | ".join(f"Position {p + 1}" for p in range(len(impls))) + " |",
           "|---|" + "---:|" * len(impls)]
    for platform in platforms:
        per_pos = df[df["platform"] == platform].groupby("pos")["norm"].median()
        md.append(f"| {platform} | " + " | ".join(f"{per_pos.get(p, float('nan')):.3f}" for p in range(len(impls))) + " |")
    md.append("")

    # --- platform comparison
    if len(platforms) == 2:
        a, b = platforms
        med = df.groupby(["platform", "op", "dist", "size", "impl"])["score"].median().unstack("platform")
        md += [f"## Platform Comparison {b} / {a}", "",
               f"Geometric mean of the median ratios over all (dist, size). < 1 means faster on {b}. "
               "Note: the two platforms use different JDK builds (see above).", "",
               "| Operation | " + " | ".join(impls) + " |", "|---|" + "---:|" * len(impls)]
        for op in OPS:
            cells = []
            for impl in impls:
                try:
                    m = med.xs((op, impl), level=("op", "impl"))
                    cells.append(f"{geomean(m[b] / m[a]):.2f}×")
                except KeyError:
                    cells.append("–")
            md.append(f"| {op} | " + " | ".join(cells) + " |")
        md.append("")
    return "\n".join(md)


def throughput_section(df: pd.DataFrame, platforms: list[str], impls: list[str]) -> list[str]:
    """Total throughput of all threads (million operations per second) by thread count."""
    md = ["## Total Throughput", "",
          "Million operations per second of all threads together (threads / median ns per operation and "
          "thread; for `build`, inserted keys per second of all workers). Higher is better.", ""]
    d = df.copy()
    d["base_dist"] = d["dist"].str.rsplit(" ", n=1).str[0].str.strip()
    threads = sorted(d["threads"].unique())
    for platform in platforms:
        for op in OPS:
            dop = d[(d["platform"] == platform) & (d["op"] == op)]
            if dop.empty:
                continue
            med = dop.groupby(["base_dist", "size", "impl", "threads"])["score"].median()
            md += [f"**{platform}, {op}**", "",
                   "| dist | size | impl | " + " | ".join(f"{t} threads" for t in threads) + " |",
                   "|---|---:|---|" + "---:|" * len(threads)]
            for (dist, size), _ in dop.groupby(["base_dist", "size"]):
                for impl in impls:
                    cells = []
                    for t in threads:
                        key = (dist, size, impl, t)
                        if key not in med.index:
                            cells.append("–")
                            continue
                        ns = med[key]
                        mops = (1 if op == "build" else t) * 1000.0 / ns
                        cells.append(f"{mops:.1f}")
                    md.append(f"| {dist} | {size:,} | {impl} | " + " | ".join(cells) + " |")
            md.append("")
    return md


def plot(df: pd.DataFrame, run_dir: Path) -> None:
    out = run_dir / "plots"
    out.mkdir(exist_ok=True)
    platforms = [p for p, _ in PLATFORMS if p in df["platform"].unique()]
    dists = sorted(df["dist"].unique())
    stats = df.groupby(["platform", "op", "dist", "impl", "size"])["score"].agg(["median", "min", "max"]).reset_index()
    for op in OPS:
        sub = stats[stats["op"] == op]
        if sub.empty:
            continue
        fig, axes = plt.subplots(len(platforms), len(dists), squeeze=False,
                                 figsize=(6 * len(dists), 4 * len(platforms)), sharex=True)
        for r, platform in enumerate(platforms):
            for c, dist in enumerate(dists):
                ax = axes[r][c]
                cell = sub[(sub["platform"] == platform) & (sub["dist"] == dist)]
                for impl in IMPLS:
                    if impl == "jdk" and not concurrent():  # boxed HashMap would flatten the chart; see tables
                        continue
                    d = cell[cell["impl"] == impl].sort_values("size")
                    if d.empty:
                        continue
                    yerr = [d["median"] - d["min"], d["max"] - d["median"]]
                    ax.errorbar(d["size"], d["median"], yerr=yerr, label=impl, capsize=3, **STYLE[impl])
                ax.set_xscale("log")
                ax.set_ylim(bottom=0)
                ax.set_title(f"{platform} - {dist}")
                ax.set_ylabel("ns/op" + (" (per insert)" if op == "build" else " (per key)" if op == "computeIfAbsent" else ""))
                ax.grid(True, which="both", alpha=0.3)
                if r == len(platforms) - 1:
                    ax.set_xlabel("size (entries)")
        axes[0][0].legend()
        fig.suptitle(f"{subject()}: {op}  (median over rounds, bars = min..max; lower is better; JDK: see report)")
        fig.tight_layout()
        fig.savefig(out / f"{op}.png", dpi=110)
        plt.close(fig)

    df = df.copy()
    df["norm"] = df["score"] / df.groupby(["platform", "op", "dist", "size", "impl"])["score"].transform("median")
    fig, axes = plt.subplots(len(platforms), 1, squeeze=False, figsize=(11, 3.5 * len(platforms)))
    for r, platform in enumerate(platforms):
        ax = axes[r][0]
        d = df[df["platform"] == platform]
        for impl in IMPLS:
            e = d[d["impl"] == impl]
            if e.empty:
                continue
            ax.scatter(e["minutes"], e["norm"], s=8, alpha=0.6, color=STYLE[impl]["color"], label=impl)
        ax.axhline(1.0, color="black", lw=0.8)
        ax.set_ylim(0.7, 1.4)
        ax.set_title(f"{platform}: score / median of its cell (drift over time)")
        ax.set_xlabel("minutes since start")
        ax.grid(True, alpha=0.3)
    axes[0][0].legend(ncol=6, fontsize=8)
    fig.tight_layout()
    fig.savefig(out / "drift.png", dpi=110)
    plt.close(fig)


def main() -> None:
    run_dir = Path(sys.argv[1] if len(sys.argv) > 1 else ".")
    detect_suite(run_dir)
    df = load(run_dir)
    global OPS
    OPS = [op for op in OPS if op in set(df["op"])]
    (run_dir / "report.md").write_text(render(df, run_dir), encoding="utf-8")
    plot(df, run_dir)
    print(f"wrote {run_dir / 'report.md'} and {run_dir / 'plots'}")


if __name__ == "__main__":
    main()
