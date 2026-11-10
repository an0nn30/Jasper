package dev.jasper.app.platform;

import java.util.List;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The text formats of Linux file associations: shared-mime-info's {@code globs2} and {@code subclasses}, the {@code mimeapps.list} the
 * MIME Applications Associations spec defines, the shared-mime-info package Jasper writes for its own types, and
 * desktop entries. Edits keep every byte they do not change, comments and line ends included.
 */
final class LinuxMime {
    /** The type Jasper declares for an extension the system does not know is {@code text/x-jasper-<ext>}. */
    static final String OWN_TYPE_PREFIX = "text/x-jasper-";
    static final String DEFAULT_APPLICATIONS = "Default Applications";
    static final String ADDED_ASSOCIATIONS = "Added Associations";
    private static final Pattern OWN_MIME_TYPE = Pattern.compile(
            "<mime-type type=\"" + Pattern.quote(OWN_TYPE_PREFIX) + "([a-z0-9_+-]{1,16})\">");

    private LinuxMime() { }

    /**
     * The type of {@code *.<extension>} in {@code globs2} files, each line {@code weight:mime:pattern[:flags]}.
     *
     * @param globs2Texts the files' texts, the most important first
     * @param extension without its dot; matched ignoring case, except by a pattern with the {@code cs} flag
     * @return the type of the highest weight; on a tie, the earlier file's (and within a file, the earlier line's)
     */
    static Optional<String> globType(List<String> globs2Texts, String extension) {
        String pattern = "*." + extension;
        String best = null;
        int bestWeight = Integer.MIN_VALUE;
        for (String text : globs2Texts) {
            for (String line : text.split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split(":", -1);
                if (fields.length < 3 || fields[1].isBlank()) continue;
                int weight;
                try {
                    weight = Integer.parseInt(fields[0].strip());
                } catch (NumberFormatException notAWeight) {
                    continue;
                }
                boolean caseSensitive = fields.length > 3 && List.of(fields[3].split(",")).contains("cs");
                boolean matches = caseSensitive ? fields[2].equals(pattern) : fields[2].equalsIgnoreCase(pattern);
                if (matches && weight > bestWeight) {
                    best = fields[1].strip();
                    bestWeight = weight;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Whether {@code type} inherits, through any number of steps, from {@code ancestor} in shared-mime-info's
     * {@code subclasses} files, each line {@code <type> <parent>}. A cycle ends the search; a type is not its own
     * ancestor.
     *
     * @param subclassesTexts the files' texts
     */
    static boolean inheritsFrom(List<String> subclassesTexts, String type, String ancestor) {
        java.util.Map<String, java.util.Set<String>> parents = new java.util.HashMap<>();
        for (String text : subclassesTexts) {
            for (String line : text.split("\\R")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.strip().split("\\s+");
                if (fields.length != 2) continue;
                parents.computeIfAbsent(fields[0], key -> new java.util.LinkedHashSet<>()).add(fields[1]);
            }
        }
        java.util.Set<String> seen = new java.util.HashSet<>(java.util.Set.of(type));
        java.util.ArrayDeque<String> next = new java.util.ArrayDeque<>(java.util.Set.of(type));
        while (!next.isEmpty()) {
            for (String parent : parents.getOrDefault(next.poll(), java.util.Set.of())) {
                if (parent.equals(ancestor)) return true;
                if (seen.add(parent)) next.add(parent);
            }
        }
        return false;
    }

    /**
     * Removes {@code desktopId} from {@code type}'s list in {@code [Default Applications]}, and the whole line when its
     * list empties. Every other byte is kept.
     */
    static String withoutDefault(String mimeapps, String type, String desktopId) {
        return withoutEntry(mimeapps, DEFAULT_APPLICATIONS, type, desktopId);
    }

    /**
     * Removes {@code desktopId} from {@code type}'s list in {@code [Added Associations]}, and the whole line when its
     * list empties. Every other byte is kept.
     */
    static String withoutAddedAssociation(String mimeapps, String type, String desktopId) {
        return withoutEntry(mimeapps, ADDED_ASSOCIATIONS, type, desktopId);
    }

    private static String withoutEntry(String mimeapps, String groupName, String type, String desktopId) {
        StringBuilder out = new StringBuilder(mimeapps.length());
        String group = null;
        for (String line : lines(mimeapps)) {
            String body = body(line);
            Optional<String> header = header(body);
            if (header.isPresent()) group = header.get();
            Optional<Entry> entry = header.isPresent() || !groupName.equals(group) ? Optional.empty() : entry(body, type);
            if (entry.isEmpty() || !entry.get().ids().contains(desktopId)) {
                out.append(line);
                continue;
            }
            List<String> rest = entry.get().ids().stream().filter(id -> !id.equals(desktopId)).toList();
            if (rest.isEmpty()) continue;
            out.append(entry.get().prefix()).append(String.join(";", rest)).append(entry.get().trailingSemicolon() ? ";" : "")
                    .append(line, body.length(), line.length());
        }
        return out.toString();
    }

    /**
     * Puts {@code desktopId} first in {@code type}'s list in {@code [Added Associations]}, once, adding the line after
     * the group's header when missing and the group at the end when missing.
     */
    static String withAddedAssociation(String mimeapps, String type, String desktopId) {
        String newline = newline(mimeapps);
        List<String> lines = new java.util.ArrayList<>(lines(mimeapps));
        int headerIndex = -1;
        for (int i = 0; i < lines.size() && headerIndex < 0; i++) {
            if (header(body(lines.get(i))).filter(ADDED_ASSOCIATIONS::equals).isPresent()) headerIndex = i;
        }
        String added = type + "=" + desktopId + ";" + newline;
        if (headerIndex < 0) {
            StringBuilder out = new StringBuilder(mimeapps);
            if (!out.isEmpty()) {
                if (!endsWithNewline(out)) out.append(newline);
                out.append(newline);
            }
            return out.append('[').append(ADDED_ASSOCIATIONS).append(']').append(newline).append(added).toString();
        }
        for (int i = headerIndex + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String body = body(line);
            if (header(body).isPresent()) break;
            Optional<Entry> entry = entry(body, type);
            if (entry.isEmpty()) continue;
            List<String> ids = new java.util.ArrayList<>(List.of(desktopId));
            entry.get().ids().stream().filter(id -> !id.equals(desktopId)).forEach(ids::add);
            if (entry.get().ids().equals(ids)) return mimeapps;
            lines.set(i, entry.get().prefix() + String.join(";", ids) + ";" + line.substring(body.length()));
            return String.join("", lines);
        }
        String header = lines.get(headerIndex);
        if (body(header).length() == header.length()) lines.set(headerIndex, header + newline);
        lines.add(headerIndex + 1, added);
        return String.join("", lines);
    }

    /** The extensions, without dots, whose {@code text/x-jasper-} types {@code packageXml} declares. */
    static SortedSet<String> declaredExtensions(String packageXml) {
        SortedSet<String> extensions = new TreeSet<>();
        Matcher matcher = OWN_MIME_TYPE.matcher(packageXml);
        while (matcher.find()) extensions.add(matcher.group(1));
        return extensions;
    }

    /**
     * Jasper's shared-mime-info package: a {@code text/x-jasper-<ext>} type, a kind of {@code text/plain}, for each
     * extension. Extensions carry no dot and use only {@code a-z 0-9 _ + -}, which XML takes as they are.
     */
    static String packageXml(SortedSet<String> extensions) {
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<mime-info xmlns=\"http://www.freedesktop.org/standards/shared-mime-info\">\n");
        for (String extension : extensions) {
            if (!extension.matches("[a-z0-9_+-]{1,16}")) throw new IllegalArgumentException("not an extension: " + extension);
            xml.append("  <mime-type type=\"").append(OWN_TYPE_PREFIX).append(extension).append("\">")
                    .append("<sub-class-of type=\"text/plain\"/>")
                    .append("<comment>.").append(extension).append(" file</comment>")
                    .append("<glob pattern=\"*.").append(extension).append("\"/></mime-type>\n");
        }
        return xml.append("</mime-info>\n").toString();
    }

    /** The unlocalised {@code Name=} of a desktop entry's {@code [Desktop Entry]} group. */
    static Optional<String> desktopName(String desktopEntry) {
        String group = null;
        for (String line : lines(desktopEntry)) {
            String body = body(line);
            Optional<String> header = header(body);
            if (header.isPresent()) {
                group = header.get();
                continue;
            }
            int equals = body.indexOf('=');
            if ("Desktop Entry".equals(group) && equals > 0 && body.substring(0, equals).strip().equals("Name")) {
                String name = body.substring(equals + 1).strip();
                return name.isEmpty() ? Optional.empty() : Optional.of(name);
            }
        }
        return Optional.empty();
    }

    /**
     * One {@code type=id;id;} line.
     *
     * @param prefix the line up to its first id: the key, {@code =} and any spaces
     * @param ids the ids, without blanks
     * @param trailingSemicolon whether the list ends with {@code ;}
     */
    private record Entry(String prefix, List<String> ids, boolean trailingSemicolon) { }

    private static Optional<Entry> entry(String body, String type) {
        int equals = body.indexOf('=');
        if (equals <= 0 || !body.substring(0, equals).strip().equals(type)) return Optional.empty();
        String value = body.substring(equals + 1);
        String list = value.stripLeading();
        String prefix = body.substring(0, body.length() - list.length());
        List<String> ids = java.util.Arrays.stream(list.split(";")).map(String::strip).filter(id -> !id.isEmpty()).toList();
        return Optional.of(new Entry(prefix, ids, list.strip().endsWith(";")));
    }

    private static Optional<String> header(String body) {
        String text = body.strip();
        return text.startsWith("[") && text.endsWith("]") ? Optional.of(text.substring(1, text.length() - 1)) : Optional.empty();
    }

    /** The lines of {@code text}, each with its own line end ({@code \r\n}, {@code \n} or {@code \r}; the last may have none). */
    private static List<String> lines(String text) {
        List<String> lines = new java.util.ArrayList<>();
        int start = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\r') {
                int end = c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n' ? i + 2 : i + 1;
                lines.add(text.substring(start, end));
                start = end;
                i = end - 1;
            }
        }
        if (start < text.length()) lines.add(text.substring(start));
        return lines;
    }

    /** A line without its line end. */
    private static String body(String line) {
        int end = line.length();
        while (end > 0 && (line.charAt(end - 1) == '\n' || line.charAt(end - 1) == '\r')) end--;
        return line.substring(0, end);
    }

    /** The text's first line end, else {@code \n}. */
    private static String newline(String text) {
        for (String line : lines(text)) {
            String body = body(line);
            if (body.length() < line.length()) return line.substring(body.length());
        }
        return "\n";
    }

    private static boolean endsWithNewline(CharSequence text) {
        char last = text.charAt(text.length() - 1);
        return last == '\n' || last == '\r';
    }
}
