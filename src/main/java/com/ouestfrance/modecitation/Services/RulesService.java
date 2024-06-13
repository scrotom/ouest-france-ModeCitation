/*
 * Nom         : RulesService.java
 *
 * Description : Cette classe lit les règles d'un fichier JSON et les applique à un document XML.
 *
 * Date        : 07/06/2024
 *
 */

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
import java.util.Iterator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Log4j2
public class RulesService {

    // Lit les règles à partir d'un fichier JSON
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

    // Applique les règles au document XML
    public void applyRules(Document document, JsonNode allRulesNode) throws CustomAppException {
        try {
            log.info("Début de l'application des règles sur le document XML");
            replaceFormattingWithQuote(document);
            applyQuoteModeRules(document, allRulesNode);
            Document reloadedDocument = reloadDocument(document);
            if (reloadedDocument != null) {
                applyQuoteModeRules(reloadedDocument, allRulesNode);
            } else {
                log.error("Le document rechargé est null");
                throw new CustomAppException("Le document rechargé est null");
            }
            log.info("Fin de l'application des règles sur le document XML");
        } catch (Exception e) {
            log.error("Erreur lors de l'application des règles au document", e);
            throw new CustomAppException("Erreur lors de l'application des règles au document", e);
        }
    }


    // Recharge le document XML en créant une nouvelle instance de celui-ci
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

    // Applique les règles de mode citation au document XML
    public void applyQuoteModeRules(Document document, JsonNode allRulesNode) throws CustomAppException {
        try {
            log.info("Application des règles de mode citation");
            Iterator<JsonNode> rulesIterator = allRulesNode.elements();
            while (rulesIterator.hasNext()) {
                JsonNode ruleNode = rulesIterator.next();
                JsonNode xpathNode = ruleNode.get("xpath");
                if (xpathNode != null) {
                    log.info("Application de la règle avec XPath : {}", xpathNode.asText());
                    applyOneRule(document, xpathNode.asText());
                } else {
                    log.warn("Règle sans XPath : {}", ruleNode);
                }
            }
            log.info("Toutes les règles ont été appliquées");
        } catch (Exception e) {
            log.error("Erreur lors de l'application des règles au document", e);
            throw new CustomAppException("Erreur lors de l'application des règles au document", e);
        }
    }

    // Applique une règle spécifique au document XML en utilisant une expression XPath
    public void applyOneRule(Document document, String xpath) throws CustomAppException {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList nodes = (NodeList) xPath.evaluate(xpath, document, XPathConstants.NODESET);
            for (int i = 0; i < nodes.getLength(); i++) {
                Node node = nodes.item(i);
                deepCheck(node, document);
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'application de XPath: " + xpath, e);
            throw new CustomAppException("Erreur lors de l'application de XPath: " + xpath, e);
        }
    }

    // Effectue une vérification approfondie sur un nœud pour gérer les citations imbriquées
    public void deepCheck(Node node, Document document) throws CustomAppException {
        try {
            if (node.getNodeType() == Node.TEXT_NODE) {
                String textContent = node.getTextContent();
                if (containsNestedQuotes(textContent)) {
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

    // Vérifie si le texte contient des citations imbriquées
    public boolean containsNestedQuotes(String text) {
        Pattern nestedQuotePattern = Pattern.compile("«[^«]*«.*»[^«]*?»");
        Matcher matcher = nestedQuotePattern.matcher(text);
        return matcher.find();
    }

    // Vérifie si le texte contient plusieurs citations dans la même balise <b>
    public boolean containsMultipleQuotesInSameB(String text) {
        Pattern multipleQuotesPattern = Pattern.compile("«[^«»]*?»[^«»]*«[^«»]*?»");
        Matcher matcher = multipleQuotesPattern.matcher(text);
        return matcher.find();
    }

    // Applique des balises <q> autour des contenus entourés de guillemets
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

    // Remplace les balises de formatage par des balises de citation <q>
    public void replaceFormattingWithQuote(Document document) throws CustomAppException {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList pNodes = (NodeList) xPath.evaluate("//p", document, XPathConstants.NODESET);

            for (int i = 0; i < pNodes.getLength(); i++) {
                Node pNode = pNodes.item(i);
                String pContent = getTextContentWithTags(pNode);

                if (containsNestedQuotes(pContent)) {
                    continue;
                }

                Pattern pattern = Pattern.compile("«([^«]*)<(b|i|u)>([^«]*)</\\2>([^«]*)»");
                Matcher matcher = pattern.matcher(pContent);

                if (matcher.find()) {
                    String beforeQuote = pContent.substring(0, matcher.start());
                    String afterQuote = pContent.substring(matcher.end());
                    String quoteContent = matcher.group(1) + matcher.group(3) + matcher.group(4);

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
                    log.info("Balises de formatage remplacées par <q> dans le texte : {}", pContent);
                } else {
                    processFormattingTagsOutsideQuotes(pNode);
                }
            }
        } catch (Exception e) {
            throw new CustomAppException("Erreur lors du remplacement des balises de formatage par <q>", e);
        }
    }

    // Traite les balises de formatage en dehors des citations
    public void processFormattingTagsOutsideQuotes(Node pNode) throws CustomAppException {
        try {
            NodeList formattingNodes = pNode.getChildNodes();
            for (int i = 0; i < formattingNodes.getLength(); i++) {
                Node formattingNode = formattingNodes.item(i);
                if (formattingNode.getNodeName().matches("b|i|u")) {
                    String formattingTextContent = formattingNode.getTextContent().trim();

                    if (formattingTextContent.startsWith("«") && formattingTextContent.endsWith("»") && !containsMultipleQuotesInSameB(formattingTextContent)) {
                        Element qElement = pNode.getOwnerDocument().createElement("q");
                        qElement.setAttribute("class", "containsQuotes");

                        while (formattingNode.hasChildNodes()) {
                            qElement.appendChild(formattingNode.getFirstChild());
                        }

                        formattingNode.getParentNode().replaceChild(qElement, formattingNode);
                    }
                }
            }
        } catch (Exception e) {
            throw new CustomAppException("Erreur lors du traitement des balises de formatage en dehors des citations", e);
        }
    }

    // Extrait le contenu textuel avec les balises incluses
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
