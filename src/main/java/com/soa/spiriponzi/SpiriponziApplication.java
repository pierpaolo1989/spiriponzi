package com.soa.spiriponzi;

import com.soa.spiriponzi.config.EnvConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SpiriponziApplication {

	public static void main(String[] args) {
		EnvConfig.loadEnv();
		SpringApplication.run(SpiriponziApplication.class, args);
	}

}
