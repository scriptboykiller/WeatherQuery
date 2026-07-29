package com.rosetta.tcup.physicalmodel.parser;

import com.rosetta.tcup.physicalmodel.domain.ParseResult;
import com.rosetta.tcup.physicalmodel.domain.SourceType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

@Component
public class XmlPhysicalModelParser implements PhysicalModelParser {
    @Override
    public SourceType supportedType() {
        return SourceType.XML;
    }

    @Override
    public ParseResult parse(String content) {
        ParseCollector collector = new ParseCollector();
        walk(SecureXmlDocuments.parse(content, "XML").getDocumentElement(), "", collector);
        return new ParseResult(supportedType(), collector.attributes(), collector.warnings());
    }

    private void walk(Element element, String parentPath, ParseCollector collector) {
        String path = join(parentPath, localName(element));
        emitAttributes(element, path, collector);

        List<Element> children = childElements(element);
        if (children.isEmpty()) {
            collector.addPath(path);
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Element child : children) {
            counts.merge(localName(child), 1, Integer::sum);
        }
        for (Element child : children) {
            String childPath = path + "." + localName(child);
            if (counts.get(localName(child)) > 1) {
                childPath += "[]";
            }
            walkChild(child, childPath, collector);
        }
    }

    private void walkChild(Element element, String path, ParseCollector collector) {
        emitAttributes(element, path, collector);
        List<Element> children = childElements(element);
        if (children.isEmpty()) {
            collector.addPath(path);
            return;
        }
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Element child : children) {
            counts.merge(localName(child), 1, Integer::sum);
        }
        for (Element child : children) {
            String childPath = path + "." + localName(child);
            if (counts.get(localName(child)) > 1) {
                childPath += "[]";
            }
            walkChild(child, childPath, collector);
        }
    }

    private void emitAttributes(Element element, String path, ParseCollector collector) {
        NamedNodeMap attributes = element.getAttributes();
        for (int index = 0; index < attributes.getLength(); index++) {
            Node attribute = attributes.item(index);
            if (!XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(attribute.getNamespaceURI())) {
                collector.addPath(path + ".@" + localName(attribute));
            }
        }
    }

    private List<Element> childElements(Element element) {
        List<Element> children = new ArrayList<>();
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                children.add((Element) node);
            }
        }
        return children;
    }

    private String join(String parent, String name) {
        return parent.isEmpty() ? name : parent + "." + name;
    }

    static String localName(Node node) {
        return node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
    }
}
