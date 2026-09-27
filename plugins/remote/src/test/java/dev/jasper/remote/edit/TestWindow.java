package dev.jasper.remote.edit;

import dev.jasper.sdk.terminal.TabHandle;
import dev.jasper.sdk.terminal.WindowHandle;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/** A terminal window whose openness a test controls. */
record TestWindow(UUID id, AtomicBoolean open) implements WindowHandle {
    @Override public List<TabHandle> tabs() { return List.of(); }
    @Override public Optional<TabHandle> activeTab() { return Optional.empty(); }
    @Override public boolean isActive() { return open.get(); }
    @Override public boolean isOpen() { return open.get(); }
    @Override public void toFront() { }
}
