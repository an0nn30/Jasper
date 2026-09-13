# Command search measurement

This opt-in measurement covers matching only. The 1,000 commands are normalized and indexed once before warmup; it does not measure native key-to-paint latency. Workstation timing is descriptive, with no CI threshold.

- OS: Mac OS X 26.6.2
- CPU architecture: aarch64
- Java: JetBrains s.r.o. 25.0.4.1
- Commit: `b2e13842de89` (Task 6 working tree)
- Catalog: 1000 indexed commands
- Query set: exact (`Open Workspace 0042`), prefix (`Open Work`), fuzzy (`opwks0042`), zero-match (`no-such-command-omega`)
- Warmup: 5000 calls per query
- Samples: 10000 calls per query
- Checksum: `-7719929789455020016`

| Query | Results | Median | p95 | Max | Allocated/call |
|---|---:|---:|---:|---:|---:|
| exact | 1 | 93.875 µs | 106.875 µs | 1039.958 µs | 624424 B |
| prefix | 5 | 18.292 µs | 19.208 µs | 623.667 µs | 48940 B |
| fuzzy | 1 | 52.791 µs | 59.750 µs | 697.084 µs | 200800 B |
| zero-match | 0 | 32.584 µs | 36.083 µs | 654.250 µs | 200680 B |
