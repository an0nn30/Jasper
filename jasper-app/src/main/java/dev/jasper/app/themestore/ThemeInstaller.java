package dev.jasper.app.themestore;

import dev.jasper.app.appearance.ThemeCatalog;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Owns writes to the themes folder: lands Marketplace downloads, copies theme plugins from disk, uninstalls, and keeps
 * provenance. Every archive is checked with {@link ThemeCatalog#inspect} first; one without a usable theme is refused.
 * Blocking and synchronized; run it on the store's worker, never the EDT.
 */
final class ThemeInstaller {
    /** A refused or failed operation; the message is for the user. */
    static class InstallException extends Exception {
        InstallException(String message) { super(message); }
        InstallException(String message, Throwable cause) { super(message, cause); }
    }

    /** A disk install whose file name is taken; ask, then install again with {@code replace}. */
    static final class Conflict extends InstallException {
        private final Path existing;
        Conflict(Path existing) { super(existing.getFileName() + " is already installed."); this.existing = existing; }
        Path existing() { return existing; }
    }

    /** An installed archive and the themes it provides. */
    record Installed(Path file, List<String> themes) {
        Installed { themes = List.copyOf(themes); }
    }

    static final String DOWNLOADS = ".downloads";
    private final Path themes;
    private final Provenance provenance;

    ThemeInstaller(Path themes) {
        this.themes = themes.toAbsolutePath().normalize();
        this.provenance = new Provenance(this.themes);
    }

    Path folder() { return themes; }

    synchronized Map<String, Provenance.Entry> sources() { return provenance.read(); }

    /** Where {@code release} downloads to before it is checked; creates the downloads folder. */
    synchronized Path downloadTarget(MarketplaceClient.Release release) throws InstallException {
        try { return Files.createDirectories(themes.resolve(DOWNLOADS)).resolve(release.updateId() + ".part"); }
        catch (IOException failure) { throw new InstallException("Cannot write the themes folder: " + failure.getMessage(), failure); }
    }

    /** Deletes what earlier downloads left behind. */
    synchronized void clearDownloads() {
        Path folder = themes.resolve(DOWNLOADS);
        if (!Files.isDirectory(folder)) return;
        try (var files = Files.list(folder)) {
            for (Path file : files.toList()) Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // A stale part file is harmless; the next page opening tries again.
        }
    }

    /**
     * Installs a downloaded Marketplace release as {@code <xmlId>-<version><extension>}, records it and removes the older
     * file of the same plugin. The download is deleted either way.
     */
    synchronized Installed installDownload(Path download, String extension, Provenance.Entry source) throws InstallException {
        try {
            String ext = extension.toLowerCase(Locale.ROOT);
            if (!ext.equals(".jar") && !ext.equals(".zip")) throw new InstallException("Unsupported theme file type: " + extension);
            var inspection = dev.jasper.app.themeimport.LegacyThemeReader.inspect(download);
            if (inspection.themes().isEmpty()) throw new InstallException(source.name() + " has no themes Jasper can use." + detail(inspection));
            String name = safe(source.xmlId() + "-" + source.version()) + ext;
            Path target = themes.resolve(name);
            Files.move(download, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            var entries = new TreeMap<>(provenance.read());
            var older = new java.util.ArrayList<String>();
            for (var entry : List.copyOf(entries.entrySet())) {
                if (!entry.getValue().xmlId().equals(source.xmlId()) || entry.getKey().equals(name)) continue;
                older.add(entry.getKey());
                entries.remove(entry.getKey());
            }
            entries.put(name, source);
            provenance.write(entries);
            for (String old : older) {
                if (!removable(old)) continue;
                try { Files.deleteIfExists(themes.resolve(old)); } catch (IOException ignored) { }
            }
            return new Installed(target, inspection.themes());
        } catch (IOException failure) {
            throw new InstallException("Cannot install " + source.name() + ": " + failure.getMessage(), failure);
        } finally {
            try { Files.deleteIfExists(download); } catch (IOException ignored) { }
        }
    }

    /** Copies {@code source} in under its own name; throws {@link Conflict} when the name is taken and {@code replace} is false. */
    synchronized Installed installFromDisk(Path source, boolean replace) throws InstallException {
        String name = source.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);
        if (!lower.endsWith(".jar") && !lower.endsWith(".zip")) throw new InstallException(name + " is not a .jar or .zip theme plugin.");
        if (name.startsWith(".")) name = "_" + name.substring(1);
        var inspection = dev.jasper.app.themeimport.LegacyThemeReader.inspect(source);
        if (inspection.themes().isEmpty()) throw new InstallException(name + " has no themes Jasper can use." + detail(inspection));
        Path target = themes.resolve(name);
        if (Files.exists(target) && !replace) throw new Conflict(target);
        try {
            Files.createDirectories(themes);
            if (!Files.exists(target) || !Files.isSameFile(source, target)) {
                Path temp = Files.createDirectories(themes.resolve(DOWNLOADS)).resolve(name + ".part");
                try {
                    Files.copy(source, temp, StandardCopyOption.REPLACE_EXISTING);
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    Files.deleteIfExists(temp);
                }
            }
            var entries = new TreeMap<>(provenance.read());
            if (entries.remove(name) != null) provenance.write(entries);
            return new Installed(target, inspection.themes());
        } catch (IOException failure) {
            throw new InstallException("Cannot install " + name + ": " + failure.getMessage(), failure);
        }
    }

    /** Deletes an installed archive and its record. */
    synchronized void uninstall(Path file) throws InstallException {
        Path absolute = file.toAbsolutePath().normalize();
        if (!themes.equals(absolute.getParent())) throw new InstallException(file.getFileName() + " is not in the themes folder.");
        String leaf = absolute.getFileName().toString().toLowerCase(Locale.ROOT);
        if (leaf.startsWith(".") || !(leaf.endsWith(".jar") || leaf.endsWith(".zip")))
            throw new InstallException(file.getFileName() + " is not an installed theme plugin.");
        try {
            Files.deleteIfExists(absolute);
            var entries = new TreeMap<>(provenance.read());
            entries.remove(absolute.getFileName().toString());
            provenance.write(entries);
        } catch (IOException failure) {
            throw new InstallException("Cannot uninstall " + file.getFileName() + ": " + failure.getMessage(), failure);
        }
    }

    /** Whether a recorded name is an archive directly in the themes folder, so replacing its plugin may delete it. */
    private boolean removable(String name) {
        Path resolved = themes.resolve(name).normalize();
        String lower = name.toLowerCase(Locale.ROOT);
        return themes.equals(resolved.getParent()) && (lower.endsWith(".jar") || lower.endsWith(".zip"));
    }

    private static String detail(ThemeCatalog.Inspection inspection) {
        return inspection.problems().isEmpty() ? "" : " " + inspection.problems().getFirst();
    }

    /** A file name made only of letters, digits, dots, dashes and underscores, never hidden. */
    private static String safe(String text) {
        String name = text.replaceAll("[^A-Za-z0-9._-]", "_");
        return name.startsWith(".") ? "_" + name.substring(1) : name;
    }
}
