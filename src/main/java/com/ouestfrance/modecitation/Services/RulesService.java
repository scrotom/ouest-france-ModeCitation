package com.ouestfrance.modecitation.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouestfrance.modecitation.Exception.CustomAppException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Log4j2
public class RulesService {

    public JsonNode readRules(String rulesJsonPath) throws CustomAppException {
        try {
            log.info("Lecture des règles depuis le fichier JSON : {}", rulesJsonPath);
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rulesNode = objectMapper.readTree(new File(rulesJsonPath));
            JsonNode allRulesNode = rulesNode.get("all");

            if (allRulesNode == null || !allRulesNode.isArray()) {
                log.error("Format JSON des règles invalide : clé 'all' non trouvée ou n'est pas un tableau");
                throw new CustomAppException("Format JSON des règles invalide : clé 'all' non trouvée ou n'est pas un tableau");
            }

            log.info("Règles lues avec succès");
            return allRulesNode;
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier JSON des règles", e);
            throw new CustomAppException("Erreur lors de la lecture du fichier JSON des règles", e);
        }
    }

    public Document reloadDocument(Document document) throws CustomAppException {
        try {
            log.info("Rechargement du document XML");
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            DOMSource source = new DOMSource(document);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document reloadedDocument = builder.parse(new InputSource(new StringReader(writer.toString())));
            log.info("Document XML rechargé avec succès");
            return reloadedDocument;
        } catch (Exception e) {
            log.error("Erreur lors du rechargement du document XML", e);
            throw new CustomAppException("Erreur lors du rechargement du document XML", e);
        }
    }

    public void applyRules(Document document, JsonNode allRulesNode) throws CustomAppException {
        try {
            log.info("Début de l'application des règles sur le document XML");
            for (JsonNode ruleNode : allRulesNode) {
                String xpath = ruleNode.get("xpath").asText();
                log.info("Application de la règle avec XPath : {}", xpath);
                applyFormattingAndQuotesToMatchingParagraphs(document, xpath);
            }
            log.info("Fin de l'application des règles sur le document XML");
        } catch (Exception e) {
            log.error("Erreur lors de l'application des règles au document", e);
            throw new CustomAppException("Erreur lors de l'application des règles au document", e);
        }
    }

    private void applyFormattingAndQuotesToMatchingParagraphs(Document document, String xpath) throws CustomAppException {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xPath.evaluate(xpath, document, XPathConstants.NODESET);
            log.info("Nombre de noeuds trouvés avec XPath {}: {}", xpath, nodes.getLength());

            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                log.info("Traitement du noeud {}: {}", i, node.getTextContent());
                if (node.getNodeType() == Node.TEXT_NODE) {
                    processFormattingTagsOutsideQuotes(node.getParentNode());
                } else {
                    processFormattingTagsOutsideQuotes(node);
                }
                deepCheck(node, document);
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'application de XPath: " + xpath, e);
            throw new CustomAppException("Erreur lors de l'application de XPath: " + xpath, e);
        }
    }

    public void deepCheck(Node node, Document document) throws CustomAppException {
        try {
            log.info("Début de deepCheck sur le noeud : {}", node.getNodeName());

            if (node.getNodeType() == Node.TEXT_NODE) {
                String textContent = node.getTextContent();
                log.info("Contenu du noeud texte : {}", textContent);
                if (containsNestedQuotes(textContent)) {
                    log.info("Texte contient des citations imbriquées : {}", textContent);
                    return;
                }
                applySurroundedContents(node, document);
            } else if (node.getNodeType() == Node.ELEMENT_NODE || node.getNodeType() == Node.DOCUMENT_NODE) {
                NodeList childNodes = node.getChildNodes();
                for (int i = 0; i < childNodes.getLength(); i++) {
                    deepCheck(childNodes.item(i), document);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de la vérification approfondie", e);
            throw new CustomAppException("Erreur lors de la vérification approfondie", e);
        }
    }

    public boolean containsNestedQuotes(String text) {
        Pattern nestedQuotePattern = Pattern.compile("«[^«]*«.*»[^«]*?»");
        Matcher matcher = nestedQuotePattern.matcher(text);
        return matcher.find();
    }

    public boolean containsMultipleQuotesInSameB(String text) {
        Pattern multipleQuotesPattern = Pattern.compile("«[^«»]*?»[^«»]*«[^«»]*?»");
        Matcher matcher = multipleQuotesPattern.matcher(text);
        return matcher.find();
    }

    public boolean areQuotesProperlyNested(String text) {
        int quoteOpenCount = 0;
        for (char ch : text.toCharArray()) {
            if (ch == '«') {
                quoteOpenCount++;
            } else if (ch == '»') {
                if (quoteOpenCount == 0) {
                    return false; // Found closing quote without a matching opening quote
                }
                quoteOpenCount--;
            }
        }
        return quoteOpenCount == 0; // Ensure all opening quotes have matching closing quotes
    }

    public void applySurroundedContents(Node node, Document document) throws CustomAppException {
        try {
            String textContent = node.getTextContent();

            if (!areQuotesProperlyNested(textContent)) {
                log.warn("Quotes non correctement imbriquées trouvées : {}", textContent);
                return;
            }

            String regex = "«[^«]*?»";
            Matcher matcher = Pattern.compile(regex).matcher(textContent);

            int lastIndex = 0;
            Node parentNode = node.getParentNode();

            if (parentNode == null) {
                return;
            }

            DocumentFragment fragment = document.createDocumentFragment();

            while (matcher.find()) {
                String match = matcher.group();
                int start = matcher.start();
                int end = matcher.end();

                String before = textContent.substring(lastIndex, start);
                String inside = textContent.substring(start, end);
                lastIndex = end;

                if (!before.isEmpty()) {
                    fragment.appendChild(document.createTextNode(before));
                }

                Element q = document.createElement("q");
                q.setAttribute("class", "containsQuotes");
                q.appendChild(document.createTextNode(inside));
                fragment.appendChild(q);
            }

            String after = textContent.substring(lastIndex);
            if (!after.isEmpty()) {
                fragment.appendChild(document.createTextNode(after));
            }

            parentNode.replaceChild(fragment, node);
            log.info("Balise <q> appliquée autour du texte : {}", textContent);
        } catch (Exception e) {
            throw new CustomAppException("Erreur lors de l'application des contenus entourés", e);
        }
    }

    public boolean containsFormattingTags(String text) {
        Pattern formattingTagPattern = Pattern.compile("<(b|i|u)>");
        Matcher matcher = formattingTagPattern.matcher(text);
        return matcher.find();
    }

    public void processFormattingTagsOutsideQuotes(Node pNode) throws CustomAppException {
        try {
            log.info("Début de processFormattingTagsOutsideQuotes pour le noeud : {}", pNode.getTextContent());
            NodeList formattingNodes = pNode.getChildNodes();
            for (int i = 0; i < formattingNodes.getLength(); i++) {
                Node formattingNode = formattingNodes.item(i);
                log.info("Traitement du noeud enfant : {}", formattingNode.getNodeName());
                if (formattingNode.getNodeName().matches("b|i|u")) {
                    String formattingTextContent = formattingNode.getTextContent().trim();
                    log.info("Contenu du texte sous balise de formatage : {}", formattingTextContent);

                    // Check if the content has properly nested quotes
                    if (!containsNestedQuotes(formattingTextContent) && !isNestedWithinQuotes(formattingNode) && formattingTextContent.startsWith("«") && formattingTextContent.endsWith("»") && !containsMultipleQuotesInSameB(formattingTextContent)) {
                        Element qElement = pNode.getOwnerDocument().createElement("q");
                        qElement.setAttribute("class", "containsQuotes");
                        qElement.setTextContent(formattingTextContent);
                        formattingNode.getParentNode().replaceChild(qElement, formattingNode);
                        log.info("Balise <q> appliquée autour du texte : {}", formattingTextContent);
                    }
                }
            }
        } catch (Exception e) {
            throw new CustomAppException("Erreur lors du traitement des balises de formatage en dehors des citations", e);
        }
    }

    public boolean isNestedWithinQuotes(Node node) {
        Node parent = node.getParentNode();
        while (parent != null) {
            if (parent.getNodeType() == Node.TEXT_NODE) {
                String textContent = parent.getTextContent();
                if (textContent.contains("«") && textContent.contains("»")) {
                    return true;
                }
            }
            parent = parent.getParentNode();
        }
        return false;
    }

    public String getTextContentWithTags(Node node) {
        StringBuilder result = new StringBuilder();
        NodeList childNodes = node.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node child = childNodes.item(i);
            switch (child.getNodeType()) {
                case Node.TEXT_NODE:
                    result.append(child.getTextContent());
                    break;
                case Node.ELEMENT_NODE:
                    result.append("<").append(child.getNodeName()).append(">");
                    result.append(getTextContentWithTags(child));
                    result.append("</").append(child.getNodeName()).append(">");
                    break;
            }
        }
        return result.toString();
    }
}