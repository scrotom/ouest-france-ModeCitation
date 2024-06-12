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
import javax.xml.transform.OutputKeys;
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
                log.error("Format JSON des règles invalide : clé 'all' non trouvée ou n'est pas un tableau");
                throw new CustomAppException("Format JSON des règles invalide : clé 'all' non trouvée ou n'est pas un tableau");
            }

            return allRulesNode;
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier JSON des règles", e);
            throw new CustomAppException("Erreur lors de la lecture du fichier JSON des règles", e);
        }
    }

    public void applyRules(Document document, JsonNode allRulesNode) throws CustomAppException {
        try {
            logDocumentState("Contenu initial du document XML", document);

            replaceBoldWithQuote(document);
            logDocumentState("Contenu du document XML après replaceBoldWithQuote", document);

            applyQuoteModeRules(document, allRulesNode);
            logDocumentState("Contenu du document XML après applyQuoteModeRules", document);

            document = reloadDocument(document);
            logDocumentState("Contenu du document XML après rechargement", document);

            applyQuoteModeRules(document, allRulesNode);
            logDocumentState("Contenu final du document XML après deuxième applyQuoteModeRules", document);
        } catch (Exception e) {
            log.error("Erreur lors de l'application des règles au document", e);
            throw new CustomAppException("Erreur lors de l'application des règles au document", e);
        }
    }

    public void logDocumentState(String message, Document document) {
        try {
            String xmlString = documentToString(document);
            log.info("{}:\n{}", message, xmlString);
        } catch (CustomAppException e) {
            log.error("Erreur lors de la capture de l'état du document", e);
        }
    }

    public Document reloadDocument(Document document) throws CustomAppException {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            DOMSource source = new DOMSource(document);
            StringWriter writer = new StringWriter();
            StreamResult result = new StreamResult(writer);
            transformer.transform(source, result);

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(writer.toString())));
        } catch (Exception e) {
            log.error("Erreur lors du rechargement du document XML", e);
            throw new CustomAppException("Erreur lors du rechargement du document XML", e);
        }
    }

    public void applyQuoteModeRules(Document document, JsonNode allRulesNode) throws CustomAppException {
        try {
            Iterator<JsonNode> rulesIterator = allRulesNode.elements();
            while (rulesIterator.hasNext()) {
                JsonNode ruleNode = rulesIterator.next();
                JsonNode xpathNode = ruleNode.get("xpath");
                if (xpathNode != null) {
                    log.info("Application de la règle avec xpath: {}", xpathNode.asText());
                    applyOneRule(document, xpathNode.asText());
                } else {
                    log.warn("Règle sans xpath: {}", ruleNode);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'application des règles au document", e);
            throw new CustomAppException("Erreur lors de l'application des règles au document", e);
        }
    }

    public void applyOneRule(Document document, String xpath) throws CustomAppException {
        try {
            log.info("Application de xpath: {}", xpath);
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xPath.evaluate(xpath, document, XPathConstants.NODESET);
            log.info("Nombre de nœuds trouvés pour XPath {}: {}", xpath, nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                log.info("Contenu du nœud: {}", node.getTextContent());
                deepCheck(node, document);
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'application de XPath: {}", xpath, e);
            throw new CustomAppException("Erreur lors de l'application de XPath: " + xpath, e);
        }
    }

    public void deepCheck(Node node, Document document) throws CustomAppException {
        try {
            log.info("Début de deepCheck pour le nœud : {}", node.getNodeName());
            if (node.getNodeType() == Node.TEXT_NODE) {
                String textContent = node.getTextContent();
                if (containsNestedQuotes(textContent)) {
                    log.info("Citation imbriquée détectée, aucun traitement appliqué.");
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

    private boolean containsNestedQuotes(String text) {
        Pattern nestedQuotePattern = Pattern.compile("«[^«]*«.*»[^«]*?»");
        Matcher matcher = nestedQuotePattern.matcher(text);
        return matcher.find();
    }

    private boolean containsMultipleQuotesInSameB(String text) {
        Pattern multipleQuotesPattern = Pattern.compile("«[^«»]*?»[^«»]*«[^«»]*?»");
        Matcher matcher = multipleQuotesPattern.matcher(text);
        return matcher.find();
    }

    public void applySurroundedContents(Node node, Document document) throws CustomAppException {
        try {
            String textContent = node.getTextContent();
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
                log.info("Citation trouvée: {}", match);
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
            log.info("Balise <q> appliquée autour du texte: {}", textContent);

            log.info("Contenu actuel du document XML :\n{}", documentToString(document));
        } catch (Exception e) {
            log.error("Erreur lors de l'application des contenus entourés", e);
            throw new CustomAppException("Erreur lors de l'application des contenus entourés", e);
        }
    }

    public void replaceBoldWithQuote(Document document) throws CustomAppException {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList pNodes = (NodeList) xPath.evaluate("//p", document, XPathConstants.NODESET);
            log.info("Nombre de nœuds <p> trouvés: {}", pNodes.getLength());

            for (int i = 0; i < pNodes.getLength(); i++) {
                Node pNode = pNodes.item(i);
                String pContent = getTextContentWithTags(pNode);
                log.info("Traitement du nœud <p> avec contenu: {}", pContent);

                if (containsNestedQuotes(pContent)) {
                    log.info("Citation imbriquée détectée dans le nœud <p>, aucun traitement appliqué.");
                    continue;
                }

                Pattern pattern = Pattern.compile("«([^«]*)<b>([^«]*)</b>([^«]*)»");
                Matcher matcher = pattern.matcher(pContent);

                if (matcher.find()) {
                    String beforeQuote = pContent.substring(0, matcher.start());
                    String afterQuote = pContent.substring(matcher.end());
                    String quoteContent = matcher.group(1) + matcher.group(2) + matcher.group(3);

                    Element qElement = document.createElement("q");
                    qElement.setAttribute("class", "containsQuotes");
                    qElement.setTextContent("«" + quoteContent + "»");

                    DocumentFragment fragment = document.createDocumentFragment();
                    if (!beforeQuote.isEmpty()) {
                        fragment.appendChild(document.createTextNode(beforeQuote));
                    }
                    fragment.appendChild(qElement);
                    if (!afterQuote.isEmpty()) {
                        fragment.appendChild(document.createTextNode(afterQuote));
                    }

                    pNode.setTextContent("");
                    pNode.appendChild(fragment);

                    log.info("Balise <b> supprimée et <q> ajoutée dans le texte: {}", getTextContentWithTags(pNode));
                } else {
                    processBoldTagsOutsideQuotes(pNode);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors du remplacement de <b> par <q>", e);
            throw new CustomAppException("Erreur lors du remplacement de <b> par <q>", e);
        }
    }

    private void processBoldTagsOutsideQuotes(Node pNode) throws CustomAppException {
        try {
            NodeList boldNodes = pNode.getChildNodes();
            for (int i = 0; i < boldNodes.getLength(); i++) {
                Node boldNode = boldNodes.item(i);
                if (boldNode.getNodeName().equals("b")) {
                    String boldTextContent = boldNode.getTextContent().trim();

                    if (boldTextContent.startsWith("«") && boldTextContent.endsWith("»") && !containsMultipleQuotesInSameB(boldTextContent)) {
                        Element qElement = pNode.getOwnerDocument().createElement("q");
                        qElement.setAttribute("class", "containsQuotes");

                        while (boldNode.hasChildNodes()) {
                            qElement.appendChild(boldNode.getFirstChild());
                        }

                        boldNode.getParentNode().replaceChild(qElement, boldNode);
                        log.info("Balise <b> remplacée par <q class=\"containsQuotes\"> avec le texte: {}", boldTextContent);
                    } else {
                        log.info("Balise <b> conservée autour du texte: {}", boldTextContent);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement des balises <b> en dehors des citations", e);
            throw new CustomAppException("Erreur lors du traitement des balises <b> en dehors des citations", e);
        }
    }

    private String getTextContentWithTags(Node node) {
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

    private String documentToString(Document document) throws CustomAppException {
        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.toString();
        } catch (Exception e) {
            log.error("Erreur lors de la conversion du document en chaîne", e);
            throw new CustomAppException("Erreur lors de la conversion du document en chaîne", e);
        }
    }
}
