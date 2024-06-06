package com.ouestfrance.modecitation.Services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public void testReadRules_Success() throws IOException, CustomAppException {
        // Création d'un fichier JSON temporaire
        Path jsonFile = tempDir.resolve("rules.json");
        String jsonContent = "{ \"all\": [{ \"desc\": \"test\", \"xpath\": \"//test\" }] }";
        Files.write(jsonFile, jsonContent.getBytes());

        JsonNode rulesNode = rulesService.readRules(jsonFile.toString());
        assertNotNull(rulesNode);
        assertTrue(rulesNode.isArray());
        assertEquals(1, rulesNode.size());
    }

    @Test
    public void testReadRules_InvalidFormat() {
        assertThrows(CustomAppException.class, () -> {
            Path jsonFile = tempDir.resolve("invalid_rules.json");
            String jsonContent = "{ \"all\": \"invalid\" }";
            Files.write(jsonFile, jsonContent.getBytes());

            rulesService.readRules(jsonFile.toString());
        });
    }

    @Test
    public void testReadRules_FileNotFound() {
        assertThrows(CustomAppException.class, () -> {
            rulesService.readRules("invalid_path.json");
        });
    }

    @Test
    public void testApplyRules_Success() throws Exception {
        // Création d'un fichier JSON temporaire
        Path jsonFile = tempDir.resolve("rules.json");
        String jsonContent = "{ \"all\": [{ \"desc\": \"test\", \"xpath\": \"//test\" }] }";
        Files.write(jsonFile, jsonContent.getBytes());

        JsonNode rulesNode = rulesService.readRules(jsonFile.toString());

        // Simulation du document XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        RulesService spyRulesService = Mockito.spy(rulesService);
        doNothing().when(spyRulesService).applyRule(any(Document.class), anyString());

        spyRulesService.applyRules(document, rulesNode);

        verify(spyRulesService, times(1)).applyRule(any(Document.class), eq("//test"));
    }

    @Test
    public void testApplyRules_RuleWithoutXpath() throws Exception {
        // Création d'un fichier JSON temporaire
        Path jsonFile = tempDir.resolve("rules.json");
        String jsonContent = "{ \"all\": [{ \"desc\": \"test\" }] }";
        Files.write(jsonFile, jsonContent.getBytes());

        JsonNode rulesNode = rulesService.readRules(jsonFile.toString());

        // Simulation du document XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        RulesService spyRulesService = Mockito.spy(rulesService);

        spyRulesService.applyRules(document, rulesNode);

        // Vérification que la méthode applyRule n'a pas été appelée
        verify(spyRulesService, times(0)).applyRule(any(Document.class), anyString());
    }

    @Test
    public void testApplyRules_Exception() throws Exception {
        // Création d'un fichier JSON temporaire
        Path jsonFile = tempDir.resolve("rules.json");
        String jsonContent = "{ \"all\": [{ \"desc\": \"test\", \"xpath\": \"//test\" }] }";
        Files.write(jsonFile, jsonContent.getBytes());

        JsonNode rulesNode = rulesService.readRules(jsonFile.toString());

        // Simulation du document XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        RulesService spyRulesService = Mockito.spy(rulesService);
        doThrow(new RuntimeException("Test exception")).when(spyRulesService).applyRule(any(Document.class), anyString());

        assertThrows(CustomAppException.class, () -> {
            spyRulesService.applyRules(document, rulesNode);
        });

        verify(spyRulesService, times(1)).applyRule(any(Document.class), eq("//test"));
    }

    @Test
    public void testApplyRule_Exception() throws Exception {
        // Simulation du document XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Element testElement = document.createElement("test");
        document.appendChild(testElement);

        RulesService spyRulesService = Mockito.spy(rulesService);
        doThrow(new RuntimeException("Test exception")).when(spyRulesService).deepCheck(any(Node.class), any(Document.class));

        assertThrows(CustomAppException.class, () -> {
            spyRulesService.applyRule(document, "//test");
        });

        // Vérifiez que deepCheck a été appelé et a déclenché l'exception
        verify(spyRulesService, times(1)).deepCheck(any(Node.class), any(Document.class));
    }

    @Test
    public void testDeepCheck_Exception() throws Exception {
        // Simulation du document XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Element node = document.createElement("test");
        document.appendChild(node);

        // Ajoutez un nœud texte comme enfant de l'élément pour déclencher applySurroundedContents
        Node textNode = document.createTextNode("Some text with «quotes».");
        node.appendChild(textNode);

        RulesService spyRulesService = Mockito.spy(rulesService);
        doThrow(new RuntimeException("Test exception")).when(spyRulesService).applySurroundedContents(any(Node.class), any(Document.class));

        assertThrows(CustomAppException.class, () -> {
            spyRulesService.deepCheck(node, document);
        });

        // Vérifiez que applySurroundedContents a été appelé et a déclenché l'exception
        verify(spyRulesService, times(1)).applySurroundedContents(any(Node.class), any(Document.class));
    }

    @Test
    public void testApplySurroundedContents_Success() throws Exception {
        // Simulation du document XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();

        // Créez un élément parent
        Element parent = document.createElement("parent");
        document.appendChild(parent);

        // Créez un nœud texte avec des guillemets et ajoutez-le au parent
        Node node = document.createTextNode("Some text with «quotes».");
        parent.appendChild(node);

        RulesService spyRulesService = Mockito.spy(rulesService);
        spyRulesService.applySurroundedContents(node, document);

        // Vérifiez si la méthode replaceChild a été appelée
        verify(spyRulesService, times(1)).applySurroundedContents(any(Node.class), any(Document.class));

        // Vérifiez que le nœud a été remplacé par un fragment avec l'élément <q>
        assertEquals("Some text with", parent.getFirstChild().getTextContent());
        assertEquals("containsQuotes", ((Element) parent.getChildNodes().item(1)).getAttribute("class"));
        assertEquals("«quotes»", parent.getChildNodes().item(1).getTextContent());
        assertEquals(".", parent.getChildNodes().item(2).getTextContent());
    }


    /*
    @Test
    public void testApplySurroundedContents_Exception() throws Exception {
        // Simulation du document XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.newDocument();
        Node node = document.createTextNode("Some text with «quotes».");

        RulesService spyRulesService = Mockito.spy(rulesService);
        doThrow(new RuntimeException("Test exception")).when(spyRulesService).applySurroundedContents(any(Node.class), any(Document.class));

        assertThrows(CustomAppException.class, () -> {
            spyRulesService.applySurroundedContents(node, document);
        });
    }*/
}
