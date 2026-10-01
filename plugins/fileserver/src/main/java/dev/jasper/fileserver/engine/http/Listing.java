package dev.jasper.fileserver.engine.http;

import dev.jasper.fileserver.engine.Sizes;
import dev.jasper.fileserver.share.ShareEntry;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A folder's HTML listing. */
final class Listing {
    static final String POLICY = "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'";
    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private Listing() { }

    static String render(List<String> segments, List<ShareEntry> entries, ZoneId zone) {
        String title = segments.isEmpty() ? "/" : "/" + String.join("/", segments) + "/";
        var out = new StringBuilder("<!doctype html>\n<html><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width\"><title>").append(HttpPaths.html(title)).append("</title>")
            .append("<style>body{font:14px system-ui,sans-serif;margin:24px}table{border-collapse:collapse}")
            .append("td,th{padding:4px 12px;text-align:left}td.size{text-align:right}</style></head><body>\n")
            .append("<h1>").append(HttpPaths.html(title)).append("</h1>\n")
            .append("<table><tr><th>Name</th><th>Size</th><th>Modified</th></tr>\n");
        if (!segments.isEmpty())
            out.append("<tr><td><a href=\"").append(HttpPaths.href(segments.subList(0, segments.size() - 1), true)).append("\">..</a></td><td></td><td></td></tr>\n");
        for (ShareEntry entry : entries) {
            var path = new ArrayList<>(segments);
            path.add(entry.name());
            out.append("<tr><td><a href=\"").append(HttpPaths.href(path, entry.directory())).append("\">")
                .append(HttpPaths.html(entry.name())).append(entry.directory() ? "/" : "").append("</a></td>")
                .append("<td class=\"size\">").append(entry.directory() ? "" : Sizes.human(entry.size())).append("</td>")
                .append("<td>").append(MODIFIED.format(entry.modified().atZone(zone))).append("</td></tr>\n");
        }
        return out.append("</table></body></html>\n").toString();
    }
}
