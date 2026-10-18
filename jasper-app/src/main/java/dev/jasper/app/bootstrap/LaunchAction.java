package dev.jasper.app.bootstrap;

/** What a launch does once it knows whether it took its home's instance lock (workspace-tabs spec 5.2). Pure. */
enum LaunchAction {
    /** This process owns the home and starts: a window, its paths, its flags, a login warm-up or a windowless server. */
    OWN,
    /** Hands its request (RAISE or OPEN_PATHS, with the start-up flags it cannot apply) to the owner and exits 0. */
    HAND_OFF,
    /** Exits 0: a login launch, or the {@code jasper} command's windowless start, while an owner runs. */
    EXIT,
    /** Restart's replacement: waits for the lock up to 30 s. */
    AWAIT_LOCK;

    /**
     * Spec 5.2's table.
     *
     * @param acquired whether this launch took the lock
     * @param options its arguments
     * @return what it does
     */
    static LaunchAction of(boolean acquired, AppArguments options) {
        if (acquired) return OWN;
        if (options.replacing().isPresent()) return AWAIT_LOCK;
        if (options.background() || options.noWindow()) return EXIT;
        return HAND_OFF;
    }
}
