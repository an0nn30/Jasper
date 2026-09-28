package dev.jasper.app.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Random edits over real and awkward files: every accepted edit lands exactly, and nothing else changes. */
class TomlDocumentPropertyTest {
    private static final List<String> LEAVES = List.of("alpha", "beta", "gamma-2", "needs space", "dotted.key");
    private static final List<String> LITERALS = List.of("1", "-7", "2.5", "true", "\"text\"", "\"with \\\"quote\\\"\"",
        "[1, 2]", "[\"a\", \"b\"]", "'literal'");

    private static List<String> corpus() throws Exception {
        String example = Files.readString(Path.of(System.getProperty("jasper.projectDir")).resolve("config.example.toml"), StandardCharsets.UTF_8);
        return List.of(example,
            "# top\nname = 'x' # trailing\n[server]\nport = 8080\nhosts = [\n  'a', # first\n  'b',\n]\n\n[server.tls]\nenabled = true\n",
            "a.b.c = 1\n'quoted key' = \"v\"\n[t]\ninline = { x = 1, y = 'z' }\n[t.sub]\n\"dotted.name\" = 2\n",
            "x = 1\r\n[crlf]\r\ny = 2 # c\r\n",
            "",
            "# only comments\n",
            "key = \"\"\"\nmulti\nline\"\"\"\n[z]\nw = '''raw'''\n");
    }

    @Test void randomEditsLandExactlyAndLeaveEverythingElseAlone() throws Exception {
        for (String text : corpus()) for (long seed = 1; seed <= 20; seed++) {
            var random = new Random(seed);
            TomlDocument document = TomlDocument.parse(text);
            var model = new LinkedHashMap<>(TomlDocument.leaves(text));
            int applied = 0, refused = 0;
            for (int step = 0; step < 40; step++) {
                var existing = new ArrayList<>(model.keySet());
                if (!existing.isEmpty() && random.nextInt(4) == 0) {
                    List<String> path = existing.get(random.nextInt(existing.size()));
                    try { document = document.remove(path); model.remove(path); applied++; }
                    catch (IllegalArgumentException refusal) { refused++; }
                } else {
                    List<String> path = randomPath(random, existing);
                    if (conflicts(path, model.keySet())) continue;
                    String literal = LITERALS.get(random.nextInt(LITERALS.size()));
                    try {
                        document = document.set(path, literal);
                        model.put(path, TomlDocument.leaves("v = " + literal).get(List.of("v")));
                        applied++;
                    } catch (IllegalArgumentException refusal) { refused++; }
                }
                assertThat(TomlDocument.leaves(document.text())).as("seed %d step %d:%n%s", seed, step, document.text()).isEqualTo(model);
            }
            assertThat(applied).as("seed %d: applied %d, refused %d", seed, applied, refused).isGreaterThan(refused * 4);
        }
    }

    private static List<String> randomPath(Random random, List<List<String>> existing) {
        if (!existing.isEmpty() && random.nextBoolean()) return existing.get(random.nextInt(existing.size()));
        List<String> parent = switch (random.nextInt(3)) {
            case 0 -> List.of();
            case 1 -> List.of("zeta", "sub");
            default -> {
                if (existing.isEmpty()) yield List.of("zeta");
                List<String> sample = existing.get(random.nextInt(existing.size()));
                yield sample.subList(0, sample.size() - 1);
            }
        };
        var path = new ArrayList<>(parent);
        path.add(LEAVES.get(random.nextInt(LEAVES.size())));
        return List.copyOf(path);
    }

    /** A leaf cannot become a table, nor a table a leaf. */
    private static boolean conflicts(List<String> path, Collection<List<String>> keys) {
        for (List<String> key : keys)
            if (!key.equals(path) && (SettingsSchema.startsWith(key, path) || SettingsSchema.startsWith(path, key))) return true;
        return false;
    }
}
