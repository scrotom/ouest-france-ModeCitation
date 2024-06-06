package com.ouestfrance.modecitation.Treatment;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouestfrance.modecitation.Exception.CustomAppException;
import com.ouestfrance.modecitation.Services.RulesService;
import com.ouestfrance.modecitation.Services.XmlService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.w3c.dom.Document;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class ModeCitationTreatmentTest {

    @InjectMocks
    private ModeCitationTreatment modeCitationTreatment;

    @Mock
    private RulesService rulesService;

    @Mock
    private XmlService xmlService;

    @TempDir
    Path tempDir;

    @BeforeEach
    public void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        injectPrivateField(modeCitationTreatment, "inputXmlPath", tempDir.resolve("input.xml").toString());
        injectPrivateField(modeCitationTreatment, "outputXmlPath", tempDir.resolve("output.xml").toString());
        injectPrivateField(modeCitationTreatment, "rulesJsonPath", tempDir.resolve("rules.json").toString());
    }

    private void injectPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    @Test
    public void testApplyQuoteMode_Success() throws Exception {
        // Création des fichiers temporaires
        String xmlContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?><doc></doc>";
        Files.write(tempDir.resolve("input.xml"), xmlContent.getBytes());

        String jsonContent = "{ \"all\": [{ \"desc\": \"test\", \"xpath\": \"//test\" }] }";
        Files.write(tempDir.resolve("rules.json"), jsonContent.getBytes());

        Document document = mock(Document.class);
        JsonNode jsonNode = mock(JsonNode.class);

        when(xmlService.loadDocument(anyString())).thenReturn(document);
        when(rulesService.readRules(anyString())).thenReturn(jsonNode);

        modeCitationTreatment.applyQuoteMode();

        verify(xmlService, times(1)).loadDocument(anyString());
        verify(rulesService, times(1)).readRules(anyString());
        verify(rulesService, times(1)).applyRules(any(Document.class), any(JsonNode.class));
        verify(xmlService, times(1)).saveDocumentToFile(any(Document.class), anyString());
    }

    @Test
    public void testApplyQuoteMode_Exception() throws Exception {
        // Création des fichiers temporaires
        String xmlContent = "<?xml version=\"1.0\" encoding=\"utf-8\"?><doc></doc>";
        Files.write(tempDir.resolve("input.xml"), xmlContent.getBytes());

        String jsonContent = "{ \"all\": [{ \"desc\": \"test\", \"xpath\": \"//test\" }] }";
        Files.write(tempDir.resolve("rules.json"), jsonContent.getBytes());

        doThrow(new CustomAppException("Test exception")).when(xmlService).loadDocument(anyString());

        assertThrows(CustomAppException.class, () -> {
            modeCitationTreatment.applyQuoteMode();
        });

        verify(xmlService, times(1)).loadDocument(anyString());
        verify(rulesService, times(1)).readRules(anyString());
        verify(rulesService, times(0)).applyRules(any(Document.class), any(JsonNode.class));
        verify(xmlService, times(0)).saveDocumentToFile(any(Document.class), anyString());
    }
}
