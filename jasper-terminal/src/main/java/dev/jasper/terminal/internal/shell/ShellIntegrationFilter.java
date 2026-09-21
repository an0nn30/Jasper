package dev.jasper.terminal.internal.shell;

/**
 * Rewrites the shell-integration sequences JediTerm ignores into its OSC 1341 custom commands,
 * which JediTerm hands to {@code TerminalCustomCommandListener}s in emulator order:
 * <pre>
 *   OSC 7 ; data ST        →  OSC 1341 ; jasper ; cwd ; data BEL
 *   OSC 133 ; data ST      →  OSC 1341 ; jasper ; mark ; data BEL
 *   CSI 0 SP q, CSI SP q   →  unchanged, then OSC 1341 ; jasper ; cursor-reset BEL
 *   CSI ... q without SP   →  dropped
 * </pre>
 * The last rule is a guard rather than an integration: only {@code CSI ... SP q} is DECSCUSR, but
 * JediTerm reads every CSI ending in {@code q} as one, so the XTVERSION query {@code CSI > q} that
 * tmux sends when it opens a session would arrive as a request for a blinking block cursor and
 * outrank the configured shape for the rest of the session. JediTerm answers none of these queries,
 * so dropping them costs nothing.
 * <p>
 * Everything else passes through unchanged. Reader thread only.
 */
public final class ShellIntegrationFilter {
    public static final String PREFIX = "\033]1341;jasper;";

    private static final char ESC = '\033';
    private static final char BEL = '\007';
    private static final int MAX_HELD = 4096;

    private enum State { TEXT, ESCAPE, OSC_NUMBER, OSC_DATA, OSC_DATA_ESCAPE, CSI }

    private final StringBuilder held = new StringBuilder();
    private final StringBuilder oscNumber = new StringBuilder();
    private final StringBuilder csiParameters = new StringBuilder();
    private State state = State.TEXT;
    private String command;
    private int dataStart;
    private boolean csiSpaced;

    public void filter(char[] input, int offset, int length, StringBuilder out) {
        for (int i = offset; i < offset + length; i++) {
            accept(input[i], out);
        }
    }

    /** End of stream: releases anything still held, unchanged. */
    public void finish(StringBuilder out) {
        out.append(held);
        reset();
    }

    private void accept(char c, StringBuilder out) {
        if (state == State.TEXT) {
            if (c == ESC) {
                held.append(c);
                state = State.ESCAPE;
            } else {
                out.append(c);
            }
            return;
        }
        held.append(c);
        switch (state) {
            case ESCAPE -> {
                if (c == ']') {
                    oscNumber.setLength(0);
                    state = State.OSC_NUMBER;
                } else if (c == '[') {
                    state = State.CSI;
                } else {
                    pass(out);
                }
            }
            case OSC_NUMBER -> {
                if (c >= '0' && c <= '9') {
                    oscNumber.append(c);
                    String number = oscNumber.toString();
                    if (!"7".startsWith(number) && !"133".startsWith(number)) {
                        pass(out);
                    }
                } else if (c == ';' && (isNumber("7") || isNumber("133"))) {
                    command = isNumber("7") ? "cwd" : "mark";
                    dataStart = held.length();
                    state = State.OSC_DATA;
                } else {
                    pass(out);
                }
            }
            case OSC_DATA -> {
                if (c == BEL) {
                    complete(out, held.substring(dataStart, held.length() - 1));
                } else if (c == ESC) {
                    state = State.OSC_DATA_ESCAPE;
                } else if (held.length() > MAX_HELD) {
                    pass(out);
                }
            }
            case OSC_DATA_ESCAPE -> {
                if (c == '\\') {
                    complete(out, held.substring(dataStart, held.length() - 2));
                } else {
                    pass(out);
                }
            }
            case CSI -> {
                if (c >= '0' && c <= '?') {
                    csiParameters.append(c);
                    if (held.length() > MAX_HELD) pass(out);
                } else if (c >= ' ' && c <= '/') {
                    csiSpaced |= c == ' ';
                    if (held.length() > MAX_HELD) pass(out);
                } else if (c < '@' || c > '~') {
                    pass(out); // ESC or a control character abandons the sequence
                } else if (c != 'q') {
                    pass(out);
                } else if (!csiSpaced) {
                    reset(); // a query JediTerm would misread as DECSCUSR; drop it
                } else if (csiParameters.isEmpty() || csiParameters.toString().equals("0")) {
                    out.append(held).append(PREFIX).append("cursor-reset").append(BEL);
                    reset();
                } else {
                    pass(out);
                }
            }
            default -> throw new IllegalStateException("unexpected state " + state);
        }
    }

    private boolean isNumber(String number) {
        return oscNumber.toString().equals(number);
    }

    private void complete(StringBuilder out, String data) {
        out.append(PREFIX).append(command).append(';').append(data).append(BEL);
        reset();
    }

    /** Not one of ours: release the held characters unchanged, keeping a trailing ESC as a possible new start. */
    private void pass(StringBuilder out) {
        int last = held.length() - 1;
        if (last > 0 && held.charAt(last) == ESC) {
            out.append(held, 0, last);
            reset();
            held.append(ESC);
            state = State.ESCAPE;
        } else {
            out.append(held);
            reset();
        }
    }

    private void reset() {
        held.setLength(0);
        csiParameters.setLength(0);
        csiSpaced = false;
        state = State.TEXT;
        command = null;
    }
}
