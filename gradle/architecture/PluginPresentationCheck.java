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
    private static final Set<String> PAINT = Set.of("paint", "paintComponent", "paintBorder", "paintChildren", "paintIcon", "update");
    private static final Set<String> STYLE = Set.of("setBackground", "setForeground", "setFont", "setBorder", "setOpaque",
        "setUI", "putClientProperty", "setSelectionColor", "setSelectedTextColor", "setCaretColor", "setSelectionBackground", "setSelectionForeground");
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
        var models = new LinkedHashMap<String, ClassModel>();
        try (var files = Files.walk(classes)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).sorted().toList()) {
                var model = ClassFile.of().parse(Files.readAllBytes(file));
                models.put(model.thisClass().asInternalName(), model);
            }
        }
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
                boolean style = STYLE.contains(member) && (visible || owner.startsWith("javax/swing/") || owner.startsWith("org/fife/ui/"));
                if (style && !canvas && !(helper && EDITOR_OPERATIONS.getOrDefault(owner, Set.of()).contains(member)))
                    problems.add(name + " styles ordinary controls: " + operation);
                if (owner.equals("javax/swing/UIManager") && !member.equals("getString"))
                    problems.add(name + " reads/writes global presentation defaults: " + operation);
                if (owner.startsWith("javax/swing/BorderFactory") && !canvas)
                    problems.add(name + " builds ordinary borders: " + operation);
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

    static List<String> checkSdk(Path classes) throws Exception {
        var problems = new ArrayList<String>();
        try (var files = Files.walk(classes)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                var model = ClassFile.of().parse(Files.readAllBytes(file));
                String owner = model.thisClass().asInternalName();
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
        }
        return problems;
    }

    static void sdkSelfTest(Path root) throws Exception {
        var bad = compile(root.resolve("bad"), "dev/jasper/sdk/ui/Bad", "public class Bad { public java.util.Optional<javax.swing.Icon> icon() { return java.util.Optional.empty(); } public javax.swing.JPanel panel(){ return null; } }");
        var problems = checkSdk(bad);
        for (String forbidden : List.of("javax/swing/Icon", "javax/swing/JPanel"))
            if (problems.stream().noneMatch(problem -> problem.contains(forbidden)))
                throw new AssertionError("SDK checker accepted signature " + forbidden);
        var good = compile(root.resolve("good"), "dev/jasper/sdk/ui/CustomContent", "public class CustomContent { public javax.swing.JComponent component(){return null;} }");
        if (!checkSdk(good).isEmpty()) throw new AssertionError("SDK checker rejected custom content root");
        System.out.println("SDK signature fixtures: raw/generic rejection and custom root acceptance passed");
    }

    static void selfTest(Path root) throws Exception {
        var cases = new LinkedHashMap<String, String>();
        cases.put("fixture/Button", "class Button { Object create() { return new javax.swing.JButton(); } }");
        cases.put("fixture/Paint", "class Paint extends javax.swing.JComponent { protected void paintComponent(java.awt.Graphics g) {} }");
        cases.put("fixture/ColoredIcon", "class ColoredIcon implements javax.swing.Icon { public int getIconWidth(){return 16;} public int getIconHeight(){return 16;} public void paintIcon(java.awt.Component c, java.awt.Graphics g,int x,int y){g.setColor(new java.awt.Color(123)); g.fillRect(x,y,16,16);} }");
        cases.put("fixture/Style", "class Style { void apply(javax.swing.JComponent c) { c.putClientProperty(\"FlatLaf.style\", \"arc: 9\"); } }");
        cases.put("fixture/InheritedStyle", "class InheritedStyle extends javax.swing.JLabel { void style(){setForeground(java.awt.Color.RED);} }");
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
                case "fixture/Style", "fixture/InheritedStyle" -> "styles ordinary controls";
                default -> "constructs visible control";
            };
            if (problems.stream().noneMatch(problem -> problem.contains(expected)))
                failures.add("Accepted forbidden operation " + expected + " in " + fixture.getKey() + ": " + problems);
        }
        var positive = Map.of(
            "fixture/Domain", "class Domain { java.awt.Color color = new java.awt.Color(123); java.awt.Point point = new java.awt.Point(1,2); java.awt.Dimension size = new java.awt.Dimension(3,4); javax.swing.Timer timer = new javax.swing.Timer(5,e -> {}); }",
            "dev/jasper/editor/ui/DocumentArea", "class DocumentArea extends javax.swing.JComponent { protected void paintComponent(java.awt.Graphics g) {g.setColor(java.awt.Color.RED); g.fillRect(0,0,4,4);} }",
            "dev/jasper/editor/ui/EditorTheme", "class EditorTheme { void apply(javax.swing.JViewport v){v.setOpaque(false); v.setBackground(new java.awt.Color(123));} }");
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
