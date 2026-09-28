package dev.jasper.editor.vim;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * What the Vim engine needs from the Editor (spec 1.1). Plans V2 and V3 add saving, closing, tabs and actions.
 *
 * @param readClipboard reads {@code '+'} (the clipboard) or {@code '*'} (the selection, or the clipboard where there
 *     is none); empty when it holds no text or cannot be read
 * @param writeClipboard writes {@code '+'} or {@code '*'}
 */
public record VimHost(Function<Character, Optional<String>> readClipboard, BiConsumer<Character, String> writeClipboard) {
    /** @return a host with no clipboard: reads are empty and writes are dropped */
    public static VimHost detached() { return new VimHost(register -> Optional.empty(), (register, text) -> { }); }
}
