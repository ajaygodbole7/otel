package org.observability.otel.integration;

import org.junit.jupiter.api.Test;
import org.observability.otel.config.TestcontainersConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OtelApplicationTests {

	@Test
	void contextLoads() {
	}

}
