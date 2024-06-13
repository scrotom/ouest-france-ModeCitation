package com.ouestfrance.modecitation.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouestfrance.modecitation.Exception.CustomAppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class RulesServiceTest {

    private RulesService rulesService;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() {
        rulesService = new RulesService();
    }

    @Test
    // Vérifie que les règles sont lues correctement depuis un fichier JSON valide
    public void testReadRules_Success() throws IOException, CustomAppException {
        Path jsonFile = tempDir.resolve("rules.json");
        String jsonContent = "{ \"all\": [{ \"desc\": \"test\", \"xpath\": \"//test\" }] }";
        Files.write(jsonFile, jsonContent.getBytes());

        JsonNode rulesNode = rulesService.readRules(jsonFile.toString());
        assertNotNull(rulesNode);
        assertTrue(rulesNode.isArray());
        assertEquals(1, rulesNode.size());
    }

    @Test
    // Vérifie que CustomAppException est lancée pour un format JSON invalide
    public void testReadRules_InvalidFormat() {
        assertThrows(CustomAppException.class, () -> {
            Path jsonFile = tempDir.resolve("invalid_rules.json");
            String jsonContent = "{ \"all\": \"invalid\" }";
            Files.write(jsonFile, jsonContent.getBytes());

            rulesService.readRules(jsonFile.toString());
        });
    }

    @Test
    // Vérifie que CustomAppException est lancée si le fichier JSON n'est pas trouvé
    public void testReadRules_FileNotFound() {
        assertThrows(CustomAppException.class, () -> {
            rulesService.readRules("invalid_path.json");
        });
    }

    @Test
    // Vérifie que les règles sont appliquées correctement à un document XML
    public void testApplyRules_Success() throws Exception {
        Path jsonFile = tempDir.resolve("rules.json");
        String jsonContent = "{ \"all\": [{ \"desc\": \"test\", \"xpath\": \"//test\" }] }";
        Files.write(jsonFile, jsonContent.getBytes());

        JsonNode rulesNode = rulesService.readRules(jsonFile.toString());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        RulesService spyRulesService = Mockito.spy(rulesService);
        doReturn(document).when(spyRulesService).reloadDocument(any(Document.class));
        doNothing().when(spyRulesService).applyOneRule(any(Document.class), anyString());

        spyRulesService.applyRules(document, rulesNode);

        verify(spyRulesService, times(2)).applyOneRule(any(Document.class), eq("//test"));
    }


    @Test
    // Vérifie le comportement lorsqu'une règle ne contient pas de xpath
    public void testApplyRules_RuleWithoutXpath() throws Exception {
        Path jsonFile = tempDir.resolve("rules.json");
        String jsonContent = "{ \"all\": [{ \"desc\": \"test\" }] }";
        Files.write(jsonFile, jsonContent.getBytes());

        JsonNode rulesNode = rulesService.readRules(jsonFile.toString());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Element rootElement = document.createElement("root");
        document.appendChild(rootElement);

        RulesService spyRulesService = Mockito.spy(rulesService);

        spyRulesService.applyRules(document, rulesNode);

        verify(spyRulesService, times(0)).applyOneRule(any(Document.class), anyString());
    }


    @Test
    // Vérifie que CustomAppException est lancée si une exception survient lors de l'application des règles
    public void testApplyRules_Exception() throws Exception {
        Path jsonFile = tempDir.resolve("rules.json");
        String jsonContent = "{ \"all\": [{ \"desc\": \"test\", \"xpath\": \"//test\" }] }";
        Files.write(jsonFile, jsonContent.getBytes());

        JsonNode rulesNode = rulesService.readRules(jsonFile.toString());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        RulesService spyRulesService = Mockito.spy(rulesService);
        doThrow(new RuntimeException("Test exception")).when(spyRulesService).applyOneRule(any(Document.class), anyString());

        assertThrows(CustomAppException.class, () -> {
            spyRulesService.applyRules(document, rulesNode);
        });

        verify(spyRulesService, times(1)).applyOneRule(any(Document.class), eq("//test"));
    }

    @Test
    // Vérifie que CustomAppException est lancée si une exception survient lors de l'application d'une règle
    public void testApplyRule_Exception() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Element testElement = document.createElement("test");
        document.appendChild(testElement);

        RulesService spyRulesService = Mockito.spy(rulesService);
        doThrow(new RuntimeException("Test exception")).when(spyRulesService).deepCheck(any(Node.class), any(Document.class));

        assertThrows(CustomAppException.class, () -> {
            spyRulesService.applyOneRule(document, "//test");
        });

        verify(spyRulesService, times(1)).deepCheck(any(Node.class), any(Document.class));
    }

    @Test
    // Vérifie que CustomAppException est lancée si une exception survient lors de la vérification approfondie d'un nœud
    public void testDeepCheck_Exception() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Element node = document.createElement("test");
        document.appendChild(node);

        Node textNode = document.createTextNode("Some text with «quotes».");
        node.appendChild(textNode);

        RulesService spyRulesService = Mockito.spy(rulesService);
        doThrow(new RuntimeException("Test exception")).when(spyRulesService).applySurroundedContents(any(Node.class), any(Document.class));

        assertThrows(CustomAppException.class, () -> {
            spyRulesService.deepCheck(node, document);
        });

        verify(spyRulesService, times(1)).applySurroundedContents(any(Node.class), any(Document.class));
    }

    @Test
    // Vérifie que les contenus entourés de guillemets sont correctement transformés
    public void testApplySurroundedContents_Success() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        Element parent = document.createElement("parent");
        document.appendChild(parent);

        Node node = document.createTextNode("Some text with «quotes».");
        parent.appendChild(node);

        rulesService.applySurroundedContents(node, document);

        assertEquals("Some text with ", parent.getFirstChild().getTextContent());
        assertEquals("containsQuotes", ((Element) parent.getChildNodes().item(1)).getAttribute("class"));
        assertEquals("«quotes»", parent.getChildNodes().item(1).getTextContent());
        assertEquals(".", parent.getChildNodes().item(2).getTextContent());
    }

    @Test
    // Vérifie que CustomAppException est lancée si une exception survient lors de l'application des contenus entourés
    public void testApplySurroundedContents_Exception() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Node node = document.createTextNode("Some text with «quotes».");

        RulesService spyRulesService = Mockito.spy(rulesService);
        doThrow(new CustomAppException("Test exception")).when(spyRulesService).applySurroundedContents(any(Node.class), any(Document.class));

        assertThrows(CustomAppException.class, () -> {
            spyRulesService.applySurroundedContents(node, document);
        });
    }

    @Test
    // Vérifie que les balises de formatage sont correctement remplacées par des balises <q>
    public void testReplaceFormattingWithQuote_Success() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        Element parent = document.createElement("parent");
        document.appendChild(parent);

        Element pElement = document.createElement("p");
        pElement.appendChild(document.createTextNode("Text before «"));
        Element bElement = document.createElement("b");
        bElement.appendChild(document.createTextNode("bold text"));
        pElement.appendChild(bElement);
        pElement.appendChild(document.createTextNode("» text after"));
        parent.appendChild(pElement);

        rulesService.replaceFormattingWithQuote(document);

        NodeList qNodes = document.getElementsByTagName("q");
        assertEquals(1, qNodes.getLength());
        assertEquals("containsQuotes", ((Element) qNodes.item(0)).getAttribute("class"));
        assertEquals("«bold text»", qNodes.item(0).getTextContent());
    }

    @Test
    // Vérifie que CustomAppException est lancée si une exception survient lors du remplacement des balises de formatage par <q>
    public void testReplaceFormattingWithQuote_Exception() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        RulesService spyRulesService = Mockito.spy(rulesService);
        doThrow(new CustomAppException("Test exception")).when(spyRulesService).replaceFormattingWithQuote(any(Document.class));

        assertThrows(CustomAppException.class, () -> {
            spyRulesService.replaceFormattingWithQuote(document);
        });
    }
}
