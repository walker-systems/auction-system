package com.walkersystems.sentinel;

import org.springframework.boot.SpringApplication;

public class TestSentinelServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(SentinelServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
