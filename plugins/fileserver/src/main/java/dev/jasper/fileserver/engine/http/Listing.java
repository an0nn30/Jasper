package dev.jasper.fileserver.engine.http;

import dev.jasper.fileserver.engine.Sizes;
import dev.jasper.fileserver.share.ShareEntry;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** A folder's HTML listing; a writable share adds an upload form and a Delete button per entry. */
final class Listing {
    static final String POLICY = "default-src 'none'; style-src 'unsafe-inline'; form-action 'self'; frame-ancestors 'none'; base-uri 'none'";
    private static final DateTimeFormatter MODIFIED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT);

    private Listing() { }

    static String render(List<String> segments, List<ShareEntry> entries, boolean writable, ZoneId zone) {
        String title = segments.isEmpty() ? "/" : "/" + String.join("/", segments) + "/";
        var out = new StringBuilder("<!doctype html>\n<html><head><meta charset=\"utf-8\">")
            .append("<meta name=\"viewport\" content=\"width=device-width\"><title>").append(HttpPaths.html(title)).append("</title>")
            .append("<style>body{font:14px system-ui,sans-serif;margin:24px}table{border-collapse:collapse}")
            .append("td,th{padding:4px 12px;text-align:left}td.size{text-align:right}form{margin:0}</style></head><body>\n")
            .append("<h1>").append(HttpPaths.html(title)).append("</h1>\n");
        if (writable)
            out.append("<form method=\"post\" enctype=\"multipart/form-data\"><input type=\"file\" name=\"file\" multiple> ")
                .append("<button>Upload</button></form>\n");
        out.append("<table><tr><th>Name</th><th>Size</th><th>Modified</th>").append(writable ? "<th></th>" : "").append("</tr>\n");
        if (!segments.isEmpty())
            out.append("<tr><td><a href=\"").append(HttpPaths.href(segments.subList(0, segments.size() - 1), true)).append("\">..</a></td>")
                .append("<td></td><td></td>").append(writable ? "<td></td>" : "").append("</tr>\n");
        for (ShareEntry entry : entries) {
            var path = new ArrayList<>(segments);
            path.add(entry.name());
            out.append("<tr><td><a href=\"").append(HttpPaths.href(path, entry.directory())).append("\">")
                .append(HttpPaths.html(entry.name())).append(entry.directory() ? "/" : "").append("</a></td>")
                .append("<td class=\"size\">").append(entry.directory() ? "" : Sizes.human(entry.size())).append("</td>")
                .append("<td>").append(MODIFIED.format(entry.modified().atZone(zone))).append("</td>");
            if (writable)
                out.append("<td><form method=\"post\"><input type=\"hidden\" name=\"_method\" value=\"DELETE\">")
                    .append("<input type=\"hidden\" name=\"name\" value=\"").append(HttpPaths.html(entry.name())).append("\">")
                    .append("<button>Delete</button></form></td>");
            out.append("</tr>\n");
        }
        return out.append("</table></body></html>\n").toString();
    }
}
