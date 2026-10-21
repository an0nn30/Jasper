package dev.jasper.editor.markdown;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Controlled markup and immutable navigation metadata for one source snapshot. */
public record MarkdownResult(String html, List<BlockAnchor> blocks, Map<String, Integer> headings,
                             List<Link> links, List<Image> images) {
    /** Takes ownership by copying every collection. */
    public MarkdownResult {
        Objects.requireNonNull(html);
        blocks = List.copyOf(blocks);
        headings = Map.copyOf(headings);
        links = List.copyOf(links);
        images = List.copyOf(images);
    }

    /** Empty named marker and its half-open UTF-16 source range. */
    public record BlockAnchor(String id, int sourceStart, int sourceEnd) { }
    /** A generated integer identifier and the original, unevaluated destination. */
    public record Link(int id, String destination) { }
    /** A generated integer identifier, original destination and visible alternative text. */
    public record Image(int id, String destination, String alt) { }
}
