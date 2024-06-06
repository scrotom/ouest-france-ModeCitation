package com.ouestfrance.modecitation;

import com.ouestfrance.modecitation.Services.ModeCitationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ModecitationApplication implements CommandLineRunner {

	@Autowired
	private ModeCitationService modeCitationService;

	public static void main(String[] args) {
		SpringApplication.run(ModecitationApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		modeCitationService.applyQuoteMode();
	}
}
