package com.ouestfrance.modecitation.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ModeCitationService {

    private static final Logger logger = LoggerFactory.getLogger(ModeCitationService.class);

    @Value("${input.xml.path}")
    private String inputXmlPath;

    @Value("${output.xml.path}")
    private String outputXmlPath;

    @Value("${rules.json.path}")
    private String rulesJsonPath;

    public void applyQuoteMode() throws Exception {
        logger.info("Starting applyQuoteMode");

        logger.info("Reading rules from: {}", rulesJsonPath);
        // Read and parse rules
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rulesNode = objectMapper.readTree(new File(rulesJsonPath));
        JsonNode allRulesNode = rulesNode.get("all");

        if (allRulesNode == null || !allRulesNode.isArray()) {
            logger.error("Invalid rules JSON format: 'all' key not found or is not an array");
            throw new IllegalArgumentException("Invalid rules JSON format: 'all' key not found or is not an array");
        }

        logger.info("Rules JSON content: {}", allRulesNode.toString());

        logger.info("Reading XML content from: {}", inputXmlPath);
        // Read XML document
        String xmlContent = readXMLFile(inputXmlPath);
        logger.info("Input XML content: {}", xmlContent);
        Document document = loadXMLFromString(xmlContent);

        logger.info("Applying rules to the document");
        // Apply rules to document
        Iterator<JsonNode> rulesIterator = allRulesNode.elements();
        while (rulesIterator.hasNext()) {
            JsonNode ruleNode = rulesIterator.next();
            JsonNode xpathNode = ruleNode.get("xpath");
            if (xpathNode != null) {
                logger.info("Applying rule with xpath: {}", xpathNode.asText());
                applyRule(document, xpathNode.asText());
            } else {
                logger.warn("Rule without xpath: {}", ruleNode);
            }
        }

        logger.info("Saving modified document to: {}", outputXmlPath);
        // Save modified document
        saveDocumentToFile(document, outputXmlPath);
        logger.info("Finished applyQuoteMode");
    }

    private String readXMLFile(String filePath) throws IOException {
        logger.info("Reading XML file: {}", filePath);
        try (InputStream inputStream = new FileInputStream(new File(filePath));
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        }
    }

    private Document loadXMLFromString(String xml) throws ParserConfigurationException, IOException, SAXException {
        logger.info("Loading XML from string");
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setNamespaceAware(true);

        DocumentBuilder builder = factory.newDocumentBuilder();
        try (Reader reader = new StringReader(xml)) {
            InputSource is = new InputSource(reader);
            is.setEncoding("UTF-8");
            return builder.parse(is);
        }
    }

    private void applyRule(Document document, String xpath) throws Exception {
        logger.info("Applying xpath: {}", xpath);
        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodes = (NodeList) xPath.evaluate(xpath, document, XPathConstants.NODESET);
        logger.info("Number of nodes found for XPath {}: {}", xpath, nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            logger.info("Node content: {}", node.getTextContent());
            deepCheck(node, document);
        }
    }


    private void deepCheck(Node node, Document document) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            applySurroundedContents(node, document);
        } else if (node.getNodeType() == Node.ELEMENT_NODE || node.getNodeType() == Node.DOCUMENT_NODE) {
            NodeList childNodes = node.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                deepCheck(childNodes.item(i), document);
            }
        }
    }

    private void applySurroundedContents(Node node, Document document) {
        String textContent = node.getTextContent();
        String regex = "«[^«]*?»";
        Matcher matcher = Pattern.compile(regex).matcher(textContent);
        while (matcher.find()) {
            String match = matcher.group();
            logger.info("Matched quote: {}", match);
            int start = matcher.start();
            int end = matcher.end();

            Node parentNode = node.getParentNode();

            if (parentNode.getNodeName().equals("q")) {
                return;
            }

            DocumentFragment fragment = document.createDocumentFragment();
            String before = textContent.substring(0, start);
            String inside = textContent.substring(start, end);
            String after = textContent.substring(end);

            if (!before.isEmpty()) {
                fragment.appendChild(document.createTextNode(before));
            }

            Element q = document.createElement("q");
            q.setAttribute("class", "containsQuotes");
            q.appendChild(document.createTextNode(inside));
            fragment.appendChild(q);

            if (!after.isEmpty()) {
                fragment.appendChild(document.createTextNode(after));
            }

            parentNode.replaceChild(fragment, node);
            logger.info("Applied q tag around text: {}", match);
        }
    }

    private void saveDocumentToFile(Document document, String filePath) throws TransformerException {
        logger.info("Saving document to file: {}", filePath);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(new File(filePath));
        transformer.transform(source, result);
    }
}
