# Smaller Protean splash

> Execute with superpowers:subagent-driven-development; one task and independent review.

**Goal:** Make the standard splash visibly smaller, as requested after desktop testing.
**Design:** Reduce each logical dimension by 20%, from 840 × 480 to 672 × 384 (36% less area). Preserve the approved composition and motion by uniformly scaling its design coordinates at paint time; render at the actual device scale. Explicit plugin branding retains its existing dimensions. This is a bounded amendment to the Protean splash design; 80% is the implementation choice communicated to the user.
**Status:** Implemented; independent task/final follow-up review passed without findings. Splash scope passes 62 tests without failures/errors/skips; application architecture passes. Smaller 1/2/4x static and motion sheets inspected; preview and GIF regenerated in `jasper-app/build/protean-splash-evidence/`. No implementation deviations. No GUI, AOT, merge or new dependencies. Existing PR #4 is the publication target; prior push/monitor authorization remains in force.

## Task 1: Reduce standard splash size

- [x] Keep the established 840 × 480 drawing coordinates, but publish 672 × 384 painter/window bounds and apply one 0.8 transform to the composition. Fill the complete design canvas and preserve graphics state, origin, clip and crisp HiDPI artwork.
- [x] Adapt existing dimensions, pixel-region assertions and render exporters to the smaller bounds. Keep the meaningful fractional/unequal-scale buffer-parity and motion checks; do not add tests that simply repeat implementation constants beyond the existing size contract.
- [x] Update the current guide's size, add a brief STATUS entry and record this plan's outcome. Historical records retain their original dimensions.
- [x] Run the existing splash tests and architecture gate; inspect headless 1×/2×/4× previews. Preserve legacy branded geometry and all queue/animation behavior.
- [x] Commit with Co-Authored-By trailer; independent review, scoped fixes if necessary, then publish to the existing PR and resume CI monitoring.
