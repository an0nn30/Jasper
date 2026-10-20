package dev.jasper.app.plugins;

import dev.jasper.app.windows.AuxiliarySurface;

/** Stable slot identity, separate from the owning surface's shown/lifetime authority. */
record SurfaceMount(AuxiliarySurface surface, boolean title) { }
