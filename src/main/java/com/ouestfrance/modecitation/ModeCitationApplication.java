/*
 * Nom         : ModeCitationApplication.java
 *
 * Description : Point d'entrée principal de l'application ModeCitation.
 *
 * Date        : 07/06/2024
 *
 */

package com.ouestfrance.modecitation;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ModeCitationApplication {

	//Lance l'application Spring Boot.
	public static void main(String[] args) {
		SpringApplication.run(ModeCitationApplication.class, args);
	}
}
