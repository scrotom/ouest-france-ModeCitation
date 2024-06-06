package com.ouestfrance.modecitation.Services;

import com.ouestfrance.modecitation.Exception.CustomAppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.DocumentFragment;

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
    public void testLoadDocument_Success() throws Exception {
        // Création d'un fichier XML temporaire
        Path xmlFile = tempDir.resolve("test.xml");
        String xmlContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?><doc></doc>";
        Files.write(xmlFile, xmlContent.getBytes());

        Document document = xmlService.loadDocument(xmlFile.toString());
        assertNotNull(document);
    }

    @Test
    public void testLoadDocument_FileNotFound() {
        assertThrows(CustomAppException.class, () -> {
            xmlService.loadDocument("invalid_path.xml");
        });
    }

    @Test
    public void testLoadDocument_Exception() throws Exception {
        XmlService spyXmlService = Mockito.spy(xmlService);
        doThrow(new IOException("Test exception")).when(spyXmlService).readXMLFile(anyString());

        assertThrows(CustomAppException.class, () -> {
            spyXmlService.loadDocument(tempDir.resolve("test.xml").toString());
        });
    }

    @Test
    public void testReadXMLFile_Exception() {
        assertThrows(IOException.class, () -> {
            xmlService.readXMLFile("invalid_path.xml");
        });
    }

    @Test
    public void testLoadXMLFromString_Exception() {
        String invalidXmlContent = "<doc><invalid></doc>";
        assertThrows(CustomAppException.class, () -> {
            xmlService.loadXMLFromString(invalidXmlContent);
        });
    }

    @Test
    public void testSaveDocumentToFile_Success() throws Exception {
        // Création d'un document XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        document.appendChild(document.createElement("doc"));

        // Sauvegarde du document XML
        Path xmlFile = tempDir.resolve("output.xml");
        xmlService.saveDocumentToFile(document, xmlFile.toString());

        // Vérification du contenu du fichier
        String savedContent = new String(Files.readAllBytes(xmlFile));
        assertTrue(savedContent.contains("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"no\"?>"));
        assertTrue(savedContent.contains("<doc/>"));
    }

    @Test
    public void testSaveDocumentToFile_Exception() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        document.appendChild(document.createElement("doc"));

        // Mock Transformer and TransformerFactory
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
    public void testDocumentToString_Exception() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        document.appendChild(document.createElement("doc"));

        // Mock Transformer and TransformerFactory
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
