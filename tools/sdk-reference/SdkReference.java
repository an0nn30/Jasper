import com.sun.source.doctree.AttributeTree;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.EndElementTree;
import com.sun.source.doctree.EntityTree;
import com.sun.source.doctree.ErroneousTree;
import com.sun.source.doctree.LinkTree;
import com.sun.source.doctree.LiteralTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.ReturnTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SinceTree;
import com.sun.source.doctree.StartElementTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import com.sun.source.doctree.UnknownInlineTagTree;
import com.sun.source.doctree.ValueTree;
import com.sun.source.doctree.DeprecatedTree;
import com.sun.source.util.DocTreePath;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.IntersectionType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.UnionType;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.DocumentationTool;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import jdk.javadoc.doclet.Doclet;
import jdk.javadoc.doclet.DocletEnvironment;
import jdk.javadoc.doclet.Reporter;

/**
 * Generates {@code docs/sdk-reference.html}, the plugin SDK reference: the guide pages in
 * {@code tools/sdk-reference/guide} followed by an API reference read from the Javadoc of the SDK
 * ({@code dev.jasper.sdk.*}), its testkit ({@code dev.jasper.sdk.testing}) and the API packages the
 * bundled plugins export ({@code plugins/<name>/src/main/java/dev/jasper/<name>/api}).
 *
 * <p>Usage: {@code java tools/sdk-reference/SdkReference.java <repository root> <output.html> [--check]}.
 * With {@code --check} nothing is written: the run fails when the output file differs from what the
 * sources generate. The run fails, naming each problem, on an unresolved {@code {@link}} in the
 * Javadoc or a guide page, an anchor no element carries, or a block tag written mid-line (which
 * Javadoc reads as text). {@code ./gradlew sdkReference} and {@code verifySdkReference} run it.
 */
public final class SdkReference {
    static Path root;
    static Path output;
    static boolean checkOnly;
    static final List<String> problems = new ArrayList<>();
    static final List<String> undocumented = new ArrayList<>();
    static String result;

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3 || (args.length == 3 && !args[2].equals("--check"))) {
            System.err.println("usage: java tools/sdk-reference/SdkReference.java <repository root> <output.html> [--check]");
            System.exit(2);
        }
        root = Path.of(args[0]).toAbsolutePath().normalize();
        output = Path.of(args[1]).toAbsolutePath().normalize();
        checkOnly = args.length == 3;

        List<Path> sourceRoots = new ArrayList<>(List.of(root.resolve("jasper-sdk/src/main/java"), root.resolve("jasper-sdk-testkit/src/main/java")));
        List<Path> files = new ArrayList<>();
        for (Path sourceRoot : sourceRoots) files.addAll(javaFiles(sourceRoot, true));
        for (Plugins.Export export : Plugins.exports(root)) {
            sourceRoots.add(export.sourceRoot());
            files.addAll(javaFiles(export.packageDirectory(), false));
        }
        DocumentationTool tool = ToolProvider.getSystemDocumentationTool();
        try (StandardJavaFileManager files0 = tool.getStandardFileManager(null, Locale.ROOT, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> units = files0.getJavaFileObjectsFromPaths(files);
            String sourcePath = sourceRoots.stream().map(Path::toString).collect(Collectors.joining(java.io.File.pathSeparator));
            List<String> options = List.of("-quiet", "-encoding", "UTF-8", "--source-path", sourcePath);
            boolean ran = tool.getTask(null, files0, null, Generator.class, options, units).call();
            if (!ran) fail("javadoc could not read the sources");
        }
        if (!problems.isEmpty()) {
            System.err.println("The SDK reference has " + problems.size() + " problem(s):");
            problems.forEach(problem -> System.err.println("  " + problem));
            System.exit(1);
        }
        if (!undocumented.isEmpty() && System.getProperty("sdkReference.listUndocumented") != null) {
            System.err.println(undocumented.size() + " public element(s) have no description:");
            undocumented.forEach(element -> System.err.println("  " + element));
        }
        if (checkOnly) {
            String present = Files.isRegularFile(output) ? Files.readString(output, StandardCharsets.UTF_8).replace("\r\n", "\n") : "";
            if (!present.equals(result)) {
                System.err.println(root.relativize(output) + " is stale: run ./gradlew sdkReference");
                System.exit(1);
            }
            return;
        }
        Files.createDirectories(output.getParent());
        Files.writeString(output, result, StandardCharsets.UTF_8);
        System.out.println("Wrote " + root.relativize(output));
    }

    static void fail(String problem) { problems.add(problem); }

    static List<Path> javaFiles(Path directory, boolean recursive) throws IOException {
        if (!Files.isDirectory(directory)) throw new IOException("missing source directory " + directory);
        try (Stream<Path> stream = recursive ? Files.walk(directory) : Files.list(directory)) {
            return stream.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }

    /** The doclet: builds the model, renders the page and leaves it in {@link #result}. */
    public static final class Generator implements Doclet {
        @Override public void init(Locale locale, Reporter reporter) { }
        @Override public String getName() { return "SdkReference"; }
        @Override public Set<? extends Option> getSupportedOptions() { return Set.of(); }
        @Override public SourceVersion getSupportedSourceVersion() { return SourceVersion.latest(); }
        @Override public boolean run(DocletEnvironment environment) {
            try {
                result = new Page(environment).render();
                return true;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    /** The bundled plugins' manifests: which plugin exports which API package. */
    static final class Plugins {
        record Export(String packageName, String pluginId, String pluginName, String version, Path sourceRoot, Path packageDirectory) { }

        static List<Export> exports(Path root) throws IOException {
            List<Export> exports = new ArrayList<>();
            try (Stream<Path> plugins = Files.list(root.resolve("plugins"))) {
                for (Path plugin : plugins.sorted().toList()) {
                    Path manifest = plugin.resolve("src/main/resources/plugin.toml");
                    if (!Files.isRegularFile(manifest)) continue;
                    String text = Files.readString(manifest, StandardCharsets.UTF_8);
                    String id = value(text, "id"), name = value(text, "name"), version = value(text, "version");
                    Matcher list = Pattern.compile("(?m)^exports\\s*=\\s*\\[([^\\]]*)]").matcher(text);
                    if (!list.find()) continue;
                    Matcher entry = Pattern.compile("\"([^\"]+)\"").matcher(list.group(1));
                    while (entry.find()) {
                        String packageName = entry.group(1);
                        Path sourceRoot = plugin.resolve("src/main/java");
                        exports.add(new Export(packageName, id, name, version, sourceRoot, sourceRoot.resolve(packageName.replace('.', '/'))));
                    }
                }
            }
            exports.sort(Comparator.comparing(Export::packageName));
            return exports;
        }

        static String value(String toml, String key) {
            Matcher matcher = Pattern.compile("(?m)^" + key + "\\s*=\\s*\"([^\"]*)\"").matcher(toml);
            return matcher.find() ? matcher.group(1) : "";
        }
    }

    // ------------------------------------------------------------------------------------------------------------
    // The page
    // ------------------------------------------------------------------------------------------------------------

    static final class Page {
        static final String JDK_API = "https://docs.oracle.com/en/java/javase/25/docs/api/";
        static final Pattern MID_LINE_TAG = Pattern.compile("(?:^|[\\s>(])@(param|return|since|throws|see|deprecated)\\b");

        final DocletEnvironment environment;
        final DocTrees trees;
        final Elements elements;
        final Types types;
        final Map<String, Plugins.Export> exports = new LinkedHashMap<>();
        /** Every documented package, by name. */
        final Map<String, PackageElement> packages = new TreeMap<>();
        /** Every documented type, in page order. */
        final List<TypeElement> typeOrder = new ArrayList<>();
        final Set<TypeElement> documentedTypes = new LinkedHashSet<>();
        /** Every anchor the page carries. */
        final Set<String> anchors = new LinkedHashSet<>();
        /** Search entries: display name, detail, kind, anchor. */
        final List<String[]> search = new ArrayList<>();
        String version = "";

        Page(DocletEnvironment environment) throws IOException {
            this.environment = environment;
            this.trees = environment.getDocTrees();
            this.elements = environment.getElementUtils();
            this.types = environment.getTypeUtils();
            for (Plugins.Export export : Plugins.exports(root)) exports.put(export.packageName(), export);
        }

        String render() throws IOException {
            collect();
            StringBuilder api = new StringBuilder();
            renderApi(api);
            String guide = renderGuide();
            StringBuilder page = new StringBuilder(1 << 20);
            page.append("<!DOCTYPE html>\n<!-- Generated by tools/sdk-reference/SdkReference.java from the SDK sources and tools/sdk-reference/guide.")
                .append(" Do not edit: run ./gradlew sdkReference. -->\n");
            page.append("<html lang=\"en\">\n<head>\n<meta charset=\"utf-8\">\n<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n");
            page.append("<title>Jasper Plugin SDK ").append(esc(version)).append(" Reference</title>\n");
            page.append("<meta name=\"description\" content=\"The Jasper plugin SDK ").append(esc(version))
                .append(": guide and complete API reference for building Jasper plugins.\">\n");
            page.append("<style>\n").append(resource("page.css")).append("</style>\n</head>\n<body>\n");
            page.append("<a class=\"skip\" href=\"#main\">Skip to content</a>\n");
            renderHeader(page);
            page.append("<div class=\"layout\">\n");
            renderNavigation(page);
            page.append("<main id=\"main\" tabindex=\"-1\">\n");
            page.append(guide);
            page.append(api);
            page.append("<footer class=\"page-footer\"><p>Jasper Plugin SDK ").append(esc(version))
                .append(". Generated from the SDK sources by <code>tools/sdk-reference</code>; regenerate with <code>./gradlew sdkReference</code>.</p></footer>\n");
            page.append("</main>\n</div>\n");
            page.append("<script id=\"search-index\" type=\"application/json\">").append(searchJson()).append("</script>\n");
            page.append("<script>\n").append(resource("page.js")).append("</script>\n</body>\n</html>\n");
            String text = page.toString().replace("<table", "<div class=\"table-wrap\"><table").replace("</table>", "</table></div>");
            checkAnchors(text);
            return text;
        }

        // -- model -------------------------------------------------------------------------------------------------

        void collect() {
            List<TypeElement> topLevel = new ArrayList<>();
            for (TypeElement type : ElementFilter.typesIn(environment.getIncludedElements())) {
                if (type.getEnclosingElement().getKind() == ElementKind.PACKAGE && included(type)) topLevel.add(type);
            }
            topLevel.sort(Comparator.comparing(type -> type.getQualifiedName().toString()));
            for (TypeElement type : topLevel) {
                PackageElement owner = elements.getPackageOf(type);
                packages.put(owner.getQualifiedName().toString(), owner);
            }
            for (String name : orderedPackages()) {
                for (TypeElement type : topLevel) {
                    if (elements.getPackageOf(type).getQualifiedName().contentEquals(name)) addType(type);
                }
            }
            for (TypeElement type : documentedTypes) {
                if (type.getQualifiedName().contentEquals("dev.jasper.sdk.JasperSdk")) {
                    for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                        if (field.getSimpleName().contentEquals("VERSION") && field.getConstantValue() instanceof String value) version = value;
                    }
                }
            }
            if (version.isEmpty()) fail("dev.jasper.sdk.JasperSdk.VERSION not found");
        }

        void addType(TypeElement type) {
            typeOrder.add(type);
            documentedTypes.add(type);
            for (TypeElement nested : ElementFilter.typesIn(type.getEnclosedElements())) {
                if (included(nested)) addType(nested);
            }
        }

        boolean included(Element element) {
            Set<Modifier> modifiers = element.getModifiers();
            boolean visible = modifiers.contains(Modifier.PUBLIC) || modifiers.contains(Modifier.PROTECTED);
            return visible && environment.isIncluded(element);
        }

        List<String> orderedPackages() {
            List<String> sdk = new ArrayList<>(), testkit = new ArrayList<>(), plugins = new ArrayList<>();
            for (String name : packages.keySet()) {
                if (name.startsWith("dev.jasper.sdk.testing")) testkit.add(name);
                else if (name.equals("dev.jasper.sdk") || name.startsWith("dev.jasper.sdk.")) sdk.add(name);
                else plugins.add(name);
            }
            // The root package first, then the plugin entry point, then the rest alphabetically.
            sdk.sort(Comparator.comparing((String name) -> name.equals("dev.jasper.sdk") ? 0 : name.equals("dev.jasper.sdk.plugin") ? 1 : 2)
                .thenComparing(Comparator.naturalOrder()));
            List<String> all = new ArrayList<>(sdk);
            all.addAll(testkit);
            all.addAll(plugins);
            return all;
        }

        static String group(String packageName) {
            if (packageName.startsWith("dev.jasper.sdk.testing")) return "testkit";
            if (packageName.equals("dev.jasper.sdk") || packageName.startsWith("dev.jasper.sdk.")) return "sdk";
            return "plugins";
        }

        // -- members -----------------------------------------------------------------------------------------------

        List<Element> members(TypeElement type, ElementKind kind) {
            List<Element> members = new ArrayList<>();
            for (Element member : type.getEnclosedElements()) {
                if (member.getKind() != kind || !included(member)) continue;
                // Implicit members (an enum's values() and valueOf, a default constructor) have no source.
                if (elements.getOrigin(member) != Elements.Origin.EXPLICIT || trees.getPath(member) == null) continue;
                if (member instanceof ExecutableElement executable) {
                    if (kind == ElementKind.METHOD && accessor(type, executable) != null) continue;
                    if (kind == ElementKind.CONSTRUCTOR && type.getKind() == ElementKind.RECORD && canonical(type, executable)
                        && trees.getDocCommentTree(executable) == null) continue;
                    if (kind == ElementKind.METHOD && trees.getDocCommentTree(executable) == null && overridesDocumented(type, executable)) continue;
                    if (kind == ElementKind.METHOD && trees.getDocCommentTree(executable) == null && objectMethod(executable)) continue;
                }
                members.add(member);
            }
            return members;
        }

        /** The record component an explicitly declared accessor reads, or {@code null}; the component's row documents it. */
        RecordComponentElement accessor(TypeElement type, ExecutableElement method) {
            if (type.getKind() != ElementKind.RECORD || !method.getParameters().isEmpty()) return null;
            for (RecordComponentElement component : type.getRecordComponents()) {
                if (component.getSimpleName().equals(method.getSimpleName())) return component;
            }
            return null;
        }

        boolean canonical(TypeElement record, ExecutableElement constructor) {
            List<? extends RecordComponentElement> components = record.getRecordComponents();
            if (components.size() != constructor.getParameters().size()) return false;
            for (int i = 0; i < components.size(); i++) {
                if (!types.isSameType(types.erasure(components.get(i).asType()), types.erasure(constructor.getParameters().get(i).asType()))) return false;
            }
            return true;
        }

        boolean objectMethod(ExecutableElement method) {
            String name = method.getSimpleName().toString();
            int parameters = method.getParameters().size();
            return (name.equals("toString") || name.equals("hashCode")) && parameters == 0 || name.equals("equals") && parameters == 1;
        }

        /** An undocumented override of a documented supertype method: the supertype's entry covers it. */
        boolean overridesDocumented(TypeElement type, ExecutableElement method) {
            for (TypeElement supertype : supertypes(type)) {
                if (!documentedTypes.contains(supertype)) continue;
                for (ExecutableElement candidate : ElementFilter.methodsIn(supertype.getEnclosedElements())) {
                    if (elements.overrides(method, candidate, type)) return true;
                }
            }
            return false;
        }

        List<TypeElement> supertypes(TypeElement type) {
            List<TypeElement> all = new ArrayList<>();
            List<TypeMirror> pending = new ArrayList<>(types.directSupertypes(type.asType()));
            while (!pending.isEmpty()) {
                TypeMirror next = pending.removeFirst();
                if (next instanceof DeclaredType declared && declared.asElement() instanceof TypeElement element && !all.contains(element)) {
                    all.add(element);
                    pending.addAll(types.directSupertypes(next));
                }
            }
            return all;
        }

        /** Documented SDK interfaces a testkit fake implements, named once instead of repeating their methods. */
        List<TypeElement> implementedDocumented(TypeElement type) {
            if (type.getKind().isInterface()) return List.of();
            return supertypes(type).stream().filter(documentedTypes::contains).filter(t -> t.getKind().isInterface()).toList();
        }

        // -- anchors -----------------------------------------------------------------------------------------------

        String id(Element element) {
            return switch (element.getKind()) {
                case PACKAGE -> "package-" + ((PackageElement) element).getQualifiedName();
                case CLASS, INTERFACE, ENUM, RECORD, ANNOTATION_TYPE -> ((TypeElement) element).getQualifiedName().toString();
                case METHOD, CONSTRUCTOR -> id(element.getEnclosingElement()) + "." + memberKey((ExecutableElement) element);
                case RECORD_COMPONENT -> id(element.getEnclosingElement()) + "." + element.getSimpleName() + "()";
                default -> id(element.getEnclosingElement()) + "." + element.getSimpleName();
            };
        }

        String memberKey(ExecutableElement method) {
            String name = method.getKind() == ElementKind.CONSTRUCTOR ? method.getEnclosingElement().getSimpleName().toString() : method.getSimpleName().toString();
            return name + "(" + method.getParameters().stream().map(p -> erasedName(p.asType(), false)).collect(Collectors.joining(",")) + ")";
        }

        String erasedName(TypeMirror type, boolean qualified) {
            TypeMirror erased = types.erasure(type);
            return switch (erased.getKind()) {
                case DECLARED -> qualified ? ((TypeElement) ((DeclaredType) erased).asElement()).getQualifiedName().toString()
                    : displayName((TypeElement) ((DeclaredType) erased).asElement());
                case ARRAY -> erasedName(((ArrayType) erased).getComponentType(), qualified) + "[]";
                default -> erased.toString();
            };
        }

        String displayName(TypeElement type) {
            StringBuilder name = new StringBuilder(type.getSimpleName());
            Element enclosing = type.getEnclosingElement();
            while (enclosing instanceof TypeElement outer) {
                name.insert(0, outer.getSimpleName() + ".");
                enclosing = outer.getEnclosingElement();
            }
            return name.toString();
        }

        /** The page anchor of a documented element, or {@code null}. Record accessors land on their component. */
        String anchorOf(Element element) {
            if (element == null) return null;
            if (element instanceof TypeElement type) return documentedTypes.contains(type) ? id(type) : null;
            if (element instanceof PackageElement pkg) return packages.containsKey(pkg.getQualifiedName().toString()) ? id(pkg) : null;
            Element owner = element.getEnclosingElement();
            if (!(owner instanceof TypeElement type) || !documentedTypes.contains(type)) return null;
            if (element instanceof VariableElement field && field.getKind() == ElementKind.FIELD && type.getKind() == ElementKind.RECORD) {
                for (RecordComponentElement component : type.getRecordComponents()) {
                    if (component.getSimpleName().equals(field.getSimpleName())) return id(component);
                }
            }
            if (element instanceof ExecutableElement method && type.getKind() == ElementKind.RECORD && method.getParameters().isEmpty()) {
                for (RecordComponentElement component : type.getRecordComponents()) {
                    if (component.getSimpleName().equals(method.getSimpleName())) return id(component);
                }
            }
            return id(element);
        }

        String jdkLink(Element element) {
            TypeElement type = element instanceof TypeElement t ? t : element.getEnclosingElement() instanceof TypeElement t ? t : null;
            if (type == null) return null;
            ModuleElement module = elements.getModuleOf(type);
            if (module == null || module.isUnnamed()) return null;
            String moduleName = module.getQualifiedName().toString();
            if (!moduleName.startsWith("java.") && !moduleName.startsWith("jdk.")) return null;
            String packageName = elements.getPackageOf(type).getQualifiedName().toString();
            String url = JDK_API + moduleName + "/" + packageName.replace('.', '/') + "/" + displayName(type) + ".html";
            if (element instanceof ExecutableElement method) {
                url += "#" + (method.getKind() == ElementKind.CONSTRUCTOR ? "%3Cinit%3E" : method.getSimpleName()) + "("
                    + method.getParameters().stream().map(p -> erasedName(p.asType(), true)).collect(Collectors.joining(",")) + ")";
            } else if (element instanceof VariableElement) {
                url += "#" + element.getSimpleName();
            }
            return url;
        }

        // -- types in signatures ------------------------------------------------------------------------------------

        String typeHtml(TypeMirror type) {
            return switch (type.getKind()) {
                case DECLARED -> {
                    DeclaredType declared = (DeclaredType) type;
                    TypeElement element = (TypeElement) declared.asElement();
                    String name = typeLink(element);
                    if (!declared.getTypeArguments().isEmpty()) {
                        name += "&lt;" + declared.getTypeArguments().stream().map(this::typeHtml).collect(Collectors.joining(", ")) + "&gt;";
                    }
                    yield name;
                }
                case ARRAY -> typeHtml(((ArrayType) type).getComponentType()) + "[]";
                case TYPEVAR -> "<span class=\"tv\">" + esc(((TypeVariable) type).asElement().getSimpleName().toString()) + "</span>";
                case WILDCARD -> {
                    WildcardType wildcard = (WildcardType) type;
                    if (wildcard.getExtendsBound() != null) yield "? extends " + typeHtml(wildcard.getExtendsBound());
                    if (wildcard.getSuperBound() != null) yield "? super " + typeHtml(wildcard.getSuperBound());
                    yield "?";
                }
                case INTERSECTION -> ((IntersectionType) type).getBounds().stream().map(this::typeHtml).collect(Collectors.joining(" &amp; "));
                case UNION -> ((UnionType) type).getAlternatives().stream().map(this::typeHtml).collect(Collectors.joining(" | "));
                default -> "<span class=\"kw\">" + esc(type.toString()) + "</span>";
            };
        }

        String typeLink(TypeElement element) {
            String name = esc(displayName(element));
            String anchor = anchorOf(element);
            if (anchor != null) return "<a class=\"tl\" href=\"#" + attr(anchor) + "\">" + name + "</a>";
            String jdk = jdkLink(element);
            if (jdk != null) return "<a class=\"tl ext\" href=\"" + attr(jdk) + "\" title=\"" + attr(element.getQualifiedName().toString()) + "\">" + name + "</a>";
            return "<span class=\"tl\" title=\"" + attr(element.getQualifiedName().toString()) + "\">" + name + "</span>";
        }

        String typeParameters(List<? extends TypeParameterElement> parameters) {
            if (parameters.isEmpty()) return "";
            return "&lt;" + parameters.stream().map(parameter -> {
                String text = "<span class=\"tv\">" + esc(parameter.getSimpleName().toString()) + "</span>";
                List<String> bounds = parameter.getBounds().stream()
                    .filter(bound -> !(bound instanceof DeclaredType d && ((TypeElement) d.asElement()).getQualifiedName().contentEquals("java.lang.Object")))
                    .map(this::typeHtml).toList();
                return bounds.isEmpty() ? text : text + " extends " + String.join(" &amp; ", bounds);
            }).collect(Collectors.joining(", ")) + "&gt;";
        }

        String annotations(Element element) {
            StringBuilder out = new StringBuilder();
            for (AnnotationMirror mirror : element.getAnnotationMirrors()) {
                TypeElement annotation = (TypeElement) mirror.getAnnotationType().asElement();
                boolean documented = annotation.getAnnotationMirrors().stream()
                    .anyMatch(meta -> ((TypeElement) meta.getAnnotationType().asElement()).getQualifiedName().contentEquals("java.lang.annotation.Documented"));
                if (documented) out.append("<span class=\"an\">@").append(typeLink(annotation)).append("</span>\n");
            }
            return out.toString();
        }

        String modifiers(Element element, Set<Modifier> drop) {
            List<String> words = new ArrayList<>();
            for (Modifier modifier : element.getModifiers()) {
                if (modifier == Modifier.PUBLIC || drop.contains(modifier)) continue;
                words.add(modifier.toString());
            }
            return words.isEmpty() ? "" : "<span class=\"kw\">" + String.join(" ", words) + "</span> ";
        }

        String kindWord(TypeElement type) {
            return switch (type.getKind()) {
                case INTERFACE -> "interface";
                case ENUM -> "enum";
                case RECORD -> "record";
                case ANNOTATION_TYPE -> "@interface";
                default -> "class";
            };
        }

        String kindLabel(TypeElement type) {
            String word = kindWord(type);
            if (type.getKind() == ElementKind.INTERFACE && type.getModifiers().contains(Modifier.SEALED)) return "sealed interface";
            if (type.getKind() == ElementKind.CLASS && type.getModifiers().contains(Modifier.ABSTRACT)) return "abstract class";
            if (type.getKind() == ElementKind.INTERFACE && functional(type)) return "functional interface";
            return word;
        }

        boolean functional(TypeElement type) {
            return type.getAnnotationMirrors().stream().anyMatch(m -> ((TypeElement) m.getAnnotationType().asElement()).getQualifiedName().contentEquals("java.lang.FunctionalInterface"));
        }

        String typeSignature(TypeElement type) {
            StringBuilder out = new StringBuilder(annotations(type));
            Set<Modifier> drop = switch (type.getKind()) {
                case INTERFACE, ANNOTATION_TYPE -> Set.of(Modifier.ABSTRACT, Modifier.STATIC);
                case ENUM, RECORD -> Set.of(Modifier.FINAL, Modifier.STATIC);
                default -> Set.of();
            };
            out.append(modifiers(type, drop)).append("<span class=\"kw\">").append(kindWord(type)).append("</span> <b>")
                .append(esc(type.getSimpleName().toString())).append("</b>").append(typeParameters(type.getTypeParameters()));
            if (type.getKind() == ElementKind.RECORD) {
                out.append("(").append(type.getRecordComponents().stream()
                    .map(component -> typeHtml(component.asType()) + " " + esc(component.getSimpleName().toString()))
                    .collect(Collectors.joining(", "))).append(")");
            }
            TypeMirror superclass = type.getSuperclass();
            if (type.getKind() == ElementKind.CLASS && superclass instanceof DeclaredType declared
                && !((TypeElement) declared.asElement()).getQualifiedName().contentEquals("java.lang.Object")) {
                out.append("\n    <span class=\"kw\">extends</span> ").append(typeHtml(superclass));
            }
            List<? extends TypeMirror> interfaces = type.getInterfaces();
            if (!interfaces.isEmpty() && type.getKind() != ElementKind.ANNOTATION_TYPE) {
                out.append("\n    <span class=\"kw\">").append(type.getKind().isInterface() ? "extends" : "implements").append("</span> ")
                    .append(interfaces.stream().map(this::typeHtml).collect(Collectors.joining(", ")));
            }
            List<? extends TypeMirror> permitted = type.getPermittedSubclasses();
            if (!permitted.isEmpty()) {
                out.append("\n    <span class=\"kw\">permits</span> ").append(permitted.stream().map(this::typeHtml).collect(Collectors.joining(", ")));
            }
            return out.toString();
        }

        String methodSignature(ExecutableElement method) {
            StringBuilder out = new StringBuilder(annotations(method));
            boolean inInterface = method.getEnclosingElement().getKind().isInterface();
            Set<Modifier> drop = inInterface ? Set.of(Modifier.ABSTRACT) : Set.of();
            out.append(modifiers(method, drop));
            String parameters = typeParameters(method.getTypeParameters());
            if (!parameters.isEmpty()) out.append(parameters).append(" ");
            if (method.getKind() == ElementKind.METHOD) out.append(typeHtml(method.getReturnType())).append(" ");
            String name = method.getKind() == ElementKind.CONSTRUCTOR ? method.getEnclosingElement().getSimpleName().toString() : method.getSimpleName().toString();
            out.append("<b>").append(esc(name)).append("</b>(");
            List<? extends VariableElement> list = method.getParameters();
            List<String> rendered = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                VariableElement parameter = list.get(i);
                String type = method.isVarArgs() && i == list.size() - 1 && parameter.asType() instanceof ArrayType array
                    ? typeHtml(array.getComponentType()) + "..." : typeHtml(parameter.asType());
                rendered.add(type + " " + esc(parameter.getSimpleName().toString()));
            }
            String joined = String.join(", ", rendered);
            out.append(joined.length() > 90 && rendered.size() > 1 ? "\n        " + String.join(",\n        ", rendered) : joined).append(")");
            if (!method.getThrownTypes().isEmpty()) {
                out.append("\n    <span class=\"kw\">throws</span> ").append(method.getThrownTypes().stream().map(this::typeHtml).collect(Collectors.joining(", ")));
            }
            return out.toString();
        }

        String fieldSignature(VariableElement field) {
            StringBuilder out = new StringBuilder(annotations(field));
            out.append(modifiers(field, Set.of())).append(typeHtml(field.asType())).append(" <b>").append(esc(field.getSimpleName().toString())).append("</b>");
            Object value = field.getConstantValue();
            if (value != null) out.append(" = <span class=\"lit\">").append(esc(literal(value))).append("</span>");
            return out.toString();
        }

        static String literal(Object value) {
            if (value instanceof String text) {
                StringBuilder out = new StringBuilder("\"");
                for (char c : text.toCharArray()) {
                    switch (c) {
                        case '"' -> out.append("\\\"");
                        case '\\' -> out.append("\\\\");
                        case '\n' -> out.append("\\n");
                        case '\t' -> out.append("\\t");
                        default -> out.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
                    }
                }
                return out.append('"').toString();
            }
            if (value instanceof Character c) return "'" + c + "'";
            if (value instanceof Long l) return l + "L";
            if (value instanceof Float f) return f + "f";
            return String.valueOf(value);
        }

        // -- doc comments ------------------------------------------------------------------------------------------

        /** A rendered doc comment: description HTML plus its block tags. */
        record Comment(String description, String firstSentence, Map<String, String> parameters, Map<String, String> typeParameters,
                       String returns, List<String[]> thrown, String since, List<String> see, String deprecated, List<String[]> notes) {
            static final Comment EMPTY = new Comment("", "", Map.of(), Map.of(), "", List.of(), "", List.of(), "", List.of());
            boolean isEmpty() { return description.isBlank(); }
        }

        final Map<Element, Comment> comments = new java.util.HashMap<>();

        Comment comment(Element element) {
            return comments.computeIfAbsent(element, this::readComment);
        }

        Comment readComment(Element element) {
            DocCommentTree tree = trees.getDocCommentTree(element);
            if (tree == null) return Comment.EMPTY;
            TreePath path = trees.getPath(element);
            Context context = new Context(element, path, tree);
            String description = html(tree.getFullBody(), context).strip();
            String firstSentence = html(tree.getFirstSentence(), context).strip();
            Map<String, String> parameters = new LinkedHashMap<>(), typeParameters = new LinkedHashMap<>();
            String returns = "", since = "", deprecated = "";
            List<String[]> thrown = new ArrayList<>(), notes = new ArrayList<>();
            List<String> see = new ArrayList<>();
            for (DocTree tag : tree.getBlockTags()) {
                switch (tag.getKind()) {
                    case PARAM -> {
                        ParamTree param = (ParamTree) tag;
                        (param.isTypeParameter() ? typeParameters : parameters).put(param.getName().getName().toString(), html(param.getDescription(), context).strip());
                    }
                    case RETURN -> returns = html(((ReturnTree) tag).getDescription(), context).strip();
                    case THROWS, EXCEPTION -> {
                        ThrowsTree throwsTree = (ThrowsTree) tag;
                        thrown.add(new String[]{reference(throwsTree.getExceptionName(), List.of(), context, false), html(throwsTree.getDescription(), context).strip()});
                    }
                    case SINCE -> since = html(((SinceTree) tag).getBody(), context).strip();
                    case SEE -> see.add(html(((SeeTree) tag).getReference(), context).strip());
                    case DEPRECATED -> deprecated = html(((DeprecatedTree) tag).getBody(), context).strip();
                    case UNKNOWN_BLOCK_TAG -> {
                        UnknownBlockTagTree unknown = (UnknownBlockTagTree) tag;
                        notes.add(new String[]{unknown.getTagName(), html(unknown.getContent(), context).strip()});
                    }
                    default -> { }
                }
            }
            for (String text : List.of(description, returns, since)) {
                Matcher matcher = MID_LINE_TAG.matcher(text.replaceAll("<code>.*?</code>", ""));
                if (matcher.find()) fail(where(element) + ": @" + matcher.group(1) + " is written mid-line, so Javadoc reads it as text; start it on its own line");
            }
            for (String text : parameters.values()) {
                Matcher matcher = MID_LINE_TAG.matcher(text.replaceAll("<code>.*?</code>", ""));
                if (matcher.find()) fail(where(element) + ": @" + matcher.group(1) + " is written mid-line inside a @param; start it on its own line");
            }
            return new Comment(description, firstSentence, parameters, typeParameters, returns, thrown, since, see, deprecated, notes);
        }

        record Context(Element element, TreePath path, DocCommentTree tree) { }

        String where(Element element) {
            if (element instanceof TypeElement type) return type.getQualifiedName().toString();
            if (element instanceof PackageElement pkg) return "package " + pkg.getQualifiedName();
            Element owner = element.getEnclosingElement();
            String name = element instanceof ExecutableElement method ? memberKey(method) : element.getSimpleName().toString();
            return (owner instanceof TypeElement type ? type.getQualifiedName() + "#" : "") + name;
        }

        String html(List<? extends DocTree> list, Context context) {
            StringBuilder out = new StringBuilder();
            for (DocTree tree : list) out.append(html(tree, context));
            return out.toString();
        }

        String html(DocTree tree, Context context) {
            return switch (tree.getKind()) {
                case TEXT -> ((TextTree) tree).getBody();
                case ENTITY -> "&" + ((EntityTree) tree).getName() + ";";
                case START_ELEMENT -> {
                    StartElementTree start = (StartElementTree) tree;
                    StringBuilder out = new StringBuilder("<").append(start.getName());
                    for (DocTree attribute : start.getAttributes()) {
                        if (attribute instanceof AttributeTree a) {
                            out.append(' ').append(a.getName());
                            if (a.getValueKind() != AttributeTree.ValueKind.EMPTY) out.append("=\"").append(html(a.getValue(), context)).append('"');
                        }
                    }
                    yield out.append(start.isSelfClosing() ? "/>" : ">").toString();
                }
                case END_ELEMENT -> "</" + ((EndElementTree) tree).getName() + ">";
                case CODE -> "<code>" + esc(((LiteralTree) tree).getBody().getBody()) + "</code>";
                case LITERAL -> esc(((LiteralTree) tree).getBody().getBody());
                case LINK, LINK_PLAIN -> {
                    LinkTree link = (LinkTree) tree;
                    yield reference(link.getReference(), link.getLabel(), context, tree.getKind() == DocTree.Kind.LINK);
                }
                case REFERENCE -> reference((ReferenceTree) tree, List.of(), context, true);
                case VALUE -> {
                    ValueTree value = (ValueTree) tree;
                    Element target = value.getReference() == null ? context.element() : resolve(value.getReference(), context);
                    if (target instanceof VariableElement field && field.getConstantValue() != null) yield "<code>" + esc(literal(field.getConstantValue())) + "</code>";
                    fail(where(context.element()) + ": {@value} does not name a constant");
                    yield "";
                }
                case RETURN -> "Returns " + html(((ReturnTree) tree).getDescription(), context) + ".";
                case UNKNOWN_INLINE_TAG -> esc(html(((UnknownInlineTagTree) tree).getContent(), context));
                case ERRONEOUS -> {
                    fail(where(context.element()) + ": malformed Javadoc: " + ((ErroneousTree) tree).getDiagnostic().getMessage(Locale.ROOT));
                    yield esc(((ErroneousTree) tree).getBody());
                }
                default -> esc(tree.toString());
            };
        }

        Element resolve(ReferenceTree reference, Context context) {
            DocTreePath path = DocTreePath.getPath(context.path(), context.tree(), reference);
            return path == null ? null : trees.getElement(path);
        }

        String reference(ReferenceTree reference, List<? extends DocTree> label, Context context, boolean code) {
            Element target = resolve(reference, context);
            String signature = reference.getSignature();
            String text = label.isEmpty() ? esc(defaultLabel(signature, context)) : html(label, context).strip();
            if (code && label.isEmpty()) text = "<code>" + text + "</code>";
            if (target == null) {
                fail(where(context.element()) + ": {@link " + signature + "} does not resolve");
                return text;
            }
            String anchor = anchorOf(target);
            if (anchor != null) return "<a href=\"#" + attr(anchor) + "\">" + text + "</a>";
            String jdk = jdkLink(target);
            if (jdk != null) return "<a class=\"ext\" href=\"" + attr(jdk) + "\">" + text + "</a>";
            return text;
        }

        String defaultLabel(String signature, Context context) {
            String label = signature;
            String owner = "";
            int hash = label.indexOf('#');
            if (hash >= 0) {
                owner = label.substring(0, hash);
                label = label.substring(hash + 1);
            }
            if (!owner.isEmpty()) {
                String simple = owner.contains(".") && Character.isLowerCase(owner.charAt(0)) ? owner.substring(owner.lastIndexOf('.') + 1) : owner;
                label = simple + "." + label;
            } else if (hash < 0 && Character.isLowerCase(label.charAt(0)) && label.contains(".")) {
                // A qualified type name: show the simple names after the package.
                String[] parts = label.split("\\.");
                int first = 0;
                while (first < parts.length - 1 && Character.isLowerCase(parts[first].charAt(0))) first++;
                label = String.join(".", java.util.Arrays.copyOfRange(parts, first, parts.length));
            }
            // Drop parameter lists' packages, (java.lang.String) reads as (String), and space the parameters so they wrap.
            return label.replaceAll("(?<=[(, ])(?:[a-z][a-z0-9_]*\\.)+(?=[A-Z])", "").replaceAll(",(?! )", ", ");
        }

        // -- API section -------------------------------------------------------------------------------------------

        void renderApi(StringBuilder out) {
            out.append("<section class=\"api\" id=\"api\">\n<h2 class=\"part\">API reference</h2>\n");
            out.append("<p class=\"lead\">Every public type and member, read from the Javadoc of SDK ").append(esc(version))
                .append(". Plugins compile against <code>dev.jasper.sdk</code>; tests use <code>dev.jasper.sdk.testing</code> from <code>jasper-sdk-testkit</code>;")
                .append(" the bundled plugins' exported API packages are reached through <a href=\"#").append(attr(firstPackageId("dev.jasper.sdk.services"))).append("\"><code>Services</code></a>.</p>\n");
            String currentGroup = "";
            for (String packageName : orderedPackages()) {
                String group = group(packageName);
                if (!group.equals(currentGroup)) {
                    currentGroup = group;
                    out.append("<h2 class=\"group\" id=\"group-").append(group).append("\">").append(groupTitle(group)).append("</h2>\n");
                    anchors.add("group-" + group);
                    out.append("<p class=\"group-lead\">").append(groupLead(group)).append("</p>\n");
                }
                renderPackage(out, packages.get(packageName));
            }
            out.append("</section>\n");
        }

        String firstPackageId(String name) { return "package-" + name; }

        static String groupTitle(String group) {
            return switch (group) {
                case "sdk" -> "Plugin SDK <span class=\"muted\">jasper-sdk</span>";
                case "testkit" -> "Test kit <span class=\"muted\">jasper-sdk-testkit</span>";
                default -> "Bundled plugin APIs";
            };
        }

        static String groupLead(String group) {
            return switch (group) {
                case "sdk" -> "The API every plugin compiles against. It depends only on the JDK. Plugins implement <code>Plugin</code> and functional callbacks; Jasper implements everything else.";
                case "testkit" -> "A headless fake of the plugin runtime for plugin unit tests: start a plugin against <code>FakePluginHost</code>, drive it, and assert what it registered.";
                default -> "Packages that bundled plugins export to other plugins. Declare the exporting plugin under <code>requires</code> in <code>plugin.toml</code> (usually with <code>optional = true</code>), compile against its jar, and look its services up with <code>Services.find</code> or <code>Services.require</code>.";
            };
        }

        void renderPackage(StringBuilder out, PackageElement pkg) {
            String name = pkg.getQualifiedName().toString();
            String id = id(pkg);
            anchors.add(id);
            search.add(new String[]{name, "package", "package", id});
            out.append("<section class=\"package\" id=\"").append(attr(id)).append("\">\n");
            out.append("<h3 class=\"package-title\"><span class=\"kind kind-package\">package</span> <code>").append(esc(name)).append("</code></h3>\n");
            Plugins.Export export = exports.get(name);
            if (export != null) {
                out.append("<p class=\"export\">Exported by <b>").append(esc(export.pluginName())).append("</b> <code>").append(esc(export.pluginId()))
                    .append("</code> ").append(esc(export.version())).append(". Declare it in <code>plugin.toml</code>: <code>requires = [{ id = \"")
                    .append(esc(export.pluginId())).append("\", version = \"&gt;=").append(esc(export.version())).append("\", optional = true }]</code>.</p>\n");
            }
            Comment comment = comment(pkg);
            if (comment.isEmpty()) undocumented.add("package " + name);
            out.append("<div class=\"doc\">").append(paragraphs(comment.description())).append("</div>\n");
            List<TypeElement> inPackage = typeOrder.stream().filter(type -> elements.getPackageOf(type).equals(pkg)).toList();
            out.append("<table class=\"summary\"><thead><tr><th>Type</th><th>Kind</th><th>Summary</th></tr></thead><tbody>\n");
            for (TypeElement type : inPackage) {
                if (type.getEnclosingElement().getKind() != ElementKind.PACKAGE) continue;
                out.append("<tr><td><a href=\"#").append(attr(id(type))).append("\"><code>").append(esc(displayName(type))).append("</code></a></td><td><span class=\"kind kind-")
                    .append(kindClass(type)).append("\">").append(kindLabel(type)).append("</span></td><td>").append(comment(type).firstSentence()).append("</td></tr>\n");
            }
            out.append("</tbody></table>\n");
            for (TypeElement type : inPackage) renderType(out, type);
            out.append("</section>\n");
        }

        String kindClass(TypeElement type) {
            return switch (type.getKind()) {
                case INTERFACE -> "interface";
                case ENUM -> "enum";
                case RECORD -> "record";
                case ANNOTATION_TYPE -> "annotation";
                default -> "class";
            };
        }

        void renderType(StringBuilder out, TypeElement type) {
            String id = id(type);
            anchors.add(id);
            String packageName = elements.getPackageOf(type).getQualifiedName().toString();
            search.add(new String[]{displayName(type), packageName, kindLabel(type), id});
            Comment comment = comment(type);
            if (comment.isEmpty()) undocumented.add(type.getQualifiedName().toString());
            boolean nested = type.getEnclosingElement() instanceof TypeElement;
            out.append("<article class=\"type").append(nested ? " nested" : "").append("\" id=\"").append(attr(id)).append("\">\n<header class=\"type-head\">");
            out.append("<span class=\"kind kind-").append(kindClass(type)).append("\">").append(kindLabel(type)).append("</span>");
            out.append("<h4><a class=\"self\" href=\"#").append(attr(id)).append("\">").append(esc(displayName(type))).append("</a></h4>");
            if (!comment.since().isEmpty()) out.append("<span class=\"since\">since ").append(comment.since()).append("</span>");
            if (elements.isDeprecated(type)) out.append("<span class=\"deprecated\">deprecated</span>");
            out.append("<span class=\"pkg\">").append(esc(packageName)).append("</span></header>\n");
            out.append("<pre class=\"sig\"><code>").append(typeSignature(type)).append("</code></pre>\n");
            out.append("<div class=\"doc\">").append(comment.isEmpty() ? "" : paragraphs(comment.description())).append("</div>\n");
            if (!comment.deprecated().isEmpty()) out.append("<p class=\"deprecation\"><b>Deprecated.</b> ").append(comment.deprecated()).append("</p>\n");
            renderNotes(out, comment);
            if (!comment.typeParameters().isEmpty()) {
                out.append("<dl class=\"tags\"><dt>Type parameters</dt>");
                comment.typeParameters().forEach((name, text) -> out.append("<dd><code>&lt;").append(esc(name)).append("&gt;</code> ").append(text).append("</dd>"));
                out.append("</dl>\n");
            }
            if (type.getKind() == ElementKind.RECORD && !type.getRecordComponents().isEmpty()) {
                out.append("<h5>Components</h5>\n<table class=\"components\"><tbody>\n");
                for (RecordComponentElement component : type.getRecordComponents()) {
                    String componentId = id(component);
                    anchors.add(componentId);
                    search.add(new String[]{displayName(type) + "." + component.getSimpleName() + "()", packageName, "component", componentId});
                    String text = comment.parameters().getOrDefault(component.getSimpleName().toString(), "");
                    if (text.isEmpty() && component.getAccessor() != null && elements.getOrigin(component.getAccessor()) == Elements.Origin.EXPLICIT) {
                        Comment accessor = comment(component.getAccessor());
                        text = !accessor.returns().isEmpty() ? accessor.returns() : accessor.description();
                    }
                    if (text.isEmpty()) undocumented.add(type.getQualifiedName() + "." + component.getSimpleName() + " (record component)");
                    out.append("<tr id=\"").append(attr(componentId)).append("\"><td class=\"ct\"><code>").append(typeHtml(component.asType())).append("</code></td><td class=\"cn\"><code><b>")
                        .append(esc(component.getSimpleName().toString())).append("</b></code></td><td>").append(text).append("</td></tr>\n");
                }
                out.append("</tbody></table>\n");
            }
            List<TypeElement> implemented = implementedDocumented(type);
            if (!implemented.isEmpty() && group(packageName).equals("testkit")) {
                out.append("<p class=\"implements\">Implements ").append(implemented.stream().map(t -> "<a href=\"#" + attr(id(t)) + "\"><code>" + esc(displayName(t)) + "</code></a>")
                    .collect(Collectors.joining(", "))).append("; the methods it implements from them are documented there.</p>\n");
            }
            List<Element> constants = members(type, ElementKind.ENUM_CONSTANT);
            if (!constants.isEmpty()) {
                out.append("<h5>Constants</h5>\n<dl class=\"constants\">\n");
                for (Element constant : constants) {
                    String constantId = id(constant);
                    anchors.add(constantId);
                    search.add(new String[]{displayName(type) + "." + constant.getSimpleName(), packageName, "constant", constantId});
                    Comment c = comment(constant);
                    out.append("<dt id=\"").append(attr(constantId)).append("\"><code>").append(esc(constant.getSimpleName().toString())).append("</code>");
                    if (!c.since().isEmpty()) out.append(" <span class=\"since\">since ").append(c.since()).append("</span>");
                    out.append("</dt><dd>").append(c.description()).append("</dd>\n");
                }
                out.append("</dl>\n");
            }
            renderMembers(out, type, members(type, ElementKind.FIELD), "Fields");
            renderMembers(out, type, members(type, ElementKind.CONSTRUCTOR), "Constructors");
            renderMembers(out, type, members(type, ElementKind.METHOD), "Methods");
            List<TypeElement> nestedTypes = ElementFilter.typesIn(type.getEnclosedElements()).stream().filter(this::included).toList();
            if (!nestedTypes.isEmpty()) {
                out.append("<h5>Nested types</h5>\n<ul class=\"nested-list\">");
                for (TypeElement nestedType : nestedTypes) {
                    out.append("<li><a href=\"#").append(attr(id(nestedType))).append("\"><code>").append(esc(displayName(nestedType))).append("</code></a> <span class=\"kind kind-")
                        .append(kindClass(nestedType)).append("\">").append(kindLabel(nestedType)).append("</span> ").append(comment(nestedType).firstSentence()).append("</li>");
                }
                out.append("</ul>\n");
            }
            out.append("</article>\n");
        }

        void renderNotes(StringBuilder out, Comment comment) {
            for (String[] note : comment.notes()) {
                String title = switch (note[0]) {
                    case "apiNote" -> "API note";
                    case "implNote" -> "Implementation note";
                    case "implSpec" -> "Implementation requirements";
                    default -> note[0];
                };
                out.append("<aside class=\"note\"><b>").append(esc(title)).append("</b> ").append(note[1]).append("</aside>\n");
            }
            if (!comment.see().isEmpty()) out.append("<p class=\"see\"><b>See also</b> ").append(String.join(", ", comment.see())).append("</p>\n");
        }

        void renderMembers(StringBuilder out, TypeElement type, List<Element> members, String title) {
            if (members.isEmpty()) return;
            String packageName = elements.getPackageOf(type).getQualifiedName().toString();
            out.append("<h5>").append(title).append("</h5>\n");
            for (Element member : members) {
                String memberId = id(member);
                anchors.add(memberId);
                Comment comment = comment(member);
                String display = member instanceof ExecutableElement method ? displayName(type) + "." + memberKey(method) : displayName(type) + "." + member.getSimpleName();
                search.add(new String[]{display, packageName, member.getKind() == ElementKind.FIELD ? "field" : member.getKind() == ElementKind.CONSTRUCTOR ? "constructor" : "method", memberId});
                if (comment.isEmpty()) undocumented.add(where(member));
                out.append("<div class=\"member\" id=\"").append(attr(memberId)).append("\">\n");
                out.append("<pre class=\"sig\"><a class=\"self\" href=\"#").append(attr(memberId)).append("\" aria-label=\"Link to this member\">#</a><code>")
                    .append(member instanceof ExecutableElement method ? methodSignature(method) : fieldSignature((VariableElement) member)).append("</code></pre>\n");
                if (!comment.since().isEmpty() || elements.isDeprecated(member)) {
                    out.append("<p class=\"badges\">");
                    if (!comment.since().isEmpty()) out.append("<span class=\"since\">since ").append(comment.since()).append("</span>");
                    if (elements.isDeprecated(member)) out.append("<span class=\"deprecated\">deprecated</span>");
                    out.append("</p>\n");
                }
                out.append("<div class=\"doc\">").append(paragraphs(comment.description())).append("</div>\n");
                if (!comment.deprecated().isEmpty()) out.append("<p class=\"deprecation\"><b>Deprecated.</b> ").append(comment.deprecated()).append("</p>\n");
                renderNotes(out, comment);
                StringBuilder tags = new StringBuilder();
                if (member instanceof ExecutableElement method) {
                    if (!comment.typeParameters().isEmpty()) {
                        tags.append("<dt>Type parameters</dt>");
                        comment.typeParameters().forEach((name, text) -> tags.append("<dd><code>&lt;").append(esc(name)).append("&gt;</code> ").append(text).append("</dd>"));
                    }
                    if (!method.getParameters().isEmpty()) {
                        tags.append("<dt>Parameters</dt>");
                        for (VariableElement parameter : method.getParameters()) {
                            String text = comment.parameters().getOrDefault(parameter.getSimpleName().toString(), "");
                            tags.append("<dd><code>").append(esc(parameter.getSimpleName().toString())).append("</code>").append(text.isEmpty() ? "" : " " + text).append("</dd>");
                        }
                    }
                    if (!comment.returns().isEmpty()) tags.append("<dt>Returns</dt><dd>").append(comment.returns()).append("</dd>");
                    if (!comment.thrown().isEmpty()) {
                        tags.append("<dt>Throws</dt>");
                        for (String[] thrown : comment.thrown()) tags.append("<dd>").append(thrown[0]).append(thrown[1].isEmpty() ? "" : " " + thrown[1]).append("</dd>");
                    }
                }
                if (!tags.isEmpty()) out.append("<dl class=\"tags\">").append(tags).append("</dl>\n");
                out.append("</div>\n");
            }
        }

        /** Javadoc separates paragraphs with {@code <p>}; wrap the leading text so every paragraph is a block. */
        static String paragraphs(String html) {
            if (html.isBlank()) return "";
            String trimmed = html.strip();
            if (trimmed.startsWith("<p") || trimmed.startsWith("<ul") || trimmed.startsWith("<ol") || trimmed.startsWith("<pre") || trimmed.startsWith("<table")) return trimmed;
            int split = indexOfBlock(trimmed);
            return split < 0 ? "<p>" + trimmed + "</p>" : "<p>" + trimmed.substring(0, split).strip() + "</p>" + trimmed.substring(split);
        }

        static int indexOfBlock(String html) {
            Matcher matcher = Pattern.compile("<(p|ul|ol|pre|table|dl|h[1-6]|blockquote)[\\s>/]").matcher(html);
            return matcher.find() ? matcher.start() : -1;
        }

        // -- header and navigation ---------------------------------------------------------------------------------

        void renderHeader(StringBuilder out) {
            out.append("<header class=\"top\">\n<button class=\"menu\" type=\"button\" aria-label=\"Show navigation\" aria-expanded=\"false\" aria-controls=\"side\">")
                .append("<span></span><span></span><span></span></button>\n");
            out.append("<a class=\"brand\" href=\"#top\"><span class=\"mark\" aria-hidden=\"true\">").append(MARK).append("</span><span class=\"brand-text\">Jasper <b>Plugin SDK</b></span></a>\n");
            out.append("<span class=\"version\" title=\"JasperSdk.VERSION\">").append(esc(version)).append("</span>\n");
            out.append("<div class=\"search\"><input id=\"search\" type=\"search\" placeholder=\"Search types and members\" autocomplete=\"off\" spellcheck=\"false\" aria-label=\"Search the reference\">")
                .append("<kbd>/</kbd><ul id=\"results\" role=\"listbox\" hidden></ul></div>\n");
            out.append("<button class=\"theme\" type=\"button\" aria-label=\"Switch between light and dark\" title=\"Switch between light and dark\">").append(THEME_ICON).append("</button>\n");
            out.append("</header>\n");
        }

        void renderNavigation(StringBuilder out) throws IOException {
            out.append("<nav class=\"side\" id=\"side\" aria-label=\"Contents\">\n<div class=\"side-inner\">\n");
            out.append("<p class=\"nav-title\">Guide</p>\n<ul class=\"nav-guide\">\n");
            for (Guide guide : guides()) out.append("<li><a href=\"#").append(attr(guide.id())).append("\">").append(guide.title()).append("</a></li>\n");
            out.append("</ul>\n");
            String currentGroup = "";
            for (String packageName : orderedPackages()) {
                String group = group(packageName);
                if (!group.equals(currentGroup)) {
                    if (!currentGroup.isEmpty()) out.append("</ul>\n");
                    currentGroup = group;
                    out.append("<p class=\"nav-title\"><a href=\"#group-").append(group).append("\">").append(group.equals("sdk") ? "Plugin SDK" : group.equals("testkit") ? "Test kit" : "Bundled plugin APIs")
                        .append("</a></p>\n<ul class=\"nav-packages\">\n");
                }
                PackageElement pkg = packages.get(packageName);
                String shortName = packageName.startsWith("dev.jasper.sdk.") ? packageName.substring("dev.jasper.sdk.".length()) : packageName.startsWith("dev.jasper.") ? packageName.substring("dev.jasper.".length()) : packageName;
                out.append("<li><details><summary><a href=\"#").append(attr(id(pkg))).append("\">").append(esc(shortName)).append("</a></summary><ul>\n");
                for (TypeElement type : typeOrder) {
                    if (!elements.getPackageOf(type).equals(pkg) || type.getEnclosingElement().getKind() != ElementKind.PACKAGE) continue;
                    out.append("<li><a href=\"#").append(attr(id(type))).append("\" class=\"k-").append(kindClass(type)).append("\">").append(esc(displayName(type))).append("</a></li>\n");
                }
                out.append("</ul></details></li>\n");
            }
            out.append("</ul>\n</div>\n</nav>\n");
        }

        // -- guide -------------------------------------------------------------------------------------------------

        record Guide(String id, String title, String html) { }

        List<Guide> guides;

        List<Guide> guides() throws IOException {
            if (guides != null) return guides;
            guides = new ArrayList<>();
            Path directory = root.resolve("tools/sdk-reference/guide");
            List<Path> pages;
            try (Stream<Path> stream = Files.list(directory)) {
                pages = stream.filter(path -> path.toString().endsWith(".html")).sorted().toList();
            }
            Pattern section = Pattern.compile("^\\s*<section[^>]*\\bid=\"([^\"]+)\"[^>]*>", Pattern.DOTALL);
            Pattern heading = Pattern.compile("<h2[^>]*>(.*?)</h2>", Pattern.DOTALL);
            for (Path page : pages) {
                String text = Files.readString(page, StandardCharsets.UTF_8).replace("\r\n", "\n");
                Matcher open = section.matcher(text);
                Matcher title = heading.matcher(text);
                if (!open.find() || !title.find()) {
                    fail(root.relativize(page) + ": a guide page starts with <section id=\"...\"> and has an <h2> title");
                    continue;
                }
                guides.add(new Guide(open.group(1), title.group(1).replaceAll("<[^>]+>", "").strip(), text));
            }
            return guides;
        }

        String renderGuide() throws IOException {
            StringBuilder out = new StringBuilder();
            out.append("<section class=\"hero\" id=\"top\">\n<p class=\"eyebrow\">Jasper Plugin SDK ").append(esc(version)).append("</p>\n")
                .append("<h1>Build the workstation you need.</h1>\n")
                .append("<p class=\"lead\">Jasper is a pluggable DevOps workstation: its SSH, SFTP and tunnels, credential vault, editor, file servers, project index, shell history and snippets are all plugins written against this SDK. ")
                .append("This reference is the guide to writing your own and the complete API they use.</p>\n");
            long typeCount = typeOrder.size();
            long packageCount = packages.keySet().stream().filter(name -> group(name).equals("sdk")).count();
            out.append("<ul class=\"stats\"><li><b>").append(packageCount).append("</b> SDK packages</li><li><b>").append(typeCount).append("</b> documented types</li><li><b>")
                .append(capabilityCount()).append("</b> capabilities</li><li><b>JDK only</b> no dependencies</li></ul>\n</section>\n");
            for (Guide guide : guides()) {
                anchors.add(guide.id());
                out.append(expand(guide)).append('\n');
            }
            return out.toString();
        }

        long capabilityCount() {
            return documentedTypes.stream().filter(t -> t.getQualifiedName().contentEquals("dev.jasper.sdk.Capabilities"))
                .flatMap(t -> ElementFilter.fieldsIn(t.getEnclosedElements()).stream()).filter(f -> f.getConstantValue() instanceof String).count();
        }

        static final Pattern GUIDE_LINK = Pattern.compile("\\{@(link|linkplain)\\s+([^\\s}]+)(?:\\s+([^}]*))?}");
        static final Pattern GUIDE_CODE = Pattern.compile("\\{@code\\s([^}]*)}");

        String expand(Guide guide) {
            String text = guide.html();
            text = text.replace("{@sdk.version}", esc(version));
            text = text.replace("<!-- generated:capabilities -->", capabilityTable());
            text = text.replace("<!-- generated:packages -->", packageTable());
            text = highlightBlocks(text);
            StringBuilder expanded = new StringBuilder();
            Matcher block = Pattern.compile("<pre class=\"code\".*?</pre>", Pattern.DOTALL).matcher(text);
            int last = 0;
            while (block.find()) {
                expanded.append(expandProse(text.substring(last, block.start()), guide)).append(block.group());
                last = block.end();
            }
            return expanded.append(expandProse(text.substring(last), guide)).toString();
        }

        String expandProse(String text, Guide guide) {
            Matcher link = GUIDE_LINK.matcher(text);
            StringBuilder out = new StringBuilder();
            while (link.find()) {
                String target = link.group(2);
                String anchor = guideTarget(target, guide);
                String label = link.group(3) != null ? link.group(3).strip() : defaultLabel(target.replaceFirst("^dev\\.jasper\\.[a-z.]+\\.(?=[A-Z])", ""), null);
                String html = link.group(1).equals("link") && link.group(3) == null ? "<code>" + esc(label) + "</code>" : label;
                link.appendReplacement(out, Matcher.quoteReplacement(anchor == null ? html : "<a href=\"#" + attr(anchor) + "\">" + html + "</a>"));
            }
            link.appendTail(out);
            Matcher code = GUIDE_CODE.matcher(out.toString());
            StringBuilder done = new StringBuilder();
            while (code.find()) code.appendReplacement(done, Matcher.quoteReplacement("<code>" + esc(code.group(1)) + "</code>"));
            code.appendTail(done);
            return done.toString();
        }

        /** Resolves a guide's {@code {@link Type}} or {@code {@link Type#member}} by qualified or unique simple name. */
        String guideTarget(String target, Guide guide) {
            String typePart = target.contains("#") ? target.substring(0, target.indexOf('#')) : target;
            String memberPart = target.contains("#") ? target.substring(target.indexOf('#') + 1) : null;
            List<TypeElement> candidates = documentedTypes.stream().filter(type -> type.getQualifiedName().contentEquals(typePart)
                || displayName(type).equals(typePart)).toList();
            if (candidates.isEmpty()) candidates = documentedTypes.stream()
                .filter(type -> type.getQualifiedName().toString().endsWith("." + typePart)).toList();
            if (candidates.size() != 1) {
                fail("guide " + guide.id() + ": {@link " + target + "} " + (candidates.isEmpty() ? "names no documented type" : "is ambiguous: " + candidates));
                return null;
            }
            TypeElement type = candidates.getFirst();
            if (memberPart == null) return id(type);
            String wanted = memberPart.replace(" ", "");
            List<String> matches = new ArrayList<>();
            for (Element member : type.getEnclosedElements()) {
                String anchor = anchorOf(member);
                if (anchor == null || !anchors.contains(anchor) && !willRender(member)) continue;
                String key = member instanceof ExecutableElement method ? memberKey(method) : member.getSimpleName().toString() + (member.getKind() == ElementKind.RECORD_COMPONENT ? "()" : "");
                String name = member.getSimpleName().toString();
                if (key.equals(wanted) || (!wanted.contains("(") && name.equals(wanted))) matches.add(anchor);
            }
            List<String> unique = matches.stream().distinct().toList();
            if (unique.size() != 1) {
                fail("guide " + guide.id() + ": {@link " + target + "} " + (unique.isEmpty() ? "names no documented member" : "is ambiguous; add the parameter types: " + unique));
                return null;
            }
            return unique.getFirst();
        }

        boolean willRender(Element member) {
            TypeElement owner = (TypeElement) member.getEnclosingElement();
            if (member.getKind() == ElementKind.RECORD_COMPONENT) return true;
            return members(owner, member.getKind()).contains(member);
        }

        String capabilityTable() {
            StringBuilder out = new StringBuilder("<table class=\"capabilities\"><thead><tr><th>Capability</th><th>Constant</th><th>What it allows</th><th>Since</th></tr></thead><tbody>\n");
            for (TypeElement type : documentedTypes) {
                if (!type.getQualifiedName().contentEquals("dev.jasper.sdk.Capabilities")) continue;
                for (VariableElement field : ElementFilter.fieldsIn(type.getEnclosedElements())) {
                    if (!(field.getConstantValue() instanceof String value)) continue;
                    Comment comment = comment(field);
                    out.append("<tr><td><code class=\"cap\">").append(esc(value)).append("</code></td><td><a href=\"#").append(attr(id(field))).append("\"><code>")
                        .append(esc(field.getSimpleName().toString())).append("</code></a></td><td>").append(comment.description()).append("</td><td>")
                        .append(comment.since().isEmpty() ? "" : comment.since()).append("</td></tr>\n");
                }
            }
            return out.append("</tbody></table>").toString();
        }

        String packageTable() {
            StringBuilder out = new StringBuilder("<table class=\"packages\"><thead><tr><th>Package</th><th>What it holds</th></tr></thead><tbody>\n");
            for (String name : orderedPackages()) {
                PackageElement pkg = packages.get(name);
                out.append("<tr><td><a href=\"#").append(attr(id(pkg))).append("\"><code>").append(esc(name)).append("</code></a></td><td>")
                    .append(comment(pkg).firstSentence()).append("</td></tr>\n");
            }
            return out.append("</tbody></table>").toString();
        }

        // -- checks and output -------------------------------------------------------------------------------------

        void checkAnchors(String page) {
            Set<String> ids = new LinkedHashSet<>();
            Matcher idMatcher = Pattern.compile("\\sid=\"([^\"]+)\"").matcher(page);
            while (idMatcher.find()) {
                if (!ids.add(unescape(idMatcher.group(1)))) fail("duplicate anchor id \"" + idMatcher.group(1) + "\"");
            }
            Matcher href = Pattern.compile("href=\"#([^\"]*)\"").matcher(page);
            Set<String> missing = new LinkedHashSet<>();
            while (href.find()) {
                String target = unescape(href.group(1));
                if (!target.isEmpty() && !ids.contains(target)) missing.add(target);
            }
            for (String target : missing) fail("a link points at #" + target + ", which nothing on the page carries");
            // Relative links lead from docs/, where the page lives, to the repository's Markdown guides.
            Matcher relative = Pattern.compile("href=\"(?![#]|[a-zA-Z][a-zA-Z0-9+.-]*:)([^\"]+)\"").matcher(page);
            Path home = root.resolve("docs");
            while (relative.find()) {
                String[] parts = unescape(relative.group(1)).split("#", 2);
                Path file = home.resolve(parts[0]).normalize();
                if (!Files.exists(file)) fail("a link points at " + relative.group(1) + ", but " + root.relativize(file) + " does not exist");
                else if (parts.length == 2 && file.toString().endsWith(".md") && !markdownAnchors(file).contains(parts[1]))
                    fail("a link points at " + relative.group(1) + ", but no heading in " + root.relativize(file) + " makes that anchor");
            }
        }

        /** Heading anchors as the documentation tests compute them: lower case, punctuation dropped, spaces to dashes. */
        static Set<String> markdownAnchors(Path file) {
            try {
                return Files.readAllLines(file, StandardCharsets.UTF_8).stream().filter(line -> line.startsWith("#"))
                    .map(line -> line.replaceFirst("^#+ +", "").toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_ -]", "").replace(' ', '-'))
                    .collect(Collectors.toSet());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        String searchJson() {
            StringBuilder out = new StringBuilder("[");
            for (int i = 0; i < search.size(); i++) {
                String[] entry = search.get(i);
                if (i > 0) out.append(',');
                out.append('[').append(json(entry[0])).append(',').append(json(entry[1])).append(',').append(json(entry[2])).append(',').append(json(entry[3])).append(']');
            }
            return out.append(']').toString();
        }

        static String json(String text) {
            StringBuilder out = new StringBuilder("\"");
            for (char c : text.toCharArray()) {
                switch (c) {
                    case '"' -> out.append("\\\"");
                    case '\\' -> out.append("\\\\");
                    case '<' -> out.append("\\u003c");
                    case '>' -> out.append("\\u003e");
                    case '&' -> out.append("\\u0026");
                    default -> out.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
                }
            }
            return out.append('"').toString();
        }

        static String resource(String name) throws IOException {
            return Files.readString(root.resolve("tools/sdk-reference").resolve(name), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }

        // -- code highlighting for the guide's <pre><code class="language-*"> blocks -----------------------------

        static final Pattern CODE_BLOCK = Pattern.compile("<pre><code class=\"language-([a-z]+)\">(.*?)</code></pre>", Pattern.DOTALL);

        static String highlightBlocks(String html) {
            Matcher matcher = CODE_BLOCK.matcher(html);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                String language = matcher.group(1);
                String code = unescape(matcher.group(2));
                String highlighted = switch (language) {
                    case "java", "kotlin" -> Highlight.java(code, language.equals("kotlin"));
                    case "toml" -> Highlight.toml(code);
                    case "shell", "bash" -> Highlight.shell(code);
                    case "json" -> Highlight.json(code);
                    default -> esc(code);
                };
                matcher.appendReplacement(out, Matcher.quoteReplacement("<pre class=\"code\" data-language=\"" + language + "\"><code>" + highlighted + "</code></pre>"));
            }
            matcher.appendTail(out);
            return out.toString();
        }
    }

    /** A small, deterministic highlighter for the guide's code samples. */
    static final class Highlight {
        static final Set<String> JAVA = Set.of("abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "if", "implements", "import", "instanceof",
            "int", "interface", "long", "new", "package", "private", "protected", "public", "record", "return", "sealed", "permits", "short",
            "static", "super", "switch", "synchronized", "this", "throw", "throws", "try", "var", "void", "volatile", "while", "yield", "true",
            "false", "null", "when");
        static final Set<String> KOTLIN = Set.of("val", "var", "fun", "plugins", "dependencies", "tasks", "true", "false", "null", "if", "else",
            "return", "object", "class", "import", "by", "in");

        static String java(String code, boolean kotlin) {
            StringBuilder out = new StringBuilder();
            int i = 0;
            while (i < code.length()) {
                char c = code.charAt(i);
                if (c == '/' && i + 1 < code.length() && code.charAt(i + 1) == '/') {
                    int end = code.indexOf('\n', i);
                    end = end < 0 ? code.length() : end;
                    out.append("<span class=\"c\">").append(esc(code.substring(i, end))).append("</span>");
                    i = end;
                } else if (c == '/' && i + 1 < code.length() && code.charAt(i + 1) == '*') {
                    int end = code.indexOf("*/", i + 2);
                    end = end < 0 ? code.length() : end + 2;
                    out.append("<span class=\"c\">").append(esc(code.substring(i, end))).append("</span>");
                    i = end;
                } else if (c == '"') {
                    int end = string(code, i);
                    out.append("<span class=\"s\">").append(esc(code.substring(i, end))).append("</span>");
                    i = end;
                } else if (c == '@' && i + 1 < code.length() && Character.isJavaIdentifierStart(code.charAt(i + 1))) {
                    int end = i + 1;
                    while (end < code.length() && (Character.isJavaIdentifierPart(code.charAt(end)) || code.charAt(end) == '.')) end++;
                    out.append("<span class=\"a\">").append(esc(code.substring(i, end))).append("</span>");
                    i = end;
                } else if (Character.isJavaIdentifierStart(c)) {
                    int end = i;
                    while (end < code.length() && Character.isJavaIdentifierPart(code.charAt(end))) end++;
                    String word = code.substring(i, end);
                    if ((kotlin ? KOTLIN : JAVA).contains(word)) out.append("<span class=\"k\">").append(word).append("</span>");
                    else if (Character.isUpperCase(word.charAt(0))) out.append("<span class=\"t\">").append(esc(word)).append("</span>");
                    else out.append(esc(word));
                    i = end;
                } else if (Character.isDigit(c)) {
                    int end = i;
                    while (end < code.length() && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '_' || code.charAt(end) == '.')) end++;
                    out.append("<span class=\"n\">").append(esc(code.substring(i, end))).append("</span>");
                    i = end;
                } else {
                    out.append(esc(String.valueOf(c)));
                    i++;
                }
            }
            return out.toString();
        }

        static int string(String code, int start) {
            if (code.startsWith("\"\"\"", start)) {
                int end = code.indexOf("\"\"\"", start + 3);
                return end < 0 ? code.length() : end + 3;
            }
            int i = start + 1;
            while (i < code.length() && code.charAt(i) != '"' && code.charAt(i) != '\n') i += code.charAt(i) == '\\' ? 2 : 1;
            return Math.min(code.length(), i + 1);
        }

        static String toml(String code) {
            StringBuilder out = new StringBuilder();
            for (String line : code.split("\n", -1)) {
                if (!out.isEmpty()) out.append('\n');
                String trimmed = line.stripLeading();
                if (trimmed.startsWith("#")) {
                    out.append("<span class=\"c\">").append(esc(line)).append("</span>");
                } else if (trimmed.startsWith("[")) {
                    out.append("<span class=\"t\">").append(esc(line)).append("</span>");
                } else {
                    int equals = line.indexOf('=');
                    if (equals > 0 && !line.substring(0, equals).contains("\"")) {
                        out.append("<span class=\"p\">").append(esc(line.substring(0, equals))).append("</span>=").append(tomlValue(line.substring(equals + 1)));
                    } else {
                        out.append(tomlValue(line));
                    }
                }
            }
            return out.toString();
        }

        static String tomlValue(String text) {
            StringBuilder out = new StringBuilder();
            int i = 0;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '"') {
                    int end = string(text, i);
                    out.append("<span class=\"s\">").append(esc(text.substring(i, end))).append("</span>");
                    i = end;
                } else if (c == '#') {
                    out.append("<span class=\"c\">").append(esc(text.substring(i))).append("</span>");
                    i = text.length();
                } else if (Character.isDigit(c) || text.startsWith("true", i) || text.startsWith("false", i)) {
                    int end = i;
                    while (end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '.' || text.charAt(end) == '_')) end++;
                    out.append("<span class=\"n\">").append(esc(text.substring(i, end))).append("</span>");
                    i = end;
                } else if (Character.isLetter(c)) {
                    int end = i;
                    while (end < text.length() && (Character.isLetterOrDigit(text.charAt(end)) || text.charAt(end) == '_' || text.charAt(end) == '-')) end++;
                    String word = text.substring(i, end);
                    int after = end;
                    while (after < text.length() && text.charAt(after) == ' ') after++;
                    out.append(after < text.length() && text.charAt(after) == '=' ? "<span class=\"p\">" + esc(word) + "</span>" : esc(word));
                    i = end;
                } else {
                    out.append(esc(String.valueOf(c)));
                    i++;
                }
            }
            return out.toString();
        }

        static String shell(String code) {
            StringBuilder out = new StringBuilder();
            for (String line : code.split("\n", -1)) {
                if (!out.isEmpty()) out.append('\n');
                if (line.stripLeading().startsWith("#")) out.append("<span class=\"c\">").append(esc(line)).append("</span>");
                else {
                    int space = line.indexOf(' ');
                    String command = space < 0 ? line : line.substring(0, space);
                    out.append("<span class=\"k\">").append(esc(command)).append("</span>").append(esc(space < 0 ? "" : line.substring(space)));
                }
            }
            return out.toString();
        }

        static String json(String code) {
            StringBuilder out = new StringBuilder();
            int i = 0;
            while (i < code.length()) {
                char c = code.charAt(i);
                if (c == '"') {
                    int end = string(code, i);
                    int after = end;
                    while (after < code.length() && Character.isWhitespace(code.charAt(after))) after++;
                    boolean key = after < code.length() && code.charAt(after) == ':';
                    out.append("<span class=\"").append(key ? "p" : "s").append("\">").append(esc(code.substring(i, end))).append("</span>");
                    i = end;
                } else if (Character.isDigit(c) || c == '-' || code.startsWith("true", i) || code.startsWith("false", i) || code.startsWith("null", i)) {
                    int end = i + 1;
                    while (end < code.length() && (Character.isLetterOrDigit(code.charAt(end)) || code.charAt(end) == '.')) end++;
                    out.append("<span class=\"n\">").append(esc(code.substring(i, end))).append("</span>");
                    i = end;
                } else {
                    out.append(esc(String.valueOf(c)));
                    i++;
                }
            }
            return out.toString();
        }
    }

    static String esc(String text) {
        StringBuilder out = new StringBuilder(text.length() + 16);
        for (char c : text.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    static String attr(String text) { return esc(text); }

    static String unescape(String html) {
        return html.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&");
    }

    /** Jasper's mark: a rounded tile with a prompt chevron and cursor, drawn in the page's accent. */
    static final String MARK = "<svg viewBox=\"0 0 32 32\" width=\"28\" height=\"28\"><rect x=\"1\" y=\"1\" width=\"30\" height=\"30\" rx=\"8\" fill=\"var(--accent)\"/>"
        + "<path d=\"M9 11l6 5-6 5\" fill=\"none\" stroke=\"#fff\" stroke-width=\"2.6\" stroke-linecap=\"round\" stroke-linejoin=\"round\"/>"
        + "<rect x=\"17\" y=\"19.5\" width=\"7\" height=\"2.6\" rx=\"1.3\" fill=\"#fff\"/></svg>";

    static final String THEME_ICON = "<svg viewBox=\"0 0 24 24\" width=\"18\" height=\"18\" aria-hidden=\"true\"><path d=\"M12 3a9 9 0 1 0 9 9 7 7 0 0 1-9-9z\" fill=\"currentColor\"/></svg>";
}
