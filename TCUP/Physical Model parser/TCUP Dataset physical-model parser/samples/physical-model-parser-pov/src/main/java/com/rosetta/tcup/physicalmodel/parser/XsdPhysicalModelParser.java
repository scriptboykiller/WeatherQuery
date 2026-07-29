package com.rosetta.tcup.physicalmodel.parser;

import com.rosetta.tcup.physicalmodel.domain.ParseResult;
import com.rosetta.tcup.physicalmodel.domain.SourceType;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

@Component
public class XsdPhysicalModelParser implements PhysicalModelParser {
    private static final String XSD_NAMESPACE = "http://www.w3.org/2001/XMLSchema";

    @Override
    public SourceType supportedType() {
        return SourceType.XSD;
    }

    @Override
    public ParseResult parse(String content) {
        Element schema = SecureXmlDocuments.parse(content, "XSD").getDocumentElement();
        if (!"schema".equals(XmlPhysicalModelParser.localName(schema)) || !XSD_NAMESPACE.equals(schema.getNamespaceURI())) {
            throw new com.rosetta.tcup.physicalmodel.support.InvalidPhysicalModelInputException("Input is not an XML Schema document");
        }
        ParseCollector collector = new ParseCollector();
        Index index = Index.from(schema, collector);
        for (Element globalElement : index.rootElements()) {
            walkElement(globalElement, "", index, new LinkedHashSet<>(), collector);
        }
        return new ParseResult(supportedType(), collector.attributes(), collector.warnings());
    }

    private void walkElement(Element declaration, String parentPath, Index index, Set<String> activeTypes, ParseCollector collector) {
        Element resolved = declaration;
        String ref = declaration.getAttribute("ref");
        if (!ref.isBlank()) {
            resolved = index.globalElements.get(localPart(ref));
            if (resolved == null) {
                collector.warn("Unresolved same-file XSD element reference: " + ref);
                return;
            }
        }
        String name = resolved.getAttribute("name");
        if (name.isBlank()) {
            collector.warn("XSD element has no resolvable name");
            return;
        }
        String path = join(parentPath, name + (isRepeatable(declaration) ? "[]" : ""));

        Element inlineSimple = firstChild(resolved, "simpleType");
        if (inlineSimple != null) {
            collector.addPath(path);
            return;
        }
        Element inlineComplex = firstChild(resolved, "complexType");
        if (inlineComplex != null) {
            walkComplexType(inlineComplex, path, index, activeTypes, collector);
            return;
        }
        String type = resolved.getAttribute("type");
        if (type.isBlank()) {
            collector.warn("Unsupported XSD element without a type or inline type: " + name);
            return;
        }
        String typeName = localPart(type);
        if (isBuiltInType(type) || index.simpleTypes.containsKey(typeName)) {
            collector.addPath(path);
            return;
        }
        Element complexType = index.complexTypes.get(typeName);
        if (complexType == null) {
            collector.warn("Unresolved same-file XSD type: " + type);
            return;
        }
        if (!activeTypes.add(typeName)) {
            collector.warn("Recursive XSD type stopped: " + typeName);
            return;
        }
        walkComplexType(complexType, path, index, activeTypes, collector);
        activeTypes.remove(typeName);
    }

    private void walkComplexType(Element complexType, String path, Index index, Set<String> activeTypes, ParseCollector collector) {
        Element choice = firstDescendant(complexType, "choice");
        if (choice != null) {
            collector.warn("XSD choice is unsupported in Phase 1");
            return;
        }
        boolean hasSupportedContent = false;
        for (Element child : childElements(complexType, null)) {
            switch (XmlPhysicalModelParser.localName(child)) {
                case "sequence", "all" -> {
                    hasSupportedContent = true;
                    for (Element element : childElements(child, "element")) {
                        walkElement(element, path, index, activeTypes, collector);
                    }
                }
                case "attribute" -> {
                    hasSupportedContent = true;
                    emitAttribute(child, path, collector);
                }
                case "simpleContent" -> {
                    hasSupportedContent = true;
                    walkSimpleContent(child, path, collector);
                }
                default -> {
                    // Annotations and other metadata do not define a Phase 1 path.
                }
            }
        }
        if (!hasSupportedContent) {
            collector.warn("Unsupported XSD complex type content at path: " + path);
        }
    }

    private void walkSimpleContent(Element simpleContent, String path, ParseCollector collector) {
        Element extensionOrRestriction = firstChild(simpleContent, "extension");
        if (extensionOrRestriction == null) {
            extensionOrRestriction = firstChild(simpleContent, "restriction");
        }
        if (extensionOrRestriction == null || extensionOrRestriction.getAttribute("base").isBlank()) {
            collector.warn("Unsupported XSD simpleContent at path: " + path);
            return;
        }
        // The element has a simple text value, irrespective of its declared simple base type.
        collector.addPath(path);
        for (Element child : childElements(extensionOrRestriction, "attribute")) {
            emitAttribute(child, path, collector);
        }
    }

    private void emitAttribute(Element declaration, String path, ParseCollector collector) {
        String name = declaration.getAttribute("name");
        if (name.isBlank()) {
            String ref = declaration.getAttribute("ref");
            collector.warn(ref.isBlank()
                    ? "XSD attribute has no resolvable name at path: " + path
                    : "Same-file XSD attribute references are unsupported in Phase 1: " + ref);
            return;
        }
        collector.addPath(path + ".@" + name);
    }

    private boolean isRepeatable(Element element) {
        String value = element.getAttribute("maxOccurs");
        if (value.isBlank() || "1".equals(value)) {
            return false;
        }
        if ("unbounded".equals(value)) {
            return true;
        }
        try {
            return new BigInteger(value).compareTo(BigInteger.ONE) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }

    private boolean isBuiltInType(String qualifiedType) {
        return qualifiedType.startsWith("xs:") || qualifiedType.startsWith("xsd:")
                || XSD_NAMESPACE.equals(qualifiedType);
    }

    private String join(String parent, String name) {
        return parent.isEmpty() ? name : parent + "." + name;
    }

    private static String localPart(String qualifiedName) {
        int separator = qualifiedName.indexOf(':');
        return separator < 0 ? qualifiedName : qualifiedName.substring(separator + 1);
    }

    private static Element firstChild(Element parent, String name) {
        for (Element child : childElements(parent, null)) {
            if (name.equals(XmlPhysicalModelParser.localName(child))) {
                return child;
            }
        }
        return null;
    }

    private static Element firstDescendant(Element parent, String name) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (name.equals(XmlPhysicalModelParser.localName(element))) {
                    return element;
                }
                Element nested = firstDescendant(element, name);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static List<Element> childElements(Element parent, String expectedName) {
        List<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element element = (Element) node;
                if (expectedName == null || expectedName.equals(XmlPhysicalModelParser.localName(element))) {
                    elements.add(element);
                }
            }
        }
        return elements;
    }

    private static final class Index {
        private final Map<String, Element> globalElements = new LinkedHashMap<>();
        private final Map<String, Element> complexTypes = new LinkedHashMap<>();
        private final Map<String, Element> simpleTypes = new LinkedHashMap<>();

        static Index from(Element schema, ParseCollector collector) {
            Index index = new Index();
            for (Element child : childElements(schema, null)) {
                String name = child.getAttribute("name");
                switch (XmlPhysicalModelParser.localName(child)) {
                    case "element" -> {
                        if (!name.isBlank()) {
                            index.globalElements.put(name, child);
                        }
                    }
                    case "complexType" -> {
                        if (!name.isBlank()) {
                            index.complexTypes.put(name, child);
                        }
                    }
                    case "simpleType" -> {
                        if (!name.isBlank()) {
                            index.simpleTypes.put(name, child);
                        }
                    }
                    case "include", "import" -> collector.warn("External XSD include/import is unsupported in Phase 1");
                    default -> {
                        // Other schema metadata is not a business element.
                    }
                }
            }
            return index;
        }

        List<Element> rootElements() {
            Set<String> referencedElementNames = new LinkedHashSet<>();
            for (Element globalElement : globalElements.values()) {
                collectReferencedElements(globalElement, referencedElementNames);
            }
            return globalElements.entrySet().stream()
                    .filter(entry -> !referencedElementNames.contains(entry.getKey()))
                    .map(Map.Entry::getValue)
                    .toList();
        }

        private static void collectReferencedElements(Element parent, Set<String> references) {
            for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element child = (Element) node;
                if ("element".equals(XmlPhysicalModelParser.localName(child)) && child.hasAttribute("ref")) {
                    references.add(localPart(child.getAttribute("ref")));
                }
                collectReferencedElements(child, references);
            }
        }
    }
}
