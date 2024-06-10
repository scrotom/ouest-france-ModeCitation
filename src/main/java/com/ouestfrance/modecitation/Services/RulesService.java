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

    //Applique les règles à un document XML
    public void applyRules(Document document, JsonNode allRulesNode) throws CustomAppException {
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
            replaceBoldWithQuote(document);
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

    //Effectue une vérification approfondie du nœud et de ses enfants (si + de 1 paire de guillemets dans le meme texte par exemple)
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
            log.error("Erreur lors de la vérification approfondie", e);
            throw new CustomAppException("Erreur lors de la vérification approfondie", e);
        }
    }

    //Applique des balises <q> autour du contenu textuel entouré de guillemets français
    public void applySurroundedContents(Node node, Document document) throws CustomAppException {
        try {
            String textContent = node.getTextContent();
            String regex = "«[^«]*?»";
            Matcher matcher = Pattern.compile(regex).matcher(textContent);
            while (matcher.find()) {
                String match = matcher.group();
                log.info("Citation trouvée: {}", match);
                int start = matcher.start();
                int end = matcher.end();

                Node parentNode = node.getParentNode();

                // Vérifie si le parentNode est null avant de l'utiliser
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
                log.info("Balise <q> appliquée autour du texte: {}", match);
            }
        } catch (Exception e) {
            log.error("Erreur lors de l'application des contenus entourés", e);
            throw new CustomAppException("Erreur lors de l'application des contenus entourés", e);
        }
    }

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
                } else {
                    // Si le texte ne commence pas et ne se termine pas par les guillemets français,
                    // retire simplement la balise <b> et conserve son contenu textuel
                    DocumentFragment fragment = document.createDocumentFragment();

                    while (boldNode.hasChildNodes()) {
                        fragment.appendChild(boldNode.getFirstChild());
                    }

                    boldNode.getParentNode().replaceChild(fragment, boldNode);
                    log.info("Balise <b> supprimée autour du texte: {}", boldTextContent);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors du remplacement de <b> par <q>", e);
            throw new CustomAppException("Erreur lors du remplacement de <b> par <q>", e);
        }
    }
}
