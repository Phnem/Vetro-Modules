"""
results.json (from the Kotlin harness in tools/bench)  ->  bench_data.js (read by download.html)

  python bench_prepare.py results.json bench_data.js <tag> <sizeBytes>

For every method the run with the MEDIAN time is replayed in the graphic, so a lane shows a real,
typical run and its final time equals the median shown in the summary.
"""
import json
import sys

SMOOTH_SECONDS = 1.5   # trailing window for the throughput curve
STEP_MS = 250


def bytes_at(timeline, ms):
    if ms <= timeline[0][0]:
        return 0
    if ms >= timeline[-1][0]:
        return timeline[-1][1]
    lo, hi = 0, len(timeline) - 1
    while hi - lo > 1:
        mid = (lo + hi) // 2
        if timeline[mid][0] <= ms:
            lo = mid
        else:
            hi = mid
    (t0, b0), (t1, b1) = timeline[lo], timeline[hi]
    return b0 + (b1 - b0) * ((ms - t0) / max(1, t1 - t0))


def main() -> None:
    results_path, out_path, tag, size = sys.argv[1], sys.argv[2], sys.argv[3], int(sys.argv[4])
    results = json.load(open(results_path, encoding="utf-8"))
    data = {"tag": tag, "sizeBytes": size, "runs": 0, "methods": {}}
    for method in ("single", "equal6", "vetro"):
        runs = sorted((r for r in results if r["method"] == method), key=lambda r: r["seconds"])
        data["runs"] = max(data["runs"], len(runs))
        rep = runs[(len(runs) - 1) // 2]            # median run (lower median when the count is even)
        tl = rep["timeline"]
        end_ms = tl[-1][0]
        speed = []
        for ms in range(0, end_ms + STEP_MS, STEP_MS):
            ms = min(ms, end_ms)
            w = SMOOTH_SECONDS * 1000
            a = bytes_at(tl, max(0, ms - w))
            b = bytes_at(tl, ms)
            span = min(w, max(ms, 1)) / 1000
            speed.append([round(ms / 1000, 3), round((b - a) / 1048576 / span, 3)])
        data["methods"][method] = {
            "median": round(rep["seconds"], 2),
            "all": [round(r["seconds"], 2) for r in runs],
            "timeline": [[t, b] for t, b in tl[:: max(1, len(tl) // 400)]] + [tl[-1]],
            "speed": speed,
        }
        print(f"{method:7s} runs={len(runs)} median={rep['seconds']:.2f}s  all={data['methods'][method]['all']}")
    with open(out_path, "w", encoding="utf-8") as f:
        f.write("window.BENCH = " + json.dumps(data, separators=(",", ":")) + ";\n")


if __name__ == "__main__":
    main()
