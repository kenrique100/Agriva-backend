package com.akentech.Agriva;

import org.springframework.boot.SpringApplication;

public class TestAgrivaApplication {

	public static void main(String[] args) {
		SpringApplication.from(AgrivaApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
