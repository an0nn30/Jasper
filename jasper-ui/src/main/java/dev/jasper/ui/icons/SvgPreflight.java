package dev.jasper.ui.icons;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;

/**
 * The receiving rules for SVG artwork, applied to the XML alone, without decoding, drawing or AWT: an {@code svg} root,
 * no doctype or external entities, no processing instruction, no script, foreign object or animation, no event or
 * {@code base} attribute, fragment-only {@code href} and {@code url(…)} references, and no escaped or {@code @} CSS.
 */
final class SvgPreflight {
    private static final Pattern URL = Pattern.compile("url\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);

    private SvgPreflight() { }

    /** Throws when {@code data} breaks a rule; the message names it. */
    static void check(byte[] data) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
        });
        var document = builder.parse(new ByteArrayInputStream(data));
        if (!"svg".equals(document.getDocumentElement().getLocalName())) throw new IOException("Not SVG");
        inspect(document, 0);
    }

    private static void inspect(Node node, int depth) throws IOException {
        if (depth > 128) throw new IOException("Artwork nesting exceeds limit");
        if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) throw new IOException("Processing instruction denied");
        String name = node.getLocalName();
        if (name != null && java.util.Set.of("script", "foreignobject", "animate", "animatetransform", "set").contains(name.toLowerCase(Locale.ROOT)))
            throw new IOException("Active SVG denied");
        var attributes = node.getAttributes();
        if (attributes != null) for (int i = 0; i < attributes.getLength(); i++) {
            var attribute = attributes.item(i);
            String key = attribute.getLocalName();
            String value = attribute.getNodeValue().trim();
            if (key != null && (key.toLowerCase(Locale.ROOT).startsWith("on") || key.equals("base")))
                throw new IOException("Active SVG attribute denied");
            if ("href".equals(key) && !value.matches("#[A-Za-z_][A-Za-z0-9_.:-]*")) throw new IOException("Nonlocal resource denied");
            css(value);
        }
        if ("style".equals(name)) css(node.getTextContent());
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) inspect(child, depth + 1);
    }

    private static void css(String value) throws IOException {
        if (value.indexOf('\\') >= 0 || value.indexOf('@') >= 0) throw new IOException("External or escaped CSS denied");
        var urls = URL.matcher(value);
        while (urls.find())
            if (!urls.group(1).trim().replace("\"", "").replace("'", "").matches("#[A-Za-z_][A-Za-z0-9_.:-]*"))
                throw new IOException("Nonlocal paint resource denied");
    }
}
