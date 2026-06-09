package com.autonomouspm;

import com.autonomouspm.service.AiService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AutonomousSoftwareProjectManagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(AutonomousSoftwareProjectManagerApplication.class, args);
	}

	@Bean
	public CommandLineRunner testOpenRouter(AiService aiService) {
		return args -> {
			System.out.println("==================================================");
			System.out.println("TESTING OPENROUTER CONNECTION...");
			System.out.println("==================================================");
			try {
				String response = aiService.chat(
					"You are a test agent. You must respond with a JSON object containing a single key 'status' with the value 'OK'. Do not output anything else.",
					"Verify API connection."
				);
				System.out.println("RESPONSE FROM OPENROUTER: " + response);
				System.out.println("==================================================");
			} catch (Exception e) {
				System.err.println("OPENROUTER ERROR: " + e.getMessage());
				e.printStackTrace();
				System.out.println("==================================================");
			}
		};
	}

}
