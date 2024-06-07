package com.ouestfrance.modecitation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

@SpringBootTest
public class ModecitationApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    public void contextLoads() {
        // Vérifie que le contexte Spring se charge correctement
        assertNotNull(applicationContext, "Le contexte Spring n'a pas pu être chargé");
    }

    @Test
    public void main() {
        // Vérifie que l'application se lance sans exceptions
        ModecitationApplication.main(new String[] {});
    }
}
