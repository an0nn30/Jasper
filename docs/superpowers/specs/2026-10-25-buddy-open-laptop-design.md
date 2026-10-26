# Buddy laptop-opening animation

Status: reverse-pose approach, design, plan and build authorized by the user on 2026-10-25.

## Intent and approaches

Begin qualifying work by visibly sitting down and opening the laptop. The user chose reusing
stand-up poses in reverse over generating dedicated opening artwork: consistent existing pixels,
no new assets. Standing enters RISE for 150 ms, LOWER_LAPTOP for 125 ms, CLOSE_LAPTOP for
125 ms, TYPE_REST for 100 ms, then TYPE_A and normal typing: exactly 500 ms.

## Lifecycle

A new LOWERING mode owns this finite sequence. Starting work while rising reverses from the
current pose, holding that pose for its full existing duration before continuing toward typing.
Stopping during lowering similarly reverses from the current pose toward standing; never jump
to TYPE_REST or IDLE. Repeated intent preserves deadlines. Attention does not interrupt either
transition. Late ticks complete transitions from their actual deadlines, then catch up typing or
idle in bounded time. Hide cancels visible motion; show preserves intent through spawn.

Already seated idle Buddy starts at LOWER_LAPTOP, avoiding a standing detour. Sleeping or tucked
Buddy wakes through the existing wake sequence, then lowers; spawn, greeting and waking finish
before honoring the latest intent. Clearing work during those busy stages avoids phantom typing.
No facade, SDK, command qualification or artwork changes. No GUI, benchmark or AOT runs.

## Verification

Pin frames and deadlines, both reversal directions at every pose, repeated intent, attention,
hide/show, sleeping/seated starts, delayed ticks and resumed typing cadence. Produce headless
normal and interrupted GIF previews using the production animator and sprites. Update existing
stand-up preview and current guides for revised interruption behavior. Run focused Buddy tests,
Buddy checks and full headless check with Python 3.13. Commit locally; merging/pushing this new
feature requires a further user request.
