package dev.jasper.app.plugins;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.tomlj.Toml;
import static org.assertj.core.api.Assertions.*;

class TomlTextTest {
    @Test void writesScalarsListsAndNestedTablesThatParseBackToTheSameValues() {
        var proxy = new LinkedHashMap<String, Object>(); proxy.put("port", 22L); proxy.put("host", "a\"b\\c\n");
        var table = new LinkedHashMap<String, Object>();
        table.put("name", "x"); table.put("count", 3L); table.put("ratio", 0.5); table.put("on", true);
        table.put("hosts", List.of("a", "b")); table.put("proxy", proxy);
        String text = TomlText.write(table);
        var parsed = Toml.parse(text);
        assertThat(parsed.errors()).isEmpty();
        assertThat(ConfigLoaderAccess.freeze(parsed)).isEqualTo(table);
        assertThat(text).startsWith("name = \"x\"\n").contains("\n[proxy]\n");
        assertThat(TomlText.write(Map.of())).isEmpty();
    }
}
