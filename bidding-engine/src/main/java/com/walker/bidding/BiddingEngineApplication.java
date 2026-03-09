package com.walker.bidding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BiddingEngineApplication {

	public static void main(String[] args) {
		SpringApplication.run(BiddingEngineApplication.class, args);
	}

}
