# Buddy stand-up animation

Status: motion approved and design, plan and implementation authorized on 2026-10-24.

## Intent and approach

When the last qualifying command ends, Jasper visibly stops typing, closes/lowers his
laptop, rises and settles into his existing standing pose. The user chose new transition
artwork over reusing the existing sitting/standing poses, which would retain a visible jump.
This is a small follow-up to the tab-aware Buddy work; command qualification and bubble
visibility remain owned by the application.

## Motion and lifecycle

The proposed exact timing is 500 ms: existing TYPE_REST for 100 ms, new CLOSE_LAPTOP for
125 ms, new LOWER_LAPTOP for 125 ms, new RISE for 150 ms, then existing IDLE. The three
new poses match the shaded pixel turtle, glasses, palette, proportions, transparent
margins and ground baseline. The laptop is closed/lowered out of view before the final
standing pose; no flourish or greeting is added. Existing 20 frames remain pixel-identical.

Only an actual WORKING-to-not-working transition starts this sequence. Repeated false
does not restart it. New work interrupts any transition frame immediately into typing.
Hover, greet and poke do not interrupt rising. Hide cancels the visible transition; show
uses the existing spawn and retained working intent. Clearing work during spawn/greet/wake
does not play a rise that was never preceded by a typing pose. Idle sitting/sleep deadlines
restart when standing completes, not when work stopped. Late ticks resolve the finite
transition without replaying missed frames indefinitely.

The clock-driven animator owns state/timing; the presentation supplies current time and
reschedules its one-shot timer on working-intent changes. No notification, SDK or public
Buddy facade contract changes are needed. No global motion preference is introduced.

## Artwork and verification

Generate the three transition poses using the approved source atlas as a character
reference; retain source/prompt provenance in the repository. Pack only the new cells at
84 × 96 with nearest-neighbor sampling and binary alpha, append them to the runtime strip,
and synchronize the LibreSprite master. Existing frame columns and logical geometry stay
fixed. Deterministic packing is asset integration, not replacement of the generated artwork.
Visual acceptance requires a smooth progression at actual rendered size and enlarged size;
the builder may adjust cell placement within the existing margins to avoid jitter.

Clock-driven tests pin exact frames/deadlines, repeated intent, renewed work at each step,
late ticks, idle timing, hide/show and busy-stage behavior. Sprite tests verify frame count,
opaque/transparent pixels and unchanged original artwork. A headless animated preview and
contact sheet use the committed runtime sprites and production animator. Run the Buddy
checks, architecture/doc checks and full headless check. Native GUI inspection is left to
the user; no GUI, benchmarks or AOT training, push or merge is authorized.
