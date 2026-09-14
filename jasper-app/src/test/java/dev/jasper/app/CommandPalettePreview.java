package dev.jasper.app;

/** Headless command palette previews using the real standard Swing window contents. */
public final class CommandPalettePreview {
    public static void main(String[] args) throws Exception {
        if (args.length == 0) throw new IllegalArgumentException("Pass an output directory.");
        var options = new java.util.ArrayList<>(java.util.List.of(args));
        options.add("--commands");
        MockUiPreview.main(options.toArray(String[]::new));
    }
}
