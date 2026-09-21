package dev.jasper.terminal.architecture;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class TerminalArchitectureTest {
    private static Path repo() {
        return Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    }
    @Test void noProductionTypesRemainInTheFlatPackage() throws Exception {
        Path flat = repo().resolve("jasper-terminal/src/main/java/dev/jasper/terminal");
        try (var files = Files.list(flat)) {
            assertThat(files.filter(p -> p.toString().endsWith(".java"))
                .filter(p -> !p.getFileName().toString().equals("package-info.java")).toList()).isEmpty();
        }
    }
    @Test void publiclyAccessibleSignaturesDoNotExposeJediTerm() throws Exception {
        Path classes = repo().resolve("jasper-terminal/build/classes/java/main");
        try (var files = Files.walk(classes)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".class")).toList()) {
                String name = classes.relativize(file).toString().replace('\\','.').replace('/','.');
                name = name.substring(0,name.length()-6);
                if (name.endsWith("package-info") || name.equals("module-info")) continue;
                checkSignatures(Class.forName(name,false,getClass().getClassLoader()));
            }
        }
    }
private static void assertVendorFree(java.lang.reflect.Type type,
        java.util.Set<java.lang.reflect.Type> seen) {
    if (type == null || !seen.add(type)) return;
    if (type instanceof Class<?> c) {
        assertThat(c.getName()).doesNotStartWith("com.jediterm.");
        if (c.isArray()) assertVendorFree(c.getComponentType(),seen);
    } else if (type instanceof java.lang.reflect.ParameterizedType p) {
        assertVendorFree(p.getRawType(),seen);
        assertVendorFree(p.getOwnerType(),seen);
        for (var t : p.getActualTypeArguments()) assertVendorFree(t,seen);
    } else if (type instanceof java.lang.reflect.GenericArrayType a) {
        assertVendorFree(a.getGenericComponentType(),seen);
    } else if (type instanceof java.lang.reflect.WildcardType w) {
        for (var t : w.getUpperBounds()) assertVendorFree(t,seen);
        for (var t : w.getLowerBounds()) assertVendorFree(t,seen);
    } else if (type instanceof java.lang.reflect.TypeVariable<?> v) {
        for (var t : v.getBounds()) assertVendorFree(t,seen);
    }
}
private static boolean exportedMember(int modifiers) {
    return java.lang.reflect.Modifier.isPublic(modifiers)
        || java.lang.reflect.Modifier.isProtected(modifiers);
}
private static void checkSignatures(Class<?> type) {
    for (Class<?> owner = type; owner != null; owner = owner.getEnclosingClass())
        if (!java.lang.reflect.Modifier.isPublic(owner.getModifiers())) return;
    var seen = new java.util.HashSet<java.lang.reflect.Type>();
    assertVendorFree(type.getGenericSuperclass(),seen);
    for (var t : type.getGenericInterfaces()) assertVendorFree(t,seen);
    for (var t : type.getTypeParameters()) assertVendorFree(t,seen);
    for (var m : type.getDeclaredMethods()) if (exportedMember(m.getModifiers())) {
        assertVendorFree(m.getGenericReturnType(),seen);
        for (var t : m.getGenericParameterTypes()) assertVendorFree(t,seen);
        for (var t : m.getGenericExceptionTypes()) assertVendorFree(t,seen);
        for (var t : m.getTypeParameters()) assertVendorFree(t,seen);
    }
    for (var c : type.getDeclaredConstructors()) if (exportedMember(c.getModifiers())) {
        for (var t : c.getGenericParameterTypes()) assertVendorFree(t,seen);
        for (var t : c.getGenericExceptionTypes()) assertVendorFree(t,seen);
        for (var t : c.getTypeParameters()) assertVendorFree(t,seen);
    }
    for (var f : type.getDeclaredFields()) if (exportedMember(f.getModifiers()))
        assertVendorFree(f.getGenericType(),seen);
}

    @Test void applicationSourceUsesOnlySupportedTerminalPackages() throws Exception {
        Path app = repo().resolve("jasper-app/src/main/java");
        try (var files = Files.walk(app)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                assertThat(text).as(file.toString()).doesNotContain("dev.jasper.terminal.internal.", ".internalAccess(");
            }
        }
    }
    @Test void vendorImportsStayInEmulation() throws Exception {
        Path source = repo().resolve("jasper-terminal/src/main/java");
        try (var files = Files.walk(source)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = Files.readString(file);
                if (!text.contains("package dev.jasper.terminal.internal.emulation;"))
                    assertThat(text).as(file.toString()).doesNotContain("import com.jediterm.");
            }
        }
    }
}
