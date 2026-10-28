package dev.jasper.app.platform;

final class LinuxTrayPolicy {
    enum Target { NONE, MODERN, LEGACY }
    private LinuxTrayPolicy() { }
    static Target target(boolean modern, boolean legacy) {
        return modern ? Target.MODERN : legacy ? Target.LEGACY : Target.NONE;
    }
    static int retrySeconds(int failures) {
        return Math.min(30, 1 << Math.min(5, Math.max(0, failures)));
    }
}
