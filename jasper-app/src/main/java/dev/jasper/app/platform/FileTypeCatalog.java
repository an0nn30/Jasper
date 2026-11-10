package dev.jasper.app.platform;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The built-in file types Jasper can claim, read from {@code file-types.tsv}: each extension with its macOS
 * UTIs and Linux MIME types, and the macOS types Jasper declares because the system lacks them. The packaging
 * build reads the same file.
 */
final class FileTypeCatalog {

    /** A macOS type Jasper declares (imports) itself. Extensions carry no dot. */
    record Import(String identifier, String conformsTo, List<String> extensions, String description) {
        Import {
            extensions = List.copyOf(extensions);
        }
    }

    private record Types(List<String> mac, List<String> linux) {}

    private static final String RESOURCE = "file-types.tsv";
    private static final class Holder {
        static final FileTypeCatalog BUILT_IN = load();
    }

    private final Map<String, Types> types;
    private final List<Import> imports;

    private FileTypeCatalog(Map<String, Types> types, List<Import> imports) {
        this.types = types;
        this.imports = List.copyOf(imports);
    }

    static FileTypeCatalog builtIn() {
        return Holder.BUILT_IN;
    }

    private static FileTypeCatalog load() {
        try (InputStream in = FileTypeCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("missing resource " + RESOURCE);
            }
            return parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static FileTypeCatalog parse(String text) {
        Map<String, Types> types = new LinkedHashMap<>();
        List<Import> imports = new java.util.ArrayList<>();
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int number = i + 1;
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] cols = line.split("\t", -1);
            if (cols[0].equals("import")) {
                if (cols.length != 5 || cols[1].isBlank() || cols[2].isBlank() || cols[3].isBlank()) {
                    throw bad(number, line, "an import line needs 5 columns");
                }
                imports.add(new Import(cols[1], cols[2], split(cols[3]), cols[4]));
            } else {
                if (cols.length != 3 || !cols[0].startsWith(".") || cols[0].length() < 2) {
                    throw bad(number, line, "a type line needs a dotted extension and 3 columns");
                }
                if (types.put(cols[0], new Types(split(cols[1]), split(cols[2]))) != null) {
                    throw bad(number, line, "duplicate extension " + cols[0]);
                }
            }
        }
        Set<String> used = new LinkedHashSet<>();
        types.values().forEach(t -> used.addAll(t.mac()));
        for (Import imp : imports) {
            if (!used.contains(imp.identifier())) {
                throw new IllegalArgumentException("import " + imp.identifier() + " is used by no type line");
            }
        }
        return new FileTypeCatalog(types, imports);
    }

    private static IllegalArgumentException bad(int number, String line, String why) {
        return new IllegalArgumentException("file types, line " + number + ": " + why + ": " + line);
    }

    private static List<String> split(String column) {
        if (column.isBlank()) {
            return List.of();
        }
        List<String> out = new java.util.ArrayList<>();
        for (String part : column.split(",")) {
            if (!part.isBlank()) {
                out.add(part.strip());
            }
        }
        return List.copyOf(out);
    }

    /** The extensions, each with its dot, in file order. */
    List<String> extensions() {
        return List.copyOf(types.keySet());
    }

    List<String> macTypes(String extension) {
        Types t = types.get(extension);
        return t == null ? List.of() : t.mac();
    }

    List<String> linuxTypes(String extension) {
        Types t = types.get(extension);
        return t == null ? List.of() : t.linux();
    }

    List<Import> macImports() {
        return imports;
    }
}
