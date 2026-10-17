package dev.jasper.app.presentation;

import dev.jasper.ui.theme.JasperTheme;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the resolver knows of one presentation provider (spec 4.4): the SHA-256 digest of its kit file, then each declared
 * theme file, then each font file, in declared order; its first problem; and, when valid, its parsed kit, so the winner is
 * not parsed twice.
 *
 * @param digest the look's identity, lower-case hex
 * @param problem the first kit validation failure, or empty
 * @param parsed the kit when it is valid
 */
public record PresentationFacts(String digest, Optional<String> problem, Optional<KitParser.Parsed> parsed) {
    /** The largest theme file: 1 MiB, as for imported themes. */
    public static final int MAX_THEME_BYTES = 1 << 20;

    /**
     * A provider's files.
     *
     * @param id the provider's id
     * @param kitPath the jar path of its kit file
     * @param files reads its jars
     * @param themePaths the jar paths of its theme files, in declared order
     */
    public record Source(String id, String kitPath, KitParser.Files files, List<String> themePaths) {
        /** Copies the list. */
        public Source {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(kitPath, "kitPath"); Objects.requireNonNull(files, "files");
            themePaths = List.copyOf(themePaths);
        }
    }

    /** Every part is required. */
    public PresentationFacts {
        Objects.requireNonNull(digest, "digest"); Objects.requireNonNull(problem, "problem"); Objects.requireNonNull(parsed, "parsed");
    }

    /** The facts of {@code source}; never throws. */
    public static PresentationFacts read(Source source) {
        MessageDigest digest = sha256();
        Optional<byte[]> kit = read(source.files(), source.kitPath(), KitParser.MAX_KIT_BYTES);
        kit.ifPresent(digest::update);
        var themes = new LinkedHashMap<String, Optional<JasperTheme>>();
        for (String path : source.themePaths()) {
            String file = path.substring(path.lastIndexOf('/') + 1);
            String id = file.endsWith(".json") ? file.substring(0, file.length() - ".json".length()) : file;
            Optional<byte[]> bytes = read(source.files(), path, MAX_THEME_BYTES);
            bytes.ifPresent(digest::update);
            themes.put(id, bytes.filter(content -> content.length <= MAX_THEME_BYTES).flatMap(content -> theme(content, id)));
        }
        if (kit.isEmpty())
            return new PresentationFacts(hex(digest), Optional.of("presentation: " + source.kitPath() + " is not in the plugin's jars"), Optional.empty());
        int slash = source.kitPath().lastIndexOf('/');
        String directory = slash < 0 ? "" : source.kitPath().substring(0, slash + 1);
        try {
            var parsed = KitParser.parse(kit.get(), new KitParser.Context(source.id(), directory, source.files(), themes));
            for (FontFile font : parsed.faces().files()) digest.update(font.bytes());
            return new PresentationFacts(hex(digest), Optional.empty(), Optional.of(parsed));
        } catch (KitParser.Invalid invalid) {
            return new PresentationFacts(hex(digest), Optional.of(invalid.getMessage()), Optional.empty());
        } catch (StackOverflowError deep) {
            // Rulings 13: a reader that ran out of stack must not stop start-up either.
            return new PresentationFacts(hex(digest), Optional.of("kit file: not valid TOML"), Optional.empty());
        } catch (RuntimeException defect) {
            // A defect in a reader must never stop start-up: the look is skipped with what went wrong.
            return new PresentationFacts(hex(digest), Optional.of("kit file: could not be read: " + defect), Optional.empty());
        }
    }

    private static Optional<byte[]> read(KitParser.Files files, String path, int max) {
        try { return files.read(path, max); }
        catch (RuntimeException unreadable) { return Optional.empty(); }
    }

    private static Optional<JasperTheme> theme(byte[] bytes, String id) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return Optional.of(JasperTheme.parse(text.startsWith("\uFEFF") ? text.substring(1) : text, id));
        } catch (CharacterCodingException | RuntimeException | StackOverflowError invalid) { return Optional.empty(); }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String hex(MessageDigest digest) { return HexFormat.of().formatHex(digest.digest()); }
}
