# Windows CI shard implementation

**Status: REVIEW PENDING.** Two isolated Windows jobs run the app and other module checks with one Gradle worker each. An aggregate job requires both. The 30-minute job and 26-minute watchdog limits are unchanged; hosted validation is pending.

**Goal:** Complete the existing Windows check without omitting any task or running concurrent test JVMs on one runner.

**Design:** Each runner independently captures the task graphs for ordinary `check` and both explicit shard root sets using the pinned wrapper and the same options as execution. The verifier rejects missing roots, missing or extra tasks, malformed or absent graphs, and failed discovery. The union of shard graphs must equal the full graph. A second guard compares the actual execution graph to its captured shard graph before any task executes. The aggregate downloads both runners’ actual graphs and requires identical full graphs and an exact actual union. Each shard independently builds prerequisites, runs the toolchain gate, and requires the existing fresh successful Gradle marker under the watchdog. Dry runs cannot write that marker. Graphs are uploaded even on success; failure diagnostics remain separate per shard. A new project check or other task absent from both shards fails graph validation until assigned.

**Alternatives:** Further test optimization is insufficiently predictable across hosts; increasing workers on one host previously caused subprocess deadlines. Separate runners preserve single-worker execution and duplicate shared prerequisites.

**Verification:** Exercise missing/extra tasks, invalid graph files, discovery failure and both shard selections in the Python fixture. A temporary real Gradle fixture verifies actual graph mismatch blocks task execution and a newly added unassigned check prevents the build and marker. Compare real repository graph union, run watchdog fixtures and workflow lint. Hosted Windows results and platform-specific graph completeness remain CI gates.
