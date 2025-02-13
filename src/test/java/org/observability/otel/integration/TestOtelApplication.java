package org.observability.otel.integration;

import org.observability.otel.OtelApplication;
import org.observability.otel.config.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

public class TestOtelApplication {

	public static void main(String[] args) {
		SpringApplication.from(OtelApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
