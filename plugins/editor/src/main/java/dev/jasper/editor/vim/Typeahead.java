package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Mappings.Mapping;
import java.awt.AWTEvent;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.swing.Timer;

/**
 * Vim's typeahead buffer (spec 2.3 and 7.2), one per editor, ported from getchar.c (vgetorpeek, handle_mapping,
 * ins_typebuf, flush_buffers). Every key waits here before it runs: typed keys at the end, the keys of a mapping's
 * right-hand side or of a macro in front. A key sequence that starts a mapping's left-hand side waits for more keys
 * up to 'timeoutlen' ms on a Swing timer; a full match is replaced by its right-hand side, which is mapped again
 * unless the mapping is {@code noremap}. A failing command drops the keys that were not typed ({@link #flush}), which
 * ends a mapping or macro; a CTRL-C typed while they run ends them too (ui_breakcheck).
 */
final class Typeahead {
    /** Whether a waiting key may start a mapping (tb_noremap): yes; no; or not as the first key of its own mapping's right-hand side. */
    enum Remap { YES, NONE, SKIP }

    /** A waiting key: how it may be mapped, and whether it was typed (not from a mapping or a macro). */
    private record Key(KeyInput key, Remap remap, boolean typed) { }

    private static final KeyInput PLUG = KeyInput.named("Plug");
    /** How many keys from mappings and macros run between looks for a typed CTRL-C. */
    static final int BREAK_CHECK = 1000;

    private final VimEditor editor;
    /** The waiting keys; the next one is first. */
    private final List<Key> keys = new ArrayList<>();
    private final Timer timer;
    /** The timer ran out: the next resolution takes what matches in full (Vim's timedout). */
    private boolean timeoutFired;
    /** How deep {@code :normal} runs: out of keys, a partial mapping resolves at once (ex_normal_busy). */
    private int exNormal;
    private boolean running;
    /** Whether CTRL-C was typed while keys run (got_int); the test replaces it. */
    private BooleanSupplier interrupted = Typeahead::ctrlCWaiting;
    /** Whether the last key taken was typed (Vim's KeyTyped). */
    private boolean keyTyped;
    /** Whether taking the last key took a typed key, perhaps as part of a mapping (gotchars ran). */
    private boolean tookTyped;

    Typeahead(VimEditor editor) {
        this.editor = editor;
        timer = new Timer(1000, event -> timeout());
        timer.setRepeats(false);
    }

    /** A key typed: it waits behind the others, then everything that can run does. */
    void typed(KeyInput key) {
        keys.add(new Key(key, Remap.YES, true));
        run();
    }

    /**
     * ins_typebuf: keys that were not typed go in front of the waiting ones.
     *
     * @param inserted the keys, first one first
     * @param remap whether they may be mapped
     */
    void insert(List<KeyInput> inserted, Remap remap) {
        List<Key> front = new ArrayList<>();
        for (int i = 0; i < inserted.size(); i++)
            front.add(new Key(inserted.get(i), remap == Remap.SKIP ? i == 0 ? Remap.SKIP : Remap.YES : remap, false));
        keys.addAll(0, front);
    }

    /** flush_buffers(FLUSH_MINIMAL), after an error: the keys at the front that were not typed go. */
    void flush() {
        while (!keys.isEmpty() && !keys.getFirst().typed()) keys.removeFirst();
    }

    /** Everything goes, typed keys too, and the timer stops (the editor closed, or the text was replaced). */
    void clear() {
        keys.clear();
        timer.stop();
    }

    /** @return whether the timer runs for 'timeoutlen' */
    boolean timing() { return timer.isRunning(); }

    /** @return whether keys wait for the rest of a mapping */
    boolean waiting() { return !running && !keys.isEmpty(); }

    /** @return the keys waiting for the rest of a mapping, for 'showcmd' */
    List<KeyInput> waitingKeys() { return keys.stream().map(Key::key).toList(); }

    /** @return whether the last key taken was typed, not from a mapping, a macro or {@code :normal} (KeyTyped) */
    boolean keyTyped() { return keyTyped; }

    /** @return whether keys that were not typed wait (a mapping's or a macro's; Vim's typebuf.tb_maplen) */
    boolean mapped() { return !keys.isEmpty() && !keys.getFirst().typed(); }

    /** @param check whether CTRL-C was typed while keys run, instead of looking at the AWT event queue */
    void interruptCheck(BooleanSupplier check) { interrupted = check; }

    /** ui_breakcheck: whether a CTRL-C key press waits in the event queue while the UI thread runs keys. */
    private static boolean ctrlCWaiting() {
        if (GraphicsEnvironment.isHeadless()) return false;
        AWTEvent event = Toolkit.getDefaultToolkit().getSystemEventQueue().peekEvent(KeyEvent.KEY_PRESSED);
        return event instanceof KeyEvent key && key.getKeyCode() == KeyEvent.VK_C && key.isControlDown();
    }

    /**
     * Every {@link #BREAK_CHECK} keys from mappings and macros: after a CTRL-C every waiting key goes, typed ones too
     * (flush_buffers(FLUSH_INPUT)), and the bell rings; the key to run instead is {@code <C-c>}, or {@code <Esc>}
     * in Insert mode and on the command line, which ends the command in progress.
     *
     * @return that key, or null when the keys go on
     */
    private KeyInput interrupted(int mappedKeys) {
        if (mappedKeys % BREAK_CHECK != 0 || !interrupted.getAsBoolean()) return null;
        keys.clear();
        editor.mappingFailed(VimFailure.bell());
        return editor.mode().inserting() || editor.mode() == Mode.COMMAND_LINE ? KeyInput.named("Esc") : KeyInput.ctrl('c');
    }

    /** 'timeoutlen' ran out: the waiting keys resolve as they are. */
    void timeout() {
        timer.stop();
        if (keys.isEmpty() || running) return;
        timeoutFired = true;
        run();
    }

    /**
     * Runs keys until none are left or the next ones wait for the rest of a mapping; then the timer starts. The keys
     * of a mapping or a macro change the text as one undo step: Vim closes an undo step only when it takes a typed
     * key outside Insert mode and the command line (u_sync in gotchars).
     */
    void run() {
        if (running) return;
        running = true;
        timer.stop();
        boolean grouped = false;
        int mappedKeys = 0;
        try {
            while (!keys.isEmpty()) {
                KeyInput key = next();
                if (key == null) break;
                KeyInput stop = keyTyped ? null : interrupted(++mappedKeys);
                if (stop != null) key = stop;
                if (grouped && tookTyped && !editor.mode().inserting() && !editor.commandLine().active()) {
                    editor.endUndoStep();
                    grouped = false;
                }
                if (!grouped && !keyTyped) {
                    editor.beginUndoGroup();
                    grouped = true;
                }
                editor.step(key);
            }
        } finally {
            if (grouped) editor.endUndoStep();
            running = false;
            timeoutFired = false;
        }
        Options options = editor.options();
        if (!keys.isEmpty() && options.flag("timeout")) {
            timer.setInitialDelay(Math.max(0, options.number("timeoutlen")));
            timer.restart();
        }
    }

    /**
     * ex_normal: the keys run now with the typeahead saved, as if typed but mapped only when {@code remap}; keys
     * that run out in the middle of a mapping resolve at once. The first failure ends the keys.
     *
     * @return the first failure, or null
     */
    VimFailure execute(List<KeyInput> run, boolean remap) {
        List<Key> saved = new ArrayList<>(keys);
        keys.clear();
        exNormal++;
        VimFailure failed = null;
        int mappedKeys = 0;
        try {
            insert(run, remap ? Remap.YES : Remap.NONE);
            while (!keys.isEmpty()) {
                KeyInput key = next();
                if (key == null) break;
                KeyInput stop = interrupted(++mappedKeys);
                if (stop != null) key = stop;
                try {
                    editor.dispatch(key);
                } catch (VimFailure failure) {
                    if (failed == null) failed = failure;
                    flush();
                }
            }
        } finally {
            exNormal--;
            keys.clear();
            keys.addAll(saved);
        }
        return failed;
    }

    /**
     * vgetorpeek and handle_mapping: the next key to run, after replacing any mapping the waiting keys start with;
     * null when they are a proper prefix of a mapping and more keys may come.
     */
    private KeyInput next() {
        boolean timedOut = timeoutFired;
        timeoutFired = false;
        tookTyped = false;
        int depth = 0;
        while (!keys.isEmpty()) {
            Key head = keys.getFirst();
            int state = editor.mapState(head.key());
            Mappings mappings = editor.vim().mappings();
            boolean plug = head.key().equals(PLUG);
            if (state != 0 && !mappings.isEmpty() && (head.remap() == Remap.YES || plug)) {
                Mapping match = null;
                boolean partial = false;
                for (Mapping m : mappings.starting(state, head.key())) {
                    int keylen = m.lhs.size();
                    int mlen = 1;
                    while (mlen < keys.size() && mlen < keylen && m.lhs.get(mlen).equals(keys.get(mlen).key())) mlen++;
                    if (mlen != keylen && !(mlen == keys.size() && keys.size() < keylen)) continue;
                    // A key that may not be mapped cannot be part of the left-hand side.
                    if (!plug && keys.subList(0, mlen).stream().anyMatch(k -> k.remap() != Remap.YES)) continue;
                    if (keylen > keys.size()) {
                        if (!timedOut && !(match != null && match.nowait)) {
                            partial = true;
                            break;
                        }
                    } else if (match == null || keylen > match.lhs.size()) match = m;
                }
                if (partial) {
                    if (exNormal == 0) return null;
                    // Out of keys inside :normal: as if the time ran out.
                    timedOut = true;
                    continue;
                }
                if (match != null) {
                    consume(match.lhs.size());
                    if (++depth >= editor.options().number("maxmapdepth")) {
                        editor.mappingFailed(new VimFailure("E223: Recursive mapping"));
                        flush();
                        depth = 0;
                        continue;
                    }
                    List<KeyInput> rhs = match.rhs;
                    Remap remap = match.noremap ? Remap.NONE
                        : rhs.size() >= match.lhs.size() && rhs.subList(0, match.lhs.size()).equals(match.lhs) ? Remap.SKIP : Remap.YES;
                    insert(rhs, remap);
                    continue;
                }
            }
            consume(1);
            keyTyped = head.typed();
            return head.key();
        }
        return null;
    }

    /** The first {@code n} keys are taken. */
    private void consume(int n) {
        for (int i = 0; i < n; i++) if (keys.removeFirst().typed()) tookTyped = true;
    }
}
