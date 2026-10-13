import java.lang.classfile.*;
import java.lang.classfile.constantpool.*;
import java.lang.classfile.instruction.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;

/** JBR 25 build gate. Input directories contain authored main classes only, never vendor jars. */
public final class PluginPresentationCheck {
    public static void main(String[] args) throws Exception {
        if (args[0].equals("--self-test")) { selfTest(Path.of(args[1])); return; }
        if (args[0].equals("--sdk-self-test")) { sdkSelfTest(Path.of(args[1])); return; }
        var problems = args[0].equals("--sdk") ? checkSdk(Path.of(args[1])) : check(Path.of(args[0]));
        if (!problems.isEmpty()) throw new IllegalStateException(String.join("\n", problems));
    }

    private static final Set<String> CANVASES = Set.of("dev/jasper/editor/ui/DocumentArea", "dev/jasper/editor/vim/VimTextArea");
    private static final String EDITOR_THEME = "dev/jasper/editor/ui/EditorTheme";
    // Plugins reach the clipboard through Platform; only the Editor's Vim registers keep their own.
    private static final String EDITOR_CLIPBOARD = "dev/jasper/editor/ui/SystemClipboard";
    private static final Set<String> PAINT = Set.of("paint", "paintComponent", "paintBorder", "paintChildren", "paintIcon", "update");
    private static final Set<String> STYLE = Set.of("setBackground", "setForeground", "setFont", "setBorder", "setOpaque",
        "setUI", "putClientProperty", "setSelectionColor", "setSelectedTextColor", "setCaretColor", "setSelectionBackground", "setSelectionForeground",
        "setSize", "setBounds", "setLocation", "setPreferredSize", "setMinimumSize", "setMaximumSize", "setMargin",
        "setLayout", "setAlignmentX", "setAlignmentY", "setHorizontalAlignment", "setVerticalAlignment",
        "setHorizontalTextPosition", "setVerticalTextPosition", "setIconTextGap", "setIcon", "setDisabledIcon",
        "setSelectedIcon", "setDisabledSelectedIcon", "setPressedIcon", "setRolloverIcon", "setRolloverSelectedIcon",
        "setIconImage", "setIconImages", "setBorderPainted", "setContentAreaFilled", "setFocusPainted");
    // Delegation is operation-specific: EditorTheme may update only the document, gutter and their viewports.
    // It cannot construct controls, paint, set arbitrary client properties, or style ordinary controls.
    private static final Map<String, Set<String>> EDITOR_OPERATIONS = Map.of(
        "org/fife/ui/rsyntaxtextarea/RSyntaxTextArea", Set.of("setBackground", "setForeground", "setOpaque", "setCaretColor", "setSelectionColor"),
        "org/fife/ui/rtextarea/RTextScrollPane", Set.of("setOpaque", "setBackground"),
        "org/fife/ui/rtextarea/Gutter", Set.of("setOpaque", "setBackground", "putClientProperty"),
        "javax/swing/JViewport", Set.of("setOpaque", "setBackground"),
        "javax/swing/JComponent", Set.of("setOpaque"));
    private static final Set<String> DOCUMENT_CONSTRUCTORS = Set.of("javax/swing/JComponent", "javax/swing/JMenuItem",
        "org/fife/ui/rtextarea/RTextScrollPane", "dev/jasper/editor/vim/VimTextArea");

    static List<String> check(Path classes) throws Exception {
        var models = models(classes);
        var problems = new ArrayList<String>();
        for (var model : models.values()) {
            String name = model.thisClass().asInternalName(), sourceOwner = name.split("\\$", 2)[0];
            boolean canvas = CANVASES.contains(sourceOwner), helper = sourceOwner.equals(EDITOR_THEME);
            // The exact class AND SourceFile must agree; nested classes inherit only that exact source owner.
            String source = model.findAttribute(Attributes.sourceFile()).map(a -> a.sourceFile().stringValue()).orElse("");
            boolean exactSource = source.equals(sourceOwner.substring(sourceOwner.lastIndexOf('/') + 1) + ".java");
            if (!exactSource) { canvas = false; helper = false; }
            for (var method : model.methods()) {
                String methodName = method.methodName().stringValue();
                if (PAINT.contains(methodName) && method.methodType().stringValue().contains("Ljava/awt/Graphics") && !canvas)
                    problems.add(name + " declares ordinary paint method " + methodName);
                if (helper && method.code().isPresent()) {
                    var instructions = method.code().get().elementList().stream().filter(Instruction.class::isInstance)
                        .map(Instruction.class::cast).toList();
                    for (int i = 0; i < instructions.size(); i++) {
                        if (instructions.get(i) instanceof InvokeInstruction call && call.name().stringValue().equals("putClientProperty")) {
                            boolean marker = i >= 2
                                && instructions.get(i - 2) instanceof ConstantInstruction.LoadConstantInstruction key
                                && key.constantEntry() instanceof StringEntry value
                                && value.stringValue().equals("jasper.editor.gutterOpacityFollower")
                                && instructions.get(i - 1) instanceof FieldInstruction flag
                                && flag.opcode() == Opcode.GETSTATIC && flag.owner().asInternalName().equals("java/lang/Boolean")
                                && flag.name().stringValue().equals("TRUE");
                            if (!marker) problems.add(name + " writes unsupported specialized client property");
                        }
                    }
                }
            }
            for (var entry : model.constantPool()) {
                if (!(entry instanceof MemberRefEntry ref)) continue;
                String owner = ref.owner().asInternalName(), member = ref.name().stringValue(), descriptor = ref.type().stringValue();
                String operation = owner + "." + member + descriptor;
                boolean visible = visible(owner, models, new HashSet<>());
                if (member.equals("<init>") && visible) {
                    boolean allowed = exactSource && sourceOwner.equals("dev/jasper/editor/ui/EditorTab") && owner.equals("dev/jasper/editor/ui/DocumentArea")
                        || canvas && (sourceOwner.endsWith("/DocumentArea") ? DOCUMENT_CONSTRUCTORS.contains(owner)
                        : Set.of("org/fife/ui/rsyntaxtextarea/RSyntaxTextArea", "org/fife/ui/rsyntaxtextarea/RSyntaxTextAreaUI",
                            "dev/jasper/editor/vim/VimTextArea$1").contains(owner));
                    if (!allowed) problems.add(name + " constructs visible control " + operation);
                }
                boolean graphics = owner.equals("java/awt/Graphics") || owner.equals("java/awt/Graphics2D");
                if (graphics && !canvas) problems.add(name + " paints outside a custom canvas: " + operation);
                boolean style = visible && STYLE.contains(member);
                if (style && !canvas && !(helper && EDITOR_OPERATIONS.getOrDefault(owner, Set.of()).contains(member)))
                    problems.add(name + " styles ordinary controls: " + operation);
                if (owner.equals("javax/swing/UIManager") && !member.equals("getString"))
                    problems.add(name + " reads/writes global presentation defaults: " + operation);
                if (owner.startsWith("javax/swing/BorderFactory") && !canvas)
                    problems.add(name + " builds ordinary borders: " + operation);
                boolean awtClipboard = owner.equals("java/awt/Toolkit")
                    && (member.equals("getSystemClipboard") || member.equals("getSystemSelection"));
                if (awtClipboard && !(exactSource && sourceOwner.equals(EDITOR_CLIPBOARD)))
                    problems.add(name + " uses the AWT clipboard directly; use Platform.copy, copySecret or clipboardText: " + operation);
            }
        }
        return problems;
    }

    private static boolean visible(String name, Map<String, ClassModel> models, Set<String> visited) {
        if (!visited.add(name)) return false;
        var own = models.get(name);
        if (own != null) return own.superclass().map(parent -> visible(parent.asInternalName(), models, visited)).orElse(false)
            || own.interfaces().stream().anyMatch(parent -> visible(parent.asInternalName(), models, visited));
        // Resolve only platform and the pinned editor vendor metadata, with initialization disabled.
        // The checker never iterates vendor jars or treats them as authored plugin classes.
        if (name.startsWith("java/") || name.startsWith("javax/") || name.startsWith("org/fife/ui/")) {
            try {
                Class<?> type = Class.forName(name.replace('/', '.'), false, PluginPresentationCheck.class.getClassLoader());
                return java.awt.Component.class.isAssignableFrom(type) || javax.swing.Icon.class.isAssignableFrom(type)
                    || javax.swing.border.Border.class.isAssignableFrom(type) || javax.swing.plaf.ComponentUI.class.isAssignableFrom(type);
            } catch (ClassNotFoundException e) { throw new IllegalStateException("Missing gate type metadata: " + name, e); }
        }
        return false;
    }

    private static Map<String, ClassModel> models(Path classes) throws Exception {
        var models = new LinkedHashMap<String, ClassModel>();
        try (var files = Files.walk(classes)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                var model = ClassFile.of().parse(Files.readAllBytes(file));
                models.put(model.thisClass().asInternalName(), model);
            }
        }
        return models;
    }

    // Descriptor exceptions permit values/custom roots, never inheritance of the raw presentation API.
    private static void checkSdkParents(String exposedOwner, ClassModel model, Map<String, ClassModel> models,
                                        Set<String> visited, List<String> problems) {
        if (!visited.add(model.thisClass().asInternalName())) return;
        var parents = new ArrayList<ClassEntry>(model.interfaces());
        model.superclass().ifPresent(parents::add);
        for (var parent : parents) {
            String type = parent.asInternalName();
            if (type.startsWith("javax/swing/") || type.startsWith("java/awt/"))
                problems.add(exposedOwner + " inherits unsupported presentation type " + type);
            var ownParent = models.get(type);
            if (ownParent != null) checkSdkParents(exposedOwner, ownParent, models, visited, problems);
        }
    }

    static List<String> checkSdk(Path classes) throws Exception {
        var problems = new ArrayList<String>();
        var models = models(classes);
        for (var model : models.values()) {
            String owner = model.thisClass().asInternalName();
            checkSdkParents(owner, model, models, new HashSet<>(), problems);
            var signatures = new ArrayList<String>();
            model.findAttribute(Attributes.signature()).ifPresent(a -> signatures.add(a.signature().stringValue()));
            for (var method : model.methods()) if ((method.flags().flagsMask() & 5) != 0) {
                signatures.add(method.methodType().stringValue());
                method.findAttribute(Attributes.signature()).ifPresent(a -> signatures.add(a.signature().stringValue()));
            }
            for (var field : model.fields()) if ((field.flags().flagsMask() & 5) != 0) {
                signatures.add(field.fieldType().stringValue());
                field.findAttribute(Attributes.signature()).ifPresent(a -> signatures.add(a.signature().stringValue()));
            }
            for (String signature : signatures) {
                var matcher = java.util.regex.Pattern.compile("L((?:javax/swing|java/awt)/[A-Za-z0-9_$/]+)").matcher(signature);
                while (matcher.find()) {
                    String type = matcher.group(1);
                    boolean allowed = Set.of("java/awt/Color", "java/awt/Dimension").contains(type)
                        || owner.equals("dev/jasper/sdk/ui/Appearance") && type.equals("java/awt/Component")
                        || owner.equals("dev/jasper/sdk/ui/CustomContent") && type.equals("javax/swing/JComponent")
                        || owner.equals("dev/jasper/sdk/ui/ContentCommand") && type.equals("javax/swing/KeyStroke");
                    if (!allowed) problems.add(owner + " exposes unsupported presentation type " + type + " in " + signature);
                }
            }
        }
        return problems;
    }

    static void sdkSelfTest(Path root) throws Exception {
        var bad = compile(root.resolve("bad"), "dev/jasper/sdk/ui/Bad", "public class Bad { public java.util.Optional<javax.swing.Icon> icon() { return java.util.Optional.empty(); } public javax.swing.JPanel panel(){ return null; } }");
        var problems = checkSdk(bad);
        for (String forbidden : List.of("javax/swing/Icon", "javax/swing/JPanel"))
            if (problems.stream().noneMatch(problem -> problem.contains(forbidden)))
                throw new AssertionError("SDK checker accepted signature " + forbidden);
        var inheritance = Map.of(
            "BadIcon", "public interface BadIcon extends javax.swing.Icon {}",
            "BadPanel", "public class BadPanel extends javax.swing.JPanel {}",
            "IndirectIcon", "public interface IndirectIcon extends IconParent {} interface IconParent extends javax.swing.Icon {}",
            "IndirectPanel", "public class IndirectPanel extends PanelParent {} class PanelParent extends javax.swing.JPanel {}",
            "CustomContent", "public class CustomContent extends javax.swing.JComponent {}");
        var failures = new ArrayList<String>();
        for (var fixture : inheritance.entrySet()) {
            String owner = "dev/jasper/sdk/ui/" + fixture.getKey();
            var actual = checkSdk(compile(root.resolve("inheritance_" + fixture.getKey()), owner, fixture.getValue()));
            String forbidden = fixture.getKey().endsWith("Icon") ? "javax/swing/Icon"
                : fixture.getKey().equals("CustomContent") ? "javax/swing/JComponent" : "javax/swing/JPanel";
            if (actual.stream().noneMatch(problem -> problem.contains(owner + " inherits unsupported presentation type " + forbidden)))
                failures.add("SDK checker accepted inheritance " + owner + " -> " + forbidden + ": " + actual);
        }
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
        var domain = compile(root.resolve("domain"), "dev/jasper/sdk/ui/DomainChild",
            "public class DomainChild extends DomainParent {} class DomainParent { public java.awt.Dimension size(){return null;} public java.awt.Color color(){return null;} }");
        if (!checkSdk(domain).isEmpty()) throw new AssertionError("SDK checker rejected domain value inheritance");
        var good = compile(root.resolve("good"), "dev/jasper/sdk/ui/CustomContent", "public class CustomContent { public javax.swing.JComponent component(){return null;} }");
        if (!checkSdk(good).isEmpty()) throw new AssertionError("SDK checker rejected custom content root");
        System.out.println("SDK signature fixtures: raw/generic and 5 inheritance rejections, custom root and domain inheritance acceptance passed");
    }

    static void selfTest(Path root) throws Exception {
        var cases = new LinkedHashMap<String, String>();
        cases.put("fixture/Button", "class Button { Object create() { return new javax.swing.JButton(); } }");
        cases.put("fixture/Paint", "class Paint extends javax.swing.JComponent { protected void paintComponent(java.awt.Graphics g) {} }");
        cases.put("fixture/ColoredIcon", "class ColoredIcon implements javax.swing.Icon { public int getIconWidth(){return 16;} public int getIconHeight(){return 16;} public void paintIcon(java.awt.Component c, java.awt.Graphics g,int x,int y){g.setColor(new java.awt.Color(123)); g.fillRect(x,y,16,16);} }");
        cases.put("fixture/Style", "class Style { void apply(javax.swing.JComponent c) { c.putClientProperty(\"FlatLaf.style\", \"arc: 9\"); } }");
        cases.put("fixture/Margin", "class Margin { void apply(javax.swing.JButton b) { b.setMargin(new java.awt.Insets(99,99,99,99)); } }");
        cases.put("fixture/PreferredSize", "class PreferredSize { void apply(java.awt.Component c) { c.setPreferredSize(new java.awt.Dimension(900,900)); } }");
        cases.put("fixture/IconAssignment", "class IconAssignment { void apply(javax.swing.AbstractButton b, javax.swing.Icon i) { b.setIcon(i); } }");
        cases.put("fixture/InheritedSize", "class InheritedSize extends javax.swing.JLabel { void apply(){setMinimumSize(new java.awt.Dimension(900,900));} }");
        cases.put("fixture/InheritedStyle", "class InheritedStyle extends javax.swing.JLabel { void style(){setForeground(java.awt.Color.RED);} }");
        cases.put("fixture/Clipboard", "class Clipboard { Object get() { return java.awt.Toolkit.getDefaultToolkit().getSystemClipboard(); } }");
        cases.put("dev/jasper/editor/ui/Other", "class Other { Object create(){return new javax.swing.JButton();} }");
        cases.put("dev/jasper/editor/ui/EditorTheme", "class EditorTheme { Object create(){return new javax.swing.JButton();} }");
        cases.put("dev/jasper/editor/ui/DocumentArea", "class DocumentArea extends javax.swing.JComponent { Object create(){return new javax.swing.JButton();} }");
        var failures = new ArrayList<String>();
        for (var fixture : cases.entrySet()) {
            Path classes = compile(root.resolve(fixture.getKey().replace('/', '_')), fixture.getKey(), fixture.getValue());
            var problems = check(classes);
            String expected = switch (fixture.getKey()) {
                case "fixture/Paint" -> "declares ordinary paint method";
                case "fixture/ColoredIcon" -> "paints outside a custom canvas";
                case "fixture/Style", "fixture/InheritedStyle", "fixture/Margin", "fixture/PreferredSize", "fixture/IconAssignment", "fixture/InheritedSize" -> "styles ordinary controls";
                case "fixture/Clipboard" -> "uses the AWT clipboard directly";
                default -> "constructs visible control";
            };
            if (problems.stream().noneMatch(problem -> problem.contains(expected)))
                failures.add("Accepted forbidden operation " + expected + " in " + fixture.getKey() + ": " + problems);
        }
        var positive = Map.of(
            "fixture/Domain", "class Domain { java.awt.Color color = new java.awt.Color(123); java.awt.Point point = new java.awt.Point(1,2); java.awt.Dimension size = new java.awt.Dimension(3,4); javax.swing.Timer timer = new javax.swing.Timer(5,e -> {}); void resize(){size.setSize(7,8);} }",
            "dev/jasper/editor/ui/DocumentArea", "class DocumentArea extends javax.swing.JComponent { void geometry(){setPreferredSize(new java.awt.Dimension(900,900));} protected void paintComponent(java.awt.Graphics g) {g.setColor(java.awt.Color.RED); g.fillRect(0,0,4,4);} }",
            "dev/jasper/editor/ui/EditorTheme", "class EditorTheme { void apply(javax.swing.JViewport v){v.setOpaque(false); v.setBackground(new java.awt.Color(123));} }",
            "dev/jasper/editor/ui/SystemClipboard", "class SystemClipboard { Object get() { return java.awt.Toolkit.getDefaultToolkit().getSystemClipboard(); } }");
        for (var fixture : positive.entrySet()) {
            var problems = check(compile(root.resolve("positive_" + fixture.getKey().replace('/', '_')), fixture.getKey(), fixture.getValue()));
            if (!problems.isEmpty()) failures.add("Rejected legitimate fixture " + fixture.getKey() + ": " + problems);
        }
        if (!failures.isEmpty()) throw new AssertionError(String.join("\n", failures));
        System.out.println("Presentation checker fixtures: " + cases.size() + " negative, " + positive.size() + " positive passed");
    }

    static Path compile(Path root, String name, String body) throws Exception {
        Path source = root.resolve(name + ".java"), classes = root.resolve("classes");
        Files.createDirectories(source.getParent()); Files.createDirectories(classes);
        Files.writeString(source, "package " + name.substring(0, name.lastIndexOf('/')).replace('/', '.') + ";\n" + body);
        int result = ToolProvider.getSystemJavaCompiler().run(null, null, null, "-d", classes.toString(), source.toString());
        if (result != 0) throw new AssertionError("Fixture did not compile: " + name);
        return classes;
    }
}
