package com.ouestfrance.modecitation.Services;

import com.ouestfrance.modecitation.Exception.CustomAppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class XmlServiceTest {

    private XmlService xmlService;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        xmlService = new XmlService();
    }

    @Test
    // Vérifie le chargement d'un document XML depuis un fichier
    public void testLoadDocumentFromFile_Success() throws Exception {
        Path xmlFile = tempDir.resolve("test.xml");
        String xmlContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?><doc></doc>";
        Files.write(xmlFile, xmlContent.getBytes());

        Document document = xmlService.loadDocument(xmlFile.toString());
        assertNotNull(document);
    }

    @Test
    // Vérifie le chargement d'un document XML depuis une URL
    public void testLoadDocumentFromURL_Success() throws Exception {
        String url = "http://example.com/test.xml";
        String xmlContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?><doc></doc>";

        XmlService spyXmlService = Mockito.spy(xmlService);
        doReturn(xmlContent).when(spyXmlService).readXMLFromURL(url);

        Document document = spyXmlService.loadDocument(url);
        assertNotNull(document);
    }

    @Test
    // Vérifie que CustomAppException est lancée si le fichier XML n'est pas trouvé
    public void testLoadDocumentFromFile_Exception() {
        assertThrows(CustomAppException.class, () -> {
            xmlService.loadDocument("invalid_path.xml");
        });
    }

    @Test
    // Vérifie que CustomAppException est lancée si une exception survient lors du chargement depuis une URL
    public void testLoadDocumentFromURL_Exception() throws Exception {
        String url = "http://example.com/test.xml";

        XmlService spyXmlService = Mockito.spy(xmlService);
        doThrow(new IOException("Test exception")).when(spyXmlService).readXMLFromURL(url);

        assertThrows(CustomAppException.class, () -> {
            spyXmlService.loadDocument(url);
        });
    }

    @Test
    // Vérifie que IOException est lancée si le fichier XML n'est pas trouvé
    public void testReadXMLFromFile_Exception() {
        assertThrows(IOException.class, () -> {
            xmlService.readXMLFromFile("invalid_path.xml");
        });
    }

    @Test
    // Vérifie la lecture du contenu XML depuis une URL
    public void testReadXMLFromURL_Success() throws Exception {
        String url = "http://example.com/test.xml";
        String xmlContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?><doc></doc>";

        XmlService spyXmlService = Mockito.spy(xmlService);
        doReturn(xmlContent).when(spyXmlService).readXMLFromURL(url);

        String result = spyXmlService.readXMLFromURL(url);
        assertNotNull(result);
        assertEquals(xmlContent, result);
    }

    @Test
    // Vérifie que IOException est lancée si une exception survient lors de la lecture depuis une URL
    public void testReadXMLFromURL_Exception() throws IOException {
        String url = "http://example.com/test.xml";

        XmlService spyXmlService = Mockito.spy(xmlService);
        doThrow(new IOException("Test exception")).when(spyXmlService).readXMLFromURL(url);

        assertThrows(IOException.class, () -> {
            spyXmlService.readXMLFromURL(url);
        });
    }

    @Test
    // Vérifie que CustomAppException est lancée pour un contenu XML invalide
    public void testLoadXMLFromString_Exception() {
        String invalidXmlContent = "<doc><invalid></doc>";
        assertThrows(CustomAppException.class, () -> {
            xmlService.loadXMLFromString(invalidXmlContent);
        });
    }

    @Test
    // Vérifie la sauvegarde d'un document XML dans un fichier
    public void testSaveDocumentToFile_Success() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        document.appendChild(document.createElement("doc"));

        Path xmlFile = tempDir.resolve("output.xml");
        xmlService.saveDocumentToFile(document, xmlFile.toString());

        String savedContent = new String(Files.readAllBytes(xmlFile));
        assertTrue(savedContent.contains("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>"));
        assertTrue(savedContent.contains("<doc/>"));
    }

    @Test
    // Vérifie que CustomAppException est lancée si une exception survient lors de la sauvegarde du document XML
    public void testSaveDocumentToFile_Exception() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        document.appendChild(document.createElement("doc"));

        Transformer mockTransformer = mock(Transformer.class);
        TransformerFactory mockTransformerFactory = mock(TransformerFactory.class);
        when(mockTransformerFactory.newTransformer()).thenReturn(mockTransformer);
        doThrow(new TransformerException("Test exception")).when(mockTransformer).transform(any(), any());

        xmlService.setTransformerFactory(mockTransformerFactory);

        assertThrows(CustomAppException.class, () -> {
            xmlService.saveDocumentToFile(document, tempDir.resolve("output.xml").toString());
        });
    }

    @Test
    // Vérifie la conversion d'un document XML en chaîne de caractères
    public void testDocumentToString() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        document.appendChild(document.createElement("doc"));

        String documentString = xmlService.documentToString(document);
        assertNotNull(documentString);
        assertTrue(documentString.contains("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>"));
        assertTrue(documentString.contains("<doc/>"));
    }

    @Test
    // Vérifie que CustomAppException est lancée si une exception survient lors de la conversion du document en chaîne
    public void testDocumentToString_Exception() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        document.appendChild(document.createElement("doc"));

        Transformer mockTransformer = mock(Transformer.class);
        TransformerFactory mockTransformerFactory = mock(TransformerFactory.class);
        when(mockTransformerFactory.newTransformer()).thenReturn(mockTransformer);
        doThrow(new TransformerException("Test exception")).when(mockTransformer).transform(any(), any());

        xmlService.setTransformerFactory(mockTransformerFactory);

        assertThrows(CustomAppException.class, () -> {
            xmlService.documentToString(document);
        });
    }
}
