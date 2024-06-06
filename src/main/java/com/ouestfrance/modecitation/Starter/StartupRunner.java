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

    @Override
    public void run(String... args) {
        try {
            modeCitationService.applyQuoteMode();
        } catch (CustomAppException e) {
            log.error("An error occurred while applying quote mode", e);
        }
    }
}
