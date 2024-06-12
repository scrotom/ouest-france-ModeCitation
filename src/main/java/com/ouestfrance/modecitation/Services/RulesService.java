/*
 * Nom         : RulesService.java
 *
 * Description : Classe permettant de lire et appliquer des règles JSON sur un document XML.
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
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.DocumentFragment;
import org.w3c.dom.Element;
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

    //Lit les règles à partir d'un fichier JSON
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



    // Recharger le document pour s'assurer que les modifications sont prises en compte
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

    // Appliquer les règles de citation initiales
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

    //Applique une règle spécifique au document XML
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

    // Effectue une vérification approfondie du nœud et de ses enfants (si + de 1 paire de guillemets dans le même texte par exemple)
    public void deepCheck(Node node, Document document) throws CustomAppException {
        try {
            log.info("Début de deepCheck pour le nœud : {}", node.getNodeName());
            if (node.getNodeType() == Node.TEXT_NODE) {
                String textContent = node.getTextContent();
                // Vérifie si le texte contient une citation imbriquée
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

    // Vérifie si une chaîne de texte contient des citations imbriquées
    private boolean containsNestedQuotes(String text) {
        Pattern nestedQuotePattern = Pattern.compile("«[^«]*«.*»[^«]*?»");
        Matcher matcher = nestedQuotePattern.matcher(text);
        return matcher.find();
    }

    // Applique des balises <q> autour du contenu textuel entouré de guillemets français
    public void applySurroundedContents(Node node, Document document) throws CustomAppException {
        try {
            String textContent = node.getTextContent();
            String regex = "«[^«]*?»";
            Matcher matcher = Pattern.compile(regex).matcher(textContent);

            int lastIndex = 0;
            Node parentNode = node.getParentNode();

            // Vérifie si le parentNode est null avant de l'utiliser
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
                String inside = textContent.substring(start, end); // Inclut les guillemets
                lastIndex = end;

                if (!before.isEmpty()) {
                    fragment.appendChild(document.createTextNode(before));
                }

                Element q = document.createElement("q");
                q.setAttribute("class", "containsQuotes");
                log.info("creation de l'element q");
                q.appendChild(document.createTextNode(inside));
                log.info("creation du text node");
                fragment.appendChild(q);
                log.info("append du fragment");
            }

            String after = textContent.substring(lastIndex);
            if (!after.isEmpty()) {
                fragment.appendChild(document.createTextNode(after));
            }

            parentNode.replaceChild(fragment, node);
            log.info("Balise <q> appliquée autour du texte: {}", textContent);

            // Afficher l'entièreté du document XML après modification
            log.info("Contenu actuel du document XML :\n{}", documentToString(document));
        } catch (Exception e) {
            log.error("Erreur lors de l'application des contenus entourés", e);
            throw new CustomAppException("Erreur lors de l'application des contenus entourés", e);
        }
    }

    // Remplacement des balises <b> par <q> ou suppression des balises <b> selon le cas
    public void replaceBoldWithQuote(Document document) throws CustomAppException {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList boldNodes = (NodeList) xPath.evaluate("//b", document, XPathConstants.NODESET);
            log.info("Nombre de nœuds <b> trouvés: {}", boldNodes.getLength());

            for (int i = 0; i < boldNodes.getLength(); i++) {
                Node boldNode = boldNodes.item(i);
                String boldTextContent = boldNode.getTextContent().trim();

                // Vérifie si le texte commence et se termine par les guillemets français
                if (boldTextContent.startsWith("«") && boldTextContent.endsWith("»")) {
                    Element qElement = document.createElement("q");
                    qElement.setAttribute("class", "containsQuotes");

                    while (boldNode.hasChildNodes()) {
                        qElement.appendChild(boldNode.getFirstChild());
                    }

                    boldNode.getParentNode().replaceChild(qElement, boldNode);
                    log.info("Balise <b> remplacée par <q class=\"containsQuotes\"> avec le texte: {}", boldTextContent);
                } else if (boldTextContent.contains("«") && boldTextContent.contains("»")) {
                    // Si le texte contient des guillemets français, remplace tout par une seule balise <q>
                    Element qElement = document.createElement("q");
                    qElement.setAttribute("class", "containsQuotes");
                    qElement.setTextContent(boldTextContent);
                    boldNode.getParentNode().replaceChild(qElement, boldNode);
                    log.info("Balise <b> contenant des guillemets remplacée par <q>: {}", boldTextContent);
                } else {
                    // Si le texte ne contient pas de guillemets français, on ne fait rien
                    log.info("Balise <b> conservée autour du texte: {}", boldTextContent);
                }
            }

            // Appel à la méthode pour gérer les balises <b> à l'intérieur des citations
            log.info("appel de la méthode replaceBoldInsideQuotes");
            replaceBoldInsideQuotes(document);
            log.info("fin de la méthode replaceBoldInsideQuotes");

        } catch (Exception e) {
            log.error("Erreur lors du remplacement de <b> par <q>", e);
            throw new CustomAppException("Erreur lors du remplacement de <b> par <q>", e);
        }
    }

    // Méthode pour gérer les balises <b> à l'intérieur des citations
    private void replaceBoldInsideQuotes(Document document) throws CustomAppException {
        try {
            XPath xPath = XPathFactory.newInstance().newXPath();
            NodeList pNodes = (NodeList) xPath.evaluate("//p", document, XPathConstants.NODESET);
            for (int i = 0; i < pNodes.getLength(); i++) {
                Node pNode = pNodes.item(i);
                String pContent = getTextContentWithTags(pNode);
                log.info("Traitement du nœud <p> avec contenu: {}", pContent);

                if (pContent.contains("«") && pContent.contains("»")) {
                    Pattern pattern = Pattern.compile("«([^«]*)<b>([^«]*)</b>([^«]*)»");
                    Matcher matcher = pattern.matcher(pContent);

                    if (matcher.find()) {
                        String beforeQuote = pContent.substring(0, matcher.start());
                        String afterQuote = pContent.substring(matcher.end());
                        String quoteContent = matcher.group(1) + matcher.group(2) + matcher.group(3);

                        log.info("Texte avant la citation: {}", beforeQuote);
                        log.info("Texte après la citation: {}", afterQuote);
                        log.info("Contenu de la citation: {}", quoteContent);

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

                        pNode.setTextContent(""); // Clear the current content
                        pNode.appendChild(fragment);

                        log.info("Balise <b> supprimée et <q> ajoutée dans le texte: {}", getTextContentWithTags(pNode));
                    } else {
                        log.info("Aucune balise <b> trouvée à l'intérieur de la citation.");
                    }
                } else {
                    log.info("Le nœud <p> ne contient pas de guillemets français.");
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors du traitement des balises <b> à l'intérieur des citations", e);
            throw new CustomAppException("Erreur lors du traitement des balises <b> à l'intérieur des citations", e);
        }
    }

    // Méthode pour obtenir le contenu texte d'un nœud avec les balises
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

    // Convertir un document XML en chaîne de caractères
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






