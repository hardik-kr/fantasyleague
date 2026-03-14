package com.cricket.fantasyleague;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FantasyleagueApplication {

	public static void main(String[] args) {
		SpringApplication.run(FantasyleagueApplication.class, args); 
	}

}
