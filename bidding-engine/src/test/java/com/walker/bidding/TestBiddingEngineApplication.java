package com.walker.bidding;

import org.springframework.boot.SpringApplication;

public class TestBiddingEngineApplication {

	static void main(String[] args) {
		SpringApplication.from(BiddingEngineApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
