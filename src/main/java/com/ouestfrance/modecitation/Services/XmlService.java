package com.ouestfrance.modecitation.Services;

import com.ouestfrance.modecitation.Exception.CustomAppException;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;
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

@Service
@Log4j2
@Setter
public class XmlService {

    private TransformerFactory transformerFactory = TransformerFactory.newInstance();

    public Document loadDocument(String filePath) throws CustomAppException {
        log.info("Reading XML content from: {}", filePath);
        try {
            String xmlContent = readXMLFile(filePath);
            return loadXMLFromString(xmlContent);
        } catch (IOException | SAXException | ParserConfigurationException e) {
            log.error("Error loading XML document", e);
            throw new CustomAppException("Error loading XML document", e);
        }
    }

    public String readXMLFile(String filePath) throws IOException {
        log.info("Reading XML file: {}", filePath);
        try (InputStream inputStream = new FileInputStream(new File(filePath));
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder content = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                content.append(line).append("\n");
            }
            return content.toString();
        } catch (IOException e) {
            log.error("Error reading XML file", e);
            throw e;
        }
    }

    public Document loadXMLFromString(String xml) throws ParserConfigurationException, IOException, SAXException, CustomAppException {
        log.info("Loading XML from string");
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
            log.error("Error loading XML from string", e);
            throw new CustomAppException("Error loading XML from string", e);
        }
    }

    public void saveDocumentToFile(Document document, String filePath) throws CustomAppException {
        log.info("Saving document to file: {}", filePath);
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
            log.error("Error saving XML document", e);
            throw new CustomAppException("Error saving XML document", e);
        }
    }

    public String documentToString(Document document) throws CustomAppException {
        try {
            Transformer transformer = transformerFactory.newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            transformer.setOutputProperty(OutputKeys.METHOD, "xml");
            StringWriter writer = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(writer));
            return writer.getBuffer().toString();
        } catch (TransformerException e) {
            log.error("Error converting document to string", e);
            throw new CustomAppException("Error converting document to string", e);
        }
    }
}
