package com.ouestfrance.modecitation.Treatment;

import com.ouestfrance.modecitation.Exception.CustomAppException;
import com.ouestfrance.modecitation.Services.RulesService;
import com.ouestfrance.modecitation.Services.XmlService;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class ModeCitationTreatment {

    @Value("${input.xml.source}")
    private String inputXmlSource;

    @Value("${output.xml.path}")
    private String outputXmlPath;

    @Value("${rules.json.path}")
    private String rulesJsonPath;

    @Autowired
    private RulesService rulesService;

    @Autowired
    private XmlService xmlService;

    public void applyQuoteMode() throws CustomAppException {
        try {
            log.info("Starting applyQuoteMode");

            log.info("Reading rules from: {}", rulesJsonPath);
            var allRulesNode = rulesService.readRules(rulesJsonPath);
            log.info("Rules JSON content: {}", allRulesNode != null ? allRulesNode.toString() : "null");

            log.info("Reading XML content from: {}", inputXmlSource);
            var document = xmlService.loadDocument(inputXmlSource);
            log.info("Loaded XML Document content: {}", document != null ? xmlService.documentToString(document) : "null");

            log.info("Applying rules to the document");
            rulesService.applyRules(document, allRulesNode);

            log.info("Modified XML Document content before saving: {}", xmlService.documentToString(document));

            log.info("Saving modified document to: {}", outputXmlPath);
            xmlService.saveDocumentToFile(document, outputXmlPath);
            log.info("Finished applyQuoteMode");

            log.info("Final XML Document content after saving: {}", xmlService.documentToString(document));
        } catch (Exception e) {
            log.error("Error applying quote mode", e);
            throw new CustomAppException("Error applying quote mode", e);
        }
    }
}
