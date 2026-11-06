package dev.jasper.browser.adblock;

import java.util.Optional;

/** What a request loads, by the names a WebKit content-blocker list gives in a trigger's {@code resource-type}. */
public enum ResourceKind {
    /** A frame's document: {@code document}. */
    DOCUMENT("document"),
    /** An image: {@code image}. */
    IMAGE("image"),
    /** A style sheet: {@code style-sheet}. */
    STYLE_SHEET("style-sheet"),
    /** A script: {@code script}. */
    SCRIPT("script"),
    /** A font: {@code font}. */
    FONT("font"),
    /** What a page's script requests, and any load of no other kind: {@code raw}. */
    RAW("raw"),
    /** An SVG document: {@code svg-document}. */
    SVG_DOCUMENT("svg-document"),
    /** Audio or video: {@code media}. */
    MEDIA("media"),
    /** A window a page opens: {@code popup}. */
    POPUP("popup"),
    /** A ping or a report the page sends: {@code ping}. */
    PING("ping"),
    /** A script's {@code fetch} or XMLHttpRequest: {@code fetch}. */
    FETCH("fetch"),
    /** A WebSocket: {@code websocket}. */
    WEBSOCKET("websocket"),
    /** Any load of no other kind: {@code other}. */
    OTHER("other");

    private final String webKitName;

    ResourceKind(String webKitName) {
        this.webKitName = webKitName;
    }

    /**
     * The name a list gives this kind.
     *
     * @return the name, such as {@code style-sheet}
     */
    public String webKitName() {
        return webKitName;
    }

    /**
     * The kind a list names.
     *
     * @param webKitName the name, exactly as WebKit writes it
     * @return the kind; empty for any other name
     */
    public static Optional<ResourceKind> named(String webKitName) {
        for (ResourceKind kind : values()) if (kind.webKitName.equals(webKitName)) return Optional.of(kind);
        return Optional.empty();
    }
}
