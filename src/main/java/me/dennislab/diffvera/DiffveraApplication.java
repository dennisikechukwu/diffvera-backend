package me.dennislab.diffvera;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DiffveraApplication {

	public static void main(String[] args) {
		SpringApplication.run(DiffveraApplication.class, args);
	}

}
