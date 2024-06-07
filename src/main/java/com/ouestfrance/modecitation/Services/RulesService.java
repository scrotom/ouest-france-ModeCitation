package com.ouestfrance.modecitation.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouestfrance.modecitation.Exception.CustomAppException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Log4j2
public class RulesService {

    public JsonNode readRules(String rulesJsonPath) throws CustomAppException {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rulesNode = objectMapper.readTree(new File(rulesJsonPath));
            JsonNode allRulesNode = rulesNode.get("all");

            if (allRulesNode == null || !allRulesNode.isArray()) {
                log.error("Invalid rules JSON format: 'all' key not found or is not an array");
                throw new CustomAppException("Invalid rules JSON format: 'all' key not found or is not an array");
            }

            return allRulesNode;
        } catch (IOException e) {
            log.error("Error reading rules JSON file", e);
            throw new CustomAppException("Error reading rules JSON file", e);
        }
    }

    public void applyRules(Document document, JsonNode allRulesNode) throws CustomAppException {
        try {
            Iterator<JsonNode> rulesIterator = allRulesNode.elements();
            while (rulesIterator.hasNext()) {
                JsonNode ruleNode = rulesIterator.next();
                JsonNode xpathNode = ruleNode.get("xpath");
                if (xpathNode != null) {
                    log.info("Applying rule with xpath: {}", xpathNode.asText());
                    applyRule(document, xpathNode.asText());
                } else {
                    log.warn("Rule without xpath: {}", ruleNode);
                }
            }
            replaceBoldWithQuote(document);
        } catch (Exception e) {
            log.error("Error applying rules to document", e);
            throw new CustomAppException("Error applying rules to document", e);
        }
    }

    public void applyRule(Document document, String xpath) throws CustomAppException {
        try {
            log.info("Applying xpath: {}", xpath);
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xPath.evaluate(xpath, document, XPathConstants.NODESET);
            log.info("Number of nodes found for XPath {}: {}", xpath, nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                log.info("Node content: {}", node.getTextContent());
                deepCheck(node, document);
            }
        } catch (Exception e) {
            log.error("Error applying XPath: {}", xpath, e);
            throw new CustomAppException("Error applying XPath: " + xpath, e);
        }
    }

    public void deepCheck(Node node, Document document) throws CustomAppException {
        try {
            if (node.getNodeType() == Node.TEXT_NODE) {
                applySurroundedContents(node, document);
            } else if (node.getNodeType() == Node.ELEMENT_NODE || node.getNodeType() == Node.DOCUMENT_NODE) {
                NodeList childNodes = node.getChildNodes();
                for (int i = 0; i < childNodes.getLength(); i++) {
                    deepCheck(childNodes.item(i), document);
                }
            }
        } catch (Exception e) {
            log.error("Error during deep check", e);
            throw new CustomAppException("Error during deep check", e);
        }
    }

    public void applySurroundedContents(Node node, Document document) throws CustomAppException {
        try {
            String textContent = node.getTextContent();
            String regex = "«[^«]*?»";
            Matcher matcher = Pattern.compile(regex).matcher(textContent);
            while (matcher.find()) {
                String match = matcher.group();
                log.info("Matched quote: {}", match);
                int start = matcher.start();
                int end = matcher.end();

                Node parentNode = node.getParentNode();

                // Check if the parentNode is null before accessing it
                if (parentNode == null || "q".equals(parentNode.getNodeName())) {
                    return;
                }

                DocumentFragment fragment = document.createDocumentFragment();
                String before = textContent.substring(0, start).trim();
                String inside = textContent.substring(start, end);
                String after = textContent.substring(end).trim();

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
                log.info("Applied q tag around text: {}", match);
            }
        } catch (Exception e) {
            log.error("Error applying surrounded contents", e);
            throw new CustomAppException("Error applying surrounded contents", e);
        }
    }

    public void replaceBoldWithQuote(Document document) throws CustomAppException {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList boldNodes = (NodeList) xPath.evaluate("//b", document, XPathConstants.NODESET);
            log.info("Number of <b> nodes found: {}", boldNodes.getLength());

            for (int i = 0; i < boldNodes.getLength(); i++) {
                Node boldNode = boldNodes.item(i);
                Element qElement = document.createElement("q");
                qElement.setAttribute("class", "containsQuotes");

                while (boldNode.hasChildNodes()) {
                    qElement.appendChild(boldNode.getFirstChild());
                }

                boldNode.getParentNode().replaceChild(qElement, boldNode);
                log.info("<b> tag replaced with <q class=\"containsQuotes\">");
            }
        } catch (Exception e) {
            log.error("Error replacing <b> with <q>", e);
            throw new CustomAppException("Error replacing <b> with <q>", e);
        }
    }
}