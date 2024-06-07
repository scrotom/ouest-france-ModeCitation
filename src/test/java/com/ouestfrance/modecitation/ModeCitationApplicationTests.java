package com.ouestfrance.modecitation;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

@SpringBootTest
public class ModeCitationApplicationTests {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    // Vérifie que le contexte Spring se charge correctement
    public void contextLoads() {
        assertNotNull(applicationContext, "Le contexte Spring n'a pas pu être chargé");
    }

    @Test
    // Vérifie que l'application se lance sans exceptions
    public void main() {
        ModeCitationApplication.main(new String[] {});
    }
}
