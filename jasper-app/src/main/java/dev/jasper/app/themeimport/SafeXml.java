package dev.jasper.app.themeimport;

import dev.jasper.app.appearance.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/** XML from theme plugins, which Jasper does not trust: no DOCTYPE, no external entities or schemas, no XInclude. */
final class SafeXml {
    private SafeXml() {}

    /** The document element; an {@link IllegalArgumentException} naming the problem when {@code xml} is not a readable document. */
    static Element parse(byte[] xml) {
        try {
            var factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);
            var builder = factory.newDocumentBuilder();
            // The default handler prints parse errors to standard error; the exception already carries them.
            builder.setErrorHandler(new DefaultHandler());
            return builder.parse(new ByteArrayInputStream(xml)).getDocumentElement();
        } catch (ParserConfigurationException | SAXException | IOException failure) {
            throw new IllegalArgumentException("not a readable XML document (" + failure.getMessage() + ")", failure);
        }
    }

    /** Every element child of {@code parent}, in document order. */
    static List<Element> children(Element parent) {
        var result = new ArrayList<Element>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling())
            if (node instanceof Element element) result.add(element);
        return result;
    }

    /** The element children of {@code parent} named {@code tag}, in document order. */
    static List<Element> children(Element parent, String tag) {
        return children(parent).stream().filter(element -> element.getTagName().equals(tag)).toList();
    }
}
