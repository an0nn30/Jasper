package dev.jasper.app.plugins;

/** Test access to the loader's table freezing. */
final class ConfigLoaderAccess {
    private ConfigLoaderAccess() { }
    static java.util.Map<String, Object> freeze(org.tomlj.TomlParseResult parsed) { return dev.jasper.app.config.ConfigLoader.freeze(parsed); }
}
