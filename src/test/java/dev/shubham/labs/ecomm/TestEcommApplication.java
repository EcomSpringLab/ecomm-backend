package dev.shubham.labs.ecomm;

import org.springframework.boot.SpringApplication;

public class TestEcommApplication {

	public static void main(String[] args) {
		SpringApplication.from(EcommApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
