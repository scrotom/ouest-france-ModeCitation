/*
 * Nom         : XmlService.java
 *
 * Description : Classe permettant de charger, lire et sauvegarder des documents XML.
 *
 * Date        : 07/06/2024
 *
 */

package com.ouestfrance.modecitation.Services;

import com.ouestfrance.modecitation.Exception.CustomAppException;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.net.URL;

@Service
@Log4j2
@Setter
public class XmlService {

    private TransformerFactory transformerFactory = TransformerFactory.newInstance();

    //Charge un document XML à partir d'une source spécifiée
    public Document loadDocument(String source) throws CustomAppException {
        log.info("Lecture du contenu XML depuis : {}", source);
        try {
            String xmlContent = readXMLFromSource(source);
            return loadXMLFromString(xmlContent);
        } catch (IOException | SAXException | ParserConfigurationException e) {
            log.error("Erreur lors du chargement du document XML", e);
            throw new CustomAppException("Erreur lors du chargement du document XML", e);
        }
    }

    //Lit le contenu XML à partir d'une source spécifiée
    public String readXMLFromSource(String source) throws IOException {
        log.info("Lecture du contenu XML depuis : {}", source);
        if (source.startsWith("http://") || source.startsWith("https://")) {
            return readXMLFromURL(source);
        } else {
            return readXMLFromFile(source);
        }
    }

    //Lit le contenu XML à partir d'un fichier
    public String readXMLFromFile(String filePath) throws IOException {
        log.info("Lecture du fichier XML : {}", filePath);
        try (InputStream inputStream = new FileInputStream(new File(filePath));
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du fichier XML", e);
            throw e;
        }
    }

    //Lit le contenu XML à partir d'une URL
    public String readXMLFromURL(String urlString) throws IOException {
        log.info("Lecture du contenu XML depuis l'URL : {}", urlString);
        try (InputStream inputStream = new URL(urlString).openStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (IOException e) {
            log.error("Erreur lors de la lecture du contenu XML depuis l'URL", e);
            throw e;
        }
    }

    //Charge un document XML à partir d'une chaîne de caractères
    public Document loadXMLFromString(String xml) throws ParserConfigurationException, IOException, SAXException, CustomAppException {
        log.info("Chargement du contenu XML depuis une chaîne de caractères");
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setIgnoringElementContentWhitespace(true);

            DocumentBuilder builder = factory.newDocumentBuilder();
            try (Reader reader = new StringReader(xml)) {
                InputSource is = new InputSource(reader);
                is.setEncoding("UTF-8");
                Document document = builder.parse(is);
                document.getDocumentElement().normalize();
                return document;
            }
        } catch (ParserConfigurationException | IOException | SAXException e) {
            log.error("Erreur lors du chargement du contenu XML depuis une chaîne", e);
            throw new CustomAppException("Erreur lors du chargement du contenu XML depuis une chaîne", e);
        }
    }

    //Sauvegarde un document XML dans un fichier
    public void saveDocumentToFile(Document document, String filePath) throws CustomAppException {
        log.info("Enregistrement du document dans le fichier : {}", filePath);
        try {
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");

            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));

            String xmlContent = writer.toString().replaceFirst("\\?>", "?>\n");

            try (BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(filePath, StandardCharsets.UTF_8))) {
                bufferedWriter.write(xmlContent);
            }
        } catch (TransformerException | IOException e) {
            log.error("Erreur lors de l'enregistrement du document XML", e);
            throw new CustomAppException("Erreur lors de l'enregistrement du document XML", e);
        }
    }

    //Convertit un document XML en chaîne de caractères
    public String documentToString(Document document) throws CustomAppException {
        try {
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.getBuffer().toString();
        } catch (TransformerException e) {
            log.error("Erreur lors de la conversion du document en chaîne", e);
            throw new CustomAppException("Erreur lors de la conversion du document en chaîne", e);
        }
    }
}
