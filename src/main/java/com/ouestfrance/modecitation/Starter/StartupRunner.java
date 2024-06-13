/*
 * Nom         : StartupRunner.java
 *
 * Description : Classe permettant d'exécuter le traitement au démarrage de l'application.
 *
 * Date        : 07/06/2024
 *
 */

package com.ouestfrance.modecitation.Starter;

import com.ouestfrance.modecitation.Exception.CustomAppException;
import com.ouestfrance.modecitation.Treatment.ModeCitationTreatment;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class StartupRunner implements CommandLineRunner {

    @Autowired
    private ModeCitationTreatment modeCitationService;

    //Lance le service de traitement des citations au démarrage
    @Override
    public void run(String... args) {
        try {
            log.info("Démarrage de l'application Mode Citation");
            modeCitationService.applyQuoteMode();
            log.info("Traitement des citations terminé avec succès");
        } catch (CustomAppException e) {
            log.error("Une erreur est survenue lors de l'application du mode citation", e);
        }
    }
}


