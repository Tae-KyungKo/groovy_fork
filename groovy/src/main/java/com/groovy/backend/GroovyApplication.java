package com.groovy.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class GroovyApplication {

	public static void main(String[] args) {
		SpringApplication.run(GroovyApplication.class, args);
	}

}
