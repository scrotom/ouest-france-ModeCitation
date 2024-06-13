/*
 * Nom         : ModeCitationTreatment.java
 *
 * Description : Classe permettant d'appliquer le mode citation sur les documents XML.
 *
 * Date        : 07/06/2024
 *
 */

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

    //Applique le mode citation aux documents XML en utilisant les règles JSON
    public void applyQuoteMode() throws CustomAppException {
        try {
            log.info("Lecture des règles depuis : {}", rulesJsonPath);
            var allRulesNode = rulesService.readRules(rulesJsonPath);

            log.info("Lecture du document XML depuis : {}", inputXmlSource);
            var document = xmlService.loadDocument(inputXmlSource);

            log.info("Application des règles au document XML");
            rulesService.applyRules(document, allRulesNode);

            log.info("Enregistrement du document modifié dans : {}", outputXmlPath);
            xmlService.saveDocumentToFile(document, outputXmlPath);
            log.info("Traitement du mode citation terminé");
        } catch (Exception e) {
            log.error("Erreur lors de l'application du mode citation", e);
            throw new CustomAppException("Erreur lors de l'application du mode citation", e);
        }
    }
}

