# Theme-selected splash artwork

The user approved the seven landscape previews and their revised, subtly recolored turtle characters (Catppuccin characters wear coordinated coats), then explicitly authorized implementation on 2026-10-26. This is the implementation design for that approved visual direction.

Use the saved interface theme for the appearance active at startup to select Jasper Meadow, Slate Cobalt, Ubuntu Yaru, Catppuccin Latte, Frappé, Macchiato or Mocha artwork. Theme changes take effect on the next ordinary windowed launch. Explicit branded looks retain their branded splash contract. Unsupported/custom/Desktop colors use Meadow artwork; invalid selections follow existing theme validation before artwork selection. System appearance uses the application's existing dark-on-unavailable policy; boot must not start extra Linux desktop helper processes just for a splash.

Keep the existing 720 × 440 logical window and scale-aware back buffer. Separate high-resolution lossless art from runtime lettering, build version, loading status and progress. Source artwork is at least 1440 × 880 (native 2×), matching the existing Retina render; verify decoded sizes and inspect headless 1×/2×/4× renders. The generator returned roughly 1604 × 980 despite a requested 4× size; 4× renders therefore scale the native illustration, not extra invented detail. Uniform cover with a tiny centered crop absorbs generator aspect rounding; never stretch axes independently. The landscape and themed character form one still illustration; there is no fabricated animation of these new poses. Existing animated Meadow remains an asset-error fallback. Preserve startup closure, headless/background/safe-mode exclusions, first-fill colors, graphics-state isolation and progress clamping.

## Approach

A full screenshot would bake in loading text/version and blur lettering. Rebuilding the approved generated scenes as vectors and animated sprites would change their appearance and greatly expand scope. The chosen implementation uses separate generated art and native text/progress rendering. This is an implementation choice under the user's go-ahead, not a claim they selected a new API.

No new dependency, SDK change, GUI launch, AOT, merge or push. Custom themes have no supplied illustrations, so the existing Meadow fallback is deliberate. Each approved theme has one illustration, regardless of light/dark variant, matching the reviewed set.
