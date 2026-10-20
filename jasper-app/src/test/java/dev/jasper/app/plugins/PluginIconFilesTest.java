package dev.jasper.app.plugins;

import static dev.jasper.app.plugins.AppContractTest.onEdt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.notifications.ActivityNotifier;
import dev.jasper.app.testsupport.PluginJars;
import dev.jasper.buddy.view.BuddyTestSupport;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginIconFilesTest {
    static final String SVG = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"16\" height=\"16\" viewBox=\"0 0 16 16\">"
        + "<rect width=\"16\" height=\"16\" rx=\"3\" fill=\"#3574F0\"/></svg>";
    @TempDir Path root;

    private PluginCandidate candidate(String id, String extraToml) throws Exception {
        String toml = PluginJars.descriptor(id, "1.0.0", "fix.Main")
            .replace("sdk = \">=0.1\"", "sdk = \">=0.15.1, <0.16\"") + extraToml;
        Path directory = root.resolve(id);
        Path jar = PluginJars.build(directory, "main.jar", toml, Map.of(), List.of());
        return new PluginCandidate(DescriptorParser.parse(toml), directory, List.of(jar), PluginCandidate.Origin.USER);
    }

    @Test void zeroByteSvgCannotAbortTheCatalogOrItsHealthyNeighbor() throws Exception {
        assertEmptyArtCatalog("svg");
    }

    @Test void zeroBytePngCannotAbortTheCatalogOrItsHealthyNeighbor() throws Exception {
        assertEmptyArtCatalog("png");
    }

    private void assertEmptyArtCatalog(String format) throws Exception {
        var empty = candidate("dev.example.empty." + format, "icon = \"icons/empty." + format + "\"\nicon_hue = \"blue\"\n");
        PluginJars.addResource(empty.jars().getFirst(), "icons/empty." + format, "");
        var healthy = candidate("dev.example.healthy." + format, "icon = \"icons/healthy.svg\"\nicon_hue = \"green\"\n");
        PluginJars.addResource(healthy.jars().getFirst(), "icons/healthy.svg", SVG);
        var candidates = List.of(empty, healthy);
        var snapshot = PluginCatalog.compute(new PluginCatalog.Launch(candidates, List.of(), Map.of(), false),
            new PluginCatalog.Disk(candidates, Map.of(), Map.of()), Version.parse(dev.jasper.sdk.JasperSdk.VERSION), id -> 0, root);
        assertThat(snapshot.rows()).extracting(PluginRuntime.Row::id).containsExactly(empty.id(), healthy.id());
        assertThat(snapshot.rows().getFirst().icon()).as("empty decoration uses the generic fallback").isEmpty();
        assertThat(snapshot.rows().getLast().icon()).hasValueSatisfying(icon -> {
            assertThat(icon.identityArtwork().tileHue()).isEqualTo(dev.jasper.ui.model.Nodes.Hue.GREEN);
            assertThat(icon.artwork()).isSameAs(PluginIconFiles.read(healthy).orElseThrow().artwork());
            icon.artwork().prepare();
        });
        assertThat(PluginIconFiles.read(empty)).isEmpty();
    }

    @Test void cachedEmptySvgStaysAbsentAcrossNeutralToBlueMetadata() throws Exception {
        assertCachedEmptyArt("svg");
    }

    @Test void cachedEmptyPngStaysAbsentAcrossNeutralToBlueMetadata() throws Exception {
        assertCachedEmptyArt("png");
    }

    private void assertCachedEmptyArt(String format) throws Exception {
        var neutral = candidate("dev.example.cached.empty." + format, "icon = \"icons/empty." + format + "\"\n");
        PluginJars.addResource(neutral.jars().getFirst(), "icons/empty." + format, "");
        var blueDescriptor = DescriptorParser.parse(PluginJars.descriptor(neutral.id(), "1.0.0", "fix.Main")
            .replace("sdk = \">=0.1\"", "sdk = \">=0.15.1, <0.16\"")
            + "icon = \"icons/empty." + format + "\"\nicon_hue = \"blue\"\n");
        var blue = new PluginCandidate(blueDescriptor, neutral.directory(), neutral.jars(), neutral.origin());
        int before = PluginIconFiles.reads();
        var first = PluginIconFiles.read(neutral);
        var second = PluginIconFiles.read(blue);
        assertThat(first).isEmpty();
        assertThat(second).isEmpty();
        assertThat(PluginIconFiles.reads() - before).as("same key retains the raw absence").isEqualTo(1);
    }

    @Test void sameByteSameVersionReplacementUsesTheShownDescriptorsHueOutsideTheByteCache() throws Exception {
        var blue=candidate("dev.example.hue", "icon = \"icons/tool.svg\"\nicon_hue = \"blue\"\n");
        PluginJars.addResource(blue.jars().getFirst(),"icons/tool.svg",SVG);
        var greenDescriptor=DescriptorParser.parse(PluginJars.descriptor("dev.example.hue","1.0.0","fix.Main")+"icon = \"icons/tool.svg\"\nicon_hue = \"green\"\n");
        var green=new PluginCandidate(greenDescriptor,blue.directory(),blue.jars(),blue.origin());
        int before=PluginIconFiles.reads();var a=PluginIconFiles.read(blue).orElseThrow();var b=PluginIconFiles.read(green).orElseThrow();
        assertThat(a.artwork()).isSameAs(b.artwork());assertThat(a.data()).isEqualTo(b.data());assertThat(a).isNotEqualTo(b);
        assertThat(a.identityArtwork().tileHue()).isEqualTo(dev.jasper.ui.model.Nodes.Hue.BLUE);
        assertThat(b.identityArtwork().tileHue()).isEqualTo(dev.jasper.ui.model.Nodes.Hue.GREEN);
        assertThat(PluginIconFiles.reads()-before).isEqualTo(1);
        assertThat(a.identityArtwork()).isSameAs(a.identityArtwork());
        var launch=new PluginCatalog.Launch(List.of(blue),List.of(PluginStatus.of(blue,PluginStatus.State.ACTIVE,"")),PluginCatalog.versions(List.of(blue)),false);
        var state=Map.of(blue.id(),new PluginStateStore.Entry(true,java.util.Set.of(),false));
        var snapshot=PluginCatalog.compute(launch,new PluginCatalog.Disk(List.of(blue),Map.of(blue.id(),green),state),Version.parse(dev.jasper.sdk.JasperSdk.VERSION),id->0,root);
        var row=snapshot.rows().getFirst();assertThat(row.pendingInstall()).isTrue();
        assertThat(row.icon().orElseThrow().identityArtwork().tileHue()).isEqualTo(dev.jasper.ui.model.Nodes.Hue.GREEN);
        assertThat(row.icon().orElseThrow().data()).isEqualTo(a.data());
    }
    @Test void cachedBytesUseTheShownCandidatesSelectedInkAndDoNotInheritAPreviousPolicy() throws Exception {
        var original=candidate("dev.example.ink", "icon = \"icons/tool.svg\"\nicon_hue = \"blue\"\n");
        PluginJars.addResource(original.jars().getFirst(),"icons/tool.svg",SVG);
        var descriptor=DescriptorParser.parse(PluginJars.descriptor(original.id(),"1.0.0","fix.Main")+"icon = \"icons/tool.svg\"\nicon_hue = \"blue\"\nicon_selected_ink = \"foreground\"\n");
        var next=new PluginCandidate(descriptor,original.directory(),original.jars(),original.origin());
        int before=PluginIconFiles.reads();var a=PluginIconFiles.read(original).orElseThrow();var b=PluginIconFiles.read(next).orElseThrow();
        assertThat(a.artwork()).isSameAs(b.artwork());assertThat(a).isNotEqualTo(b);
        assertThat(a.identityArtwork().selectedInk()).isEqualTo(dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED);
        assertThat(b.identityArtwork().selectedInk()).isEqualTo(dev.jasper.ui.icons.IdentityArtwork.SelectedInk.FOREGROUND);
        assertThat(PluginIconFiles.read(original)).contains(a);assertThat(PluginIconFiles.reads()-before).isEqualTo(1);
        var launch=new PluginCatalog.Launch(List.of(original),List.of(PluginStatus.of(original,PluginStatus.State.ACTIVE,"")),PluginCatalog.versions(List.of(original)),false);
        var state=Map.of(original.id(),new PluginStateStore.Entry(true,java.util.Set.of(),false));
        var snapshot=PluginCatalog.compute(launch,new PluginCatalog.Disk(List.of(original),Map.of(original.id(),next),state),Version.parse(dev.jasper.sdk.JasperSdk.VERSION),id->0,root);
        assertThat(snapshot.rows().getFirst().icon()).contains(b);
    }

    @Test void readsTheNamedIconOnceAndGivesNoneWhenMissingOrTooLarge() throws Exception {
        var withIcon = candidate("dev.example.icon", "icon = \"icons/tool.svg\"\n");
        PluginJars.addResource(withIcon.jars().get(0), "icons/tool.svg", SVG);
        int before = PluginIconFiles.reads();
        var icon = PluginIconFiles.read(withIcon).orElseThrow();
        assertThat(icon.svg()).isTrue();
        assertThat(new String(icon.data(), StandardCharsets.UTF_8)).isEqualTo(SVG);
        assertThat(PluginIconFiles.read(withIcon)).contains(icon);
        assertThat(PluginIconFiles.reads() - before).as("read once per plugin version").isEqualTo(1);

        assertThat(PluginIconFiles.read(candidate("dev.example.missing", "icon = \"icons/none.png\"\n"))).isEmpty();
        var large = candidate("dev.example.large", "icon = \"icons/big.svg\"\n");
        PluginJars.addResource(large.jars().get(0), "icons/big.svg", "x".repeat(PluginIconFiles.MAX_BYTES + 1));
        assertThat(PluginIconFiles.read(large)).isEmpty();
        assertThat(PluginIconFiles.read(candidate("dev.example.plain", ""))).isEmpty();
    }

    @Test void theRuntimesRowsCarryTheIconTheHomepageAndTheExports() throws Exception {
        Path user = root.resolve("user");
        Path jar = PluginJars.build(user.resolve("dev.example.shown/jars"), "main.jar",
            PluginJars.descriptor("dev.example.shown", "1.0.0", "fix.Main")
                + "icon = \"icons/shown.svg\"\nhomepage = \"https://example.dev/shown\"\nexports = [\"dev.example.shown.api\", \"dev.example.shown.a\"]\n",
            Map.of(), List.of());
        PluginJars.addResource(jar, "icons/shown.svg", SVG);
        var runtime = new AtomicReference<PluginRuntime>();
        onEdt(() -> {
            runtime.set(new PluginRuntime(new PluginRuntime.Options(null, user, null, false, root.resolve("plugins.toml"),
                root.resolve("plugins.lock")), new ActivityNotifier(new BuddyTestSupport().companion(), () -> { }),
                PluginRuntime.SettingsReports.IGNORED, new dev.jasper.app.contributions.Contributions(),
                AppContractTest.headlessWindows(), new dev.jasper.app.terminals.TerminalRegistry()));
            runtime.get().start(Map.of(), true);
        });
        try {
            var outcome = new CompletableFuture<PluginRuntime.Outcome>();
            onEdt(() -> runtime.get().snapshot(outcome::complete));
            var row = outcome.get(10, TimeUnit.SECONDS).snapshot().rows().stream()
                .filter(candidate -> candidate.id().equals("dev.example.shown")).findFirst().orElseThrow();
            assertThat(row.homepage()).isEqualTo("https://example.dev/shown");
            assertThat(row.exports()).containsExactly("dev.example.shown.a", "dev.example.shown.api");
            assertThat(row.icon()).hasValueSatisfying(icon -> assertThat(icon.svg()).isTrue());
        } finally {
            var pending = new AtomicReference<List<CompletableFuture<?>>>();
            var pluginWorkers = PluginShutdownTestSupport.capture(runtime.get());
            onEdt(() -> pending.set(runtime.get().stop()));
            CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);
            pluginWorkers.await();
        }
    }
}
