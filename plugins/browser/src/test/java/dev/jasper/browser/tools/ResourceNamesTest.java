package dev.jasper.browser.tools;

import dev.jasper.browser.tools.SavePayload.Kind;
import java.net.URI;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The names Save Page As gives a page's resources in its {@code resources} folder (plan 7's Task 7, ruling F13), and the
 * folder it suggests for a page: an address's last path segment, percent-decoded, with only {@code [A-Za-z0-9._-]} kept,
 * no leading or trailing dot, at most 80 characters with the extension kept, {@code .css} or {@code .js} for a style or
 * script that lacks it, a web extension kept on anything else and {@code .bin} appended otherwise, a Windows device name
 * behind {@code _}, and each name unique without regard to case. Pure.
 */
class ResourceNamesTest {
    /** The name {@code url} gets as the first of a save. */
    private static String name(String url, Kind kind) {
        return new ResourceNames().name(URI.create(url), kind);
    }

    @Test void theLastSegmentIsDecodedAndOnlySafeCharactersAreKept() {
        assertThat(name("https://example.com/img/logo.png?v=2#top", Kind.OTHER)).isEqualTo("logo.png");
        assertThat(name("https://example.com/a/caf%C3%A9%20au%20lait.jpg", Kind.OTHER)).as("decoded, then each other character _")
            .isEqualTo("caf__au_lait.jpg");
        assertThat(name("https://example.com/a/%2Fetc%2Fpasswd.txt", Kind.OTHER)).as("a decoded slash is no separator")
            .isEqualTo("_etc_passwd.txt");
        assertThat(name("https://example.com/a/%FF%FE.png", Kind.OTHER)).as("bytes that are no UTF-8").isEqualTo("__.png");
        assertThat(name("https://example.com/", Kind.OTHER)).as("no segment").isEqualTo("resource.bin");
        assertThat(name("https://example.com", Kind.OTHER)).isEqualTo("resource.bin");
    }

    /** Ruling F13: neither {@code .} nor {@code ..} names the folder itself, and no name is hidden or loses a trailing dot. */
    @Test void dotsAreTrimmedAndADotNameIsResource() {
        assertThat(name("https://example.com/%2e%2e", Kind.OTHER)).isEqualTo("resource.bin");
        assertThat(name("https://example.com/a/..", Kind.OTHER)).isEqualTo("resource.bin");
        assertThat(name("https://example.com/.", Kind.STYLE)).isEqualTo("resource.css");
        assertThat(name("https://example.com/.htaccess", Kind.OTHER)).isEqualTo("htaccess.bin");
        assertThat(name("https://example.com/logo.", Kind.OTHER)).isEqualTo("logo.bin");
        assertThat(name("https://example.com/...app.js...", Kind.SCRIPT)).isEqualTo("app.js");
    }

    /** Ruling F13: what is no web resource's type keeps its name but gets {@code .bin}, so a double-click runs nothing. */
    @Test void onlyAWebExtensionIsKeptOnAnOtherResource() {
        assertThat(name("https://evil.example/run.exe", Kind.OTHER)).isEqualTo("run.exe.bin");
        assertThat(name("https://evil.example/run.js", Kind.OTHER)).as("js is a web extension").isEqualTo("run.js");
        assertThat(name("https://example.com/photo.JPEG", Kind.OTHER)).as("in any case").isEqualTo("photo.JPEG");
        assertThat(name("https://example.com/avatar", Kind.OTHER)).isEqualTo("avatar.bin");
        for (String extension : new String[] {"png", "jpg", "jpeg", "gif", "webp", "avif", "svg", "ico", "bmp", "css", "js", "mjs", "json",
                "woff", "woff2", "ttf", "otf", "eot", "mp4", "webm", "ogg", "ogv", "mp3", "wav", "m4a", "vtt", "txt", "xml"})
            assertThat(name("https://example.com/f." + extension, Kind.OTHER)).as(extension).isEqualTo("f." + extension);
        assertThat(name("https://example.com/f.html", Kind.OTHER)).isEqualTo("f.html.bin");
        assertThat(name("https://example.com/f.bat", Kind.OTHER)).isEqualTo("f.bat.bin");
    }

    @Test void aStyleOrScriptWithoutItsExtensionGetsIt() {
        assertThat(name("https://fonts.example/css2?family=Inter", Kind.STYLE)).isEqualTo("css2.css");
        assertThat(name("https://example.com/site.css", Kind.STYLE)).isEqualTo("site.css");
        assertThat(name("https://example.com/SITE.CSS", Kind.STYLE)).isEqualTo("SITE.CSS");
        assertThat(name("https://example.com/theme.less", Kind.STYLE)).isEqualTo("theme.less.css");
        assertThat(name("https://example.com/app", Kind.SCRIPT)).isEqualTo("app.js");
        assertThat(name("https://example.com/app.js", Kind.SCRIPT)).isEqualTo("app.js");
        assertThat(name("https://example.com/run.exe", Kind.SCRIPT)).isEqualTo("run.exe.js");
    }

    @Test void aLongNameIsCutToEightyCharactersKeepingItsExtension() {
        String stem = "a".repeat(200);
        String cut = name("https://example.com/" + stem + ".png", Kind.OTHER);
        assertThat(cut).hasSize(ResourceNames.LONGEST).endsWith("a.png").startsWith("aaaa");
        String style = name("https://example.com/" + stem, Kind.STYLE);
        assertThat(style).hasSize(ResourceNames.LONGEST).endsWith(".css");
        String other = name("https://example.com/" + stem + ".exe", Kind.OTHER);
        assertThat(other).as("the extension kept is the one the name ends with").hasSize(ResourceNames.LONGEST).endsWith("aaaa.bin");
    }

    @Test void aWindowsDeviceNameGetsAnUnderscore() {
        assertThat(name("https://example.com/CON.txt", Kind.OTHER)).isEqualTo("_CON.txt");
        assertThat(name("https://example.com/nul", Kind.STYLE)).isEqualTo("_nul.css");
        assertThat(name("https://example.com/com1.png", Kind.OTHER)).isEqualTo("_com1.png");
        assertThat(name("https://example.com/console.png", Kind.OTHER)).isEqualTo("console.png");
    }

    /** As APFS and NTFS compare names: {@code logo.PNG} and {@code logo.png} are one file, so the second is numbered. */
    @Test void eachNameIsUniqueWithoutRegardToCase() {
        var names = new ResourceNames();
        assertThat(names.name(URI.create("https://a.example/logo.PNG"), Kind.OTHER)).isEqualTo("logo.PNG");
        assertThat(names.name(URI.create("https://b.example/logo.png"), Kind.OTHER)).isEqualTo("logo-1.png");
        assertThat(names.name(URI.create("https://c.example/x/logo.png"), Kind.OTHER)).isEqualTo("logo-2.png");
        assertThat(names.name(URI.create("https://c.example/logo-1.png"), Kind.OTHER)).as("a name the numbering took is numbered too")
            .isEqualTo("logo-1-1.png");
        assertThat(names.name(URI.create("https://a.example/"), Kind.OTHER)).isEqualTo("resource.bin");
        assertThat(names.name(URI.create("https://b.example/"), Kind.OTHER)).isEqualTo("resource-1.bin");
        assertThat(new ResourceNames().name(URI.create("https://a.example/logo.png"), Kind.OTHER)).as("each save its own")
            .isEqualTo("logo.png");
    }

    /** The folder a save suggests: the page's host by the same rule, never its user name or password, else {@code page}. */
    @Test void theFolderIsNamedAfterTheHost() {
        assertThat(ResourceNames.folderFor(URI.create("https://user:pw@example.com/a"))).isEqualTo("example.com");
        assertThat(ResourceNames.folderFor(URI.create("https://news.example.co.uk:8443/a/b?c"))).isEqualTo("news.example.co.uk");
        assertThat(ResourceNames.folderFor(URI.create("file:///x"))).isEqualTo("page");
        assertThat(ResourceNames.folderFor(URI.create("about:blank"))).isEqualTo("page");
        assertThat(ResourceNames.folderFor(URI.create("data:text/html,hi"))).isEqualTo("page");
        assertThat(ResourceNames.folderFor(URI.create("http://[::1]:8080/"))).as("IPv6 without brackets").isEqualTo("__1");
        assertThat(ResourceNames.folderFor(URI.create("http://my_box.example:3000/"))).as("a host Java reads as a registry name")
            .isEqualTo("my_box.example");
        assertThat(ResourceNames.folderFor(URI.create("https://example.com./"))).isEqualTo("example.com");
        assertThat(ResourceNames.folderFor(URI.create("http://con/"))).isEqualTo("_con");
    }
}
