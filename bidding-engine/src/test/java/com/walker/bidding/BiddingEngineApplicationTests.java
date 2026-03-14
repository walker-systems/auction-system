package com.walker.bidding;

import com.walker.bidding.config.DatabaseInitializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BiddingEngineApplicationTests {

	@MockitoBean
	private DatabaseInitializer databaseInitializer;

	@Test
	void contextLoads() {
	}

}
