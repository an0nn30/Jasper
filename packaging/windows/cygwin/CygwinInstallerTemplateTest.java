import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;

/** The production WiX template, through the entry point packageInstaller runs: the Cygwin action and the browser registration. */
public final class CygwinInstallerTemplateTest {
    private static final String ENTRY = "classes/jdk/jpackage/internal/resources/main.wxs";
    // JBR 25's main.wxs, cut to the elements around the parts the template changes.
    private static final String TEMPLATE = """
        <?xml version="1.0" encoding="utf-8"?>
        <Wix xmlns="http://schemas.microsoft.com/wix/2006/wi"
             xmlns:util="http://schemas.microsoft.com/wix/UtilExtension">
          <Product Id="$(var.JpProductCode)" Name="$(var.JpAppName)" Language="1033" Version="$(var.JpAppVersion)"
            Manufacturer="$(var.JpAppVendor)" UpgradeCode="$(var.JpProductUpgradeCode)">
            <Directory Id="TARGETDIR" Name="SourceDir"/>
            <Feature Id="DefaultFeature" Title="!(loc.MainFeatureTitle)" Level="1">
              <ComponentGroupRef Id="Shortcuts"/>
              <ComponentGroupRef Id="Files"/>
              <ComponentGroupRef Id="FileAssociations"/>
              <ComponentGroupRef Id="FragmentOsCondition"/>
            </Feature>
            <UIRef Id="JpUI"/>
            <InstallExecuteSequence>
              <LaunchConditions Before="AppSearch"/>
              <RemoveExistingProducts Before="CostInitialize"/>
            </InstallExecuteSequence>
          </Product>
        </Wix>
        """;
    // Root|key|value name ("" for the default value)|data, as Windows reads them after the installation.
    private static final List<String> REGISTRATION = List.of(
        "HKCU|Software\\RegisteredApplications|Jasper|Software\\Jasper\\Capabilities",
        "HKCU|Software\\Jasper\\Capabilities|ApplicationName|Jasper",
        "HKCU|Software\\Jasper\\Capabilities|ApplicationDescription|Jasper's built-in web browser",
        "HKCU|Software\\Jasper\\Capabilities\\URLAssociations|http|JasperURL",
        "HKCU|Software\\Jasper\\Capabilities\\URLAssociations|https|JasperURL",
        "HKCU|Software\\Classes\\JasperURL||Jasper URL",
        "HKCU|Software\\Classes\\JasperURL\\DefaultIcon||\"[INSTALLDIR]Jasper.exe\",0",
        "HKCU|Software\\Classes\\JasperURL\\shell\\open\\command||\"[INSTALLDIR]Jasper.exe\" --url \"%1\"",
        "HKCU|Software\\Classes\\JasperFile||Jasper document",
        "HKCU|Software\\Classes\\JasperFile\\DefaultIcon||\"[INSTALLDIR]Jasper.exe\",0",
        "HKCU|Software\\Classes\\JasperFile\\shell\\open\\command||\"[INSTALLDIR]Jasper.exe\" \"%1\"");
    // The keys Jasper owns, deleted with everything below them; RegisteredApplications only loses its Jasper value.
    private static final Set<String> REMOVED_KEYS = Set.of("HKCU|Software\\Jasper\\Capabilities", "HKCU|Software\\Classes\\JasperURL",
        "HKCU|Software\\Classes\\JasperFile");
    private static int checks, fixtures;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("installer-template-test");
        try {
            verify(transform(root, TEMPLATE));
            String feature = TEMPLATE.substring(TEMPLATE.indexOf("    <Feature"), TEMPLATE.indexOf("    <UIRef"));
            rejects(root, TEMPLATE.replace(feature, ""), "a template without a feature");
            rejects(root, TEMPLATE.replace(feature, feature + feature.replace("DefaultFeature", "SecondFeature")), "a template with two features");
            String runtime = "this runtime has no Windows template";
            Path jmod = Path.of(System.getProperty("java.home"), "jmods", "jdk.jpackage.jmod");
            if (Files.isRegularFile(jmod)) try (var archive = new ZipFile(jmod.toFile())) {
                if (archive.getEntry(ENTRY) != null) {
                    Path resources = root.resolve("runtime-resources");
                    CygwinInstallerTemplate.main(new String[]{jmod.toString(), resources.toString()});
                    verify(Files.readString(resources.resolve("main.wxs")));
                    runtime = "including this runtime's own template";
                }
            }
            System.out.println("Installer template checks passed: " + checks + "; " + runtime);
        } finally { try (var files = Files.walk(root)) { for (Path p : files.sorted(Comparator.reverseOrder()).toList()) Files.delete(p); } }
    }

    /** Writes the template into a jmod-shaped archive and runs the transform packageInstaller runs on the JBR's jmod. */
    private static String transform(Path root, String template) throws Exception {
        fixtures++;
        Path jmod = root.resolve("jdk.jpackage-" + fixtures + ".jmod"), resources = root.resolve("resources-" + fixtures);
        try (var zip = new ZipOutputStream(Files.newOutputStream(jmod))) {
            zip.putNextEntry(new ZipEntry(ENTRY)); zip.write(template.getBytes(StandardCharsets.UTF_8)); zip.closeEntry();
        }
        CygwinInstallerTemplate.main(new String[]{jmod.toString(), resources.toString()});
        return Files.readString(resources.resolve("main.wxs"));
    }

    private static void verify(String wxs) throws Exception {
        var factory = DocumentBuilderFactory.newInstance(); factory.setNamespaceAware(true);
        Document document = factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(wxs.getBytes(StandardCharsets.UTF_8)));
        require(withId(document, "CustomAction", "Id", "JasperProvisionCygwin").size() == 1
            && withId(document, "Custom", "Action", "JasperProvisionCygwin").size() == 1, "the Cygwin custom action, once");
        List<Element> components = withId(document, "Component", "Id", "JasperWebBrowser");
        require(components.size() == 1, "one web browser component: " + components.size());
        Element component = components.get(0), parent = (Element) component.getParentNode();
        require(parent.getLocalName().equals("DirectoryRef") && parent.getAttribute("Id").equals("INSTALLDIR"), "the component in INSTALLDIR");
        // Like packageInstaller's upgradeUuid, the GUID stays the same in every release.
        require(component.getAttribute("Guid").equals("{6F685105-E30D-4CAF-9091-ED60DF8D5D62}"), "the component's GUID: " + component.getAttribute("Guid"));
        // Every installed component is removed on uninstall, unless it is permanent or never installed.
        require(!component.hasAttribute("Permanent") && !component.hasAttribute("NeverOverwrite")
            && elements(component, "Condition").isEmpty(), "a component installed every time and removed on uninstall");
        List<Element> features = elements(document.getDocumentElement(), "Feature");
        require(features.size() == 1 && withId(features.get(0), "ComponentRef", "Id", "JasperWebBrowser").size() == 1,
            "the component in the product's only feature");
        List<String> registered = new ArrayList<>(), keyPaths = new ArrayList<>();
        for (Element value : elements(component, "RegistryValue")) {
            require(value.getAttribute("Type").equals("string") && !value.hasAttribute("Action"), "a string the uninstall removes: " + where(value));
            String row = where(value) + "|" + value.getAttribute("Name") + "|" + value.getAttribute("Value");
            registered.add(row);
            if (value.getAttribute("KeyPath").equals("yes")) keyPaths.add(row);
        }
        require(sorted(registered).equals(sorted(REGISTRATION)), "exactly the browser registration: " + registered);
        require(keyPaths.size() == 1 && keyPaths.get(0).startsWith("HKCU|"), "one per-user key path: " + keyPaths);
        Set<String> removed = new HashSet<>();
        for (Element key : elements(component, "RegistryKey")) {
            require(!key.hasAttribute("Action") && !key.hasAttribute("ForceCreateOnInstall"), "no key kept or created beyond its values: " + where(key));
            if (key.getAttribute("ForceDeleteOnUninstall").equals("yes")) removed.add(where(key));
        }
        require(removed.equals(REMOVED_KEYS), "the uninstall deletes exactly Jasper's own keys: " + removed);
    }

    /** Root|key of a registry element: the nearest Root, and the Key attributes of it and its RegistryKey parents. */
    private static String where(Element element) {
        String root = "", key = "";
        for (Node node = element; node instanceof Element e && !e.getLocalName().equals("Component"); node = node.getParentNode()) {
            if (root.isEmpty()) root = e.getAttribute("Root");
            if (e.hasAttribute("Key")) key = key.isEmpty() ? e.getAttribute("Key") : e.getAttribute("Key") + "\\" + key;
        }
        return root + "|" + key;
    }
    private static List<Element> elements(Element parent, String name) {
        var found = new ArrayList<Element>();
        var nodes = parent.getElementsByTagNameNS("*", name);
        for (int i = 0; i < nodes.getLength(); i++) found.add((Element) nodes.item(i));
        return found;
    }
    private static List<Element> withId(Document document, String name, String attribute, String id) {
        return withId(document.getDocumentElement(), name, attribute, id);
    }
    private static List<Element> withId(Element parent, String name, String attribute, String id) {
        return elements(parent, name).stream().filter(e -> e.getAttribute(attribute).equals(id)).toList();
    }
    private static List<String> sorted(List<String> rows) { return rows.stream().sorted().toList(); }
    private static void rejects(Path root, String template, String what) throws Exception {
        try { transform(root, template); throw new AssertionError("Accepted " + what); }
        catch (IllegalArgumentException expected) {
            require(expected.getMessage().equals("Unsupported JBR main.wxs; review the selected JBR template"), "the template message: " + expected.getMessage());
        }
    }
    private static void require(boolean ok, String what) { if (!ok) throw new AssertionError(what); checks++; }
}
