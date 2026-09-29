package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Mappings.Mapping;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * The emulated extensions (spec 8), as IdeaVim has them: each is switched on by {@code set {name}} or by its
 * {@code Plug '…'} line, and then adds its plugin's default mappings to its {@code <Plug>} commands (unless its
 * {@code g:…_no_mappings} is set, and, as the plugin checks with hasmapto(), unless the user mapped the
 * {@code <Plug>} name already). Switching it off takes those mappings away again.
 */
final class Extensions {
    /**
     * An extension.
     *
     * @param option the switch, as {@code :set} names it
     * @param repository its plugin's {@code owner/name}, which a {@code Plug} line names
     * @param noMappings the variable that keeps its default mappings out, or null
     * @param check how its plugin asks hasmapto() first: not at all, once for all its mappings, or for each
     * @param mappings its default mappings, in the order its plugin makes them: a mapping command, the keys and the
     *     {@code <Plug>} name
     */
    record Extension(String option, String repository, String noMappings, Check check, List<String[]> mappings) { }

    /**
     * How a plugin checks before it maps: not at all, hasmapto() once for all its mappings or for each, or with
     * {@code <unique>}, which leaves out a key the user mapped already in that mode.
     */
    enum Check { NONE, ALL, EACH, UNIQUE }

    private static String[] map(String command, String lhs, String rhs) { return new String[] { command, lhs, rhs }; }

    /** Every extension Jasper has. */
    static final List<Extension> ALL = List.of(
        new Extension("surround", "tpope/vim-surround", "surround_no_mappings", Check.NONE, List.of(
            map("nmap", "ds", "<Plug>Dsurround"), map("nmap", "cs", "<Plug>Csurround"), map("nmap", "cS", "<Plug>CSurround"),
            map("nmap", "ys", "<Plug>Ysurround"), map("nmap", "yS", "<Plug>YSurround"), map("nmap", "yss", "<Plug>Yssurround"),
            map("nmap", "ySs", "<Plug>YSsurround"), map("nmap", "ySS", "<Plug>YSsurround"), map("xmap", "S", "<Plug>VSurround"),
            map("xmap", "gS", "<Plug>VgSurround"))),
        new Extension("commentary", "tpope/vim-commentary", null, Check.ALL, List.of(
            map("xmap", "gc", "<Plug>Commentary"), map("nmap", "gc", "<Plug>Commentary"), map("omap", "gc", "<Plug>Commentary"),
            map("nmap", "gcc", "<Plug>CommentaryLine"), map("nmap", "gcu", "<Plug>Commentary<Plug>Commentary"))),
        new Extension("exchange", "tommcdo/vim-exchange", "exchange_no_mappings", Check.EACH, List.of(
            map("nmap", "cx", "<Plug>(Exchange)"), map("xmap", "X", "<Plug>(Exchange)"), map("nmap", "cxc", "<Plug>(ExchangeClear)"),
            map("nmap", "cxx", "<Plug>(ExchangeLine)"))),
        new Extension("replacewithregister", "vim-scripts/ReplaceWithRegister", null, Check.EACH, List.of(
            map("nmap", "gr", "<Plug>ReplaceWithRegisterOperator"), map("nmap", "grr", "<Plug>ReplaceWithRegisterLine"),
            map("xmap", "gr", "<Plug>ReplaceWithRegisterVisual"))),
        new Extension("vim-paragraph-motion", "dbakker/vim-paragraph-motion", null, Check.UNIQUE, List.of(
            map("nmap", "}", "<Plug>(ParagraphNextMotion)"), map("omap", "}", "<Plug>(ParagraphNextMotion)"),
            map("xmap", "}", "<Plug>(ParagraphNextMotion)"), map("nmap", "{", "<Plug>(ParagraphPrevMotion)"),
            map("omap", "{", "<Plug>(ParagraphPrevMotion)"), map("xmap", "{", "<Plug>(ParagraphPrevMotion)"))),
        new Extension("textobj-entire", "kana/vim-textobj-entire", "textobj_entire_no_default_key_mappings", Check.UNIQUE, List.of(
            map("xmap", "ae", "<Plug>(textobj-entire-a)"), map("omap", "ae", "<Plug>(textobj-entire-a)"),
            map("xmap", "ie", "<Plug>(textobj-entire-i)"), map("omap", "ie", "<Plug>(textobj-entire-i)"))),
        new Extension("textobj-indent", "michaeljsmith/vim-indent-object", null, Check.NONE, List.of(
            map("omap", "ai", "<Plug>textobj-indent-ai"), map("omap", "ii", "<Plug>textobj-indent-ii"),
            map("xmap", "ai", "<Plug>textobj-indent-ai"), map("xmap", "ii", "<Plug>textobj-indent-ii"),
            map("omap", "aI", "<Plug>textobj-indent-aI"), map("omap", "iI", "<Plug>textobj-indent-iI"),
            map("xmap", "aI", "<Plug>textobj-indent-aI"), map("xmap", "iI", "<Plug>textobj-indent-iI"))),
        new Extension("argtextobj", "vim-scripts/argtextobj.vim", null, Check.NONE, List.of(
            map("xmap", "ia", "<Plug>InnerArgument"), map("xmap", "aa", "<Plug>OuterArgument"),
            map("omap", "ia", "<Plug>InnerArgument"), map("omap", "aa", "<Plug>OuterArgument"))),
        new Extension("highlightedyank", "machakann/vim-highlightedyank", null, Check.NONE, List.of()),
        new Extension("matchit", "chrisbra/matchit", "no_plugin_maps", Check.NONE, List.of(
            map("nmap", "%", "<Plug>(MatchitNormalForward)"), map("nmap", "g%", "<Plug>(MatchitNormalBackward)"),
            map("xmap", "%", "<Plug>(MatchitVisualForward)"), map("xmap", "g%", "<Plug>(MatchitVisualBackward)"),
            map("omap", "%", "<Plug>(MatchitOperationForward)"), map("omap", "g%", "<Plug>(MatchitOperationBackward)"),
            map("nmap", "[%", "<Plug>(MatchitNormalMultiBackward)"), map("nmap", "]%", "<Plug>(MatchitNormalMultiForward)"),
            map("xmap", "[%", "<Plug>(MatchitVisualMultiBackward)"), map("xmap", "]%", "<Plug>(MatchitVisualMultiForward)"),
            map("omap", "[%", "<Plug>(MatchitOperationMultiBackward)"), map("omap", "]%", "<Plug>(MatchitOperationMultiForward)"),
            map("xmap", "a%", "<Plug>(MatchitVisualTextObject)"))));

    private final Vim vim;
    /** The default mappings each switched-on extension added. */
    private final Map<String, List<Mapping>> added = new HashMap<>();
    /** The extensions' script variables ({@code s:}), which last as long as Vim does. */
    private final Map<Class<?>, Object> scripts = new HashMap<>();

    Extensions(Vim vim) { this.vim = vim; }

    /**
     * @param repository what a {@code Plug} line names: {@code owner/name}, perhaps as a URL or with {@code .git}
     * @return the extension, or empty for a plugin Jasper does not emulate
     */
    static Optional<Extension> byRepository(String repository) {
        String name = repository.strip();
        if (name.endsWith(".git")) name = name.substring(0, name.length() - 4);
        if (name.endsWith("/")) name = name.substring(0, name.length() - 1);
        String[] parts = name.split("/");
        if (parts.length < 2) return Optional.empty();
        String key = (parts[parts.length - 2] + "/" + parts[parts.length - 1]).toLowerCase(Locale.ROOT);
        return ALL.stream().filter(e -> e.repository().toLowerCase(Locale.ROOT).equals(key)).findFirst();
    }

    /** @return an extension's script variables, made the first time */
    <T> T script(Class<T> type, java.util.function.Supplier<T> make) { return type.cast(scripts.computeIfAbsent(type, key -> make.get())); }

    /** @return whether the extension whose switch this is is on */
    boolean on(String option) { return vim.options().flag(option); }

    /** The switches may have changed: extensions switched on get their mappings, those switched off lose them. */
    void sync() {
        for (Extension extension : ALL) {
            boolean on = on(extension.option());
            if (on && !added.containsKey(extension.option())) added.put(extension.option(), install(extension));
            else if (!on && added.containsKey(extension.option())) {
                Mappings mappings = vim.mappings();
                List<Mapping> current = mappings.all();
                for (Mapping m : added.remove(extension.option())) if (current.contains(m)) mappings.remove(m);
            }
        }
    }

    private List<Mapping> install(Extension extension) {
        List<Mapping> made = new ArrayList<>();
        Object skip = extension.noMappings() == null ? null : vim.variables().get(extension.noMappings());
        if (skip != null && !skip.toString().equals("0") && !skip.toString().isEmpty()) return made;
        Mappings mappings = vim.mappings();
        // commentary.vim: if !hasmapto('<Plug>Commentary') || maparg('gc','n') ==# '' (all its mappings, or none).
        if (extension.check() == Check.ALL) {
            List<KeyInput> plug = ExMaps.parse(extension.mappings().getFirst()[2], false), gc = ExMaps.parse(extension.mappings().getFirst()[1], true);
            boolean mapped = mappings.all().stream().anyMatch(m -> (m.modes & Mappings.MAP) != 0 && contains(m.rhs, plug));
            boolean gcTaken = mappings.all().stream().anyMatch(m -> (m.modes & Mappings.NORMAL) != 0 && m.lhs.equals(gc));
            if (mapped && gcTaken) return made;
        }
        for (String[] m : extension.mappings()) {
            int modes = ExMaps.mode(m[0], false);
            List<KeyInput> lhs = ExMaps.parse(m[1], true), rhs = ExMaps.parse(m[2], false);
            // hasmapto(): the user mapped this <Plug> name already, so the plugin leaves its default out.
            if (extension.check() == Check.EACH && mappings.all().stream().anyMatch(existing -> (existing.modes & modes) != 0 && contains(existing.rhs, rhs)))
                continue;
            // <unique>: the key is mapped already in the mode (E227 in Vim, which the plugin's loading shows).
            if (extension.check() == Check.UNIQUE && mappings.all().stream().anyMatch(existing -> (existing.modes & modes) != 0 && existing.lhs.equals(lhs)))
                continue;
            made.add(ExMaps.define(mappings, modes, lhs, rhs, false, false));
        }
        return made;
    }

    private static boolean contains(List<KeyInput> keys, List<KeyInput> part) {
        for (int i = 0; i + part.size() <= keys.size(); i++) if (keys.subList(i, i + part.size()).equals(part)) return true;
        return false;
    }

    /**
     * {@code Plug '…'} (vim-plug's, as IdeaVim reads it): switches on the extension Jasper has for the plugin named;
     * a plugin Jasper does not emulate is ignored without an error (spec 8).
     */
    static void plug(ExCommand cmd) {
        String arg = cmd.arg.strip();
        if (arg.isEmpty() || (arg.charAt(0) != '\'' && arg.charAt(0) != '"')) throw new VimFailure("E471: Argument required");
        int close = arg.indexOf(arg.charAt(0), 1);
        if (close < 0) throw new VimFailure("E474: Invalid argument: " + arg);
        byRepository(arg.substring(1, close)).ifPresent(extension -> cmd.editor.options().set(extension.option(), true));
    }

    /** The extension is off: its {@code <Plug>} command fails, as a {@code <Plug>} name nothing defines does. */
    static void require(Context c, String option) {
        if (!c.vim.extensions().on(option)) throw Context.fail();
    }
}
