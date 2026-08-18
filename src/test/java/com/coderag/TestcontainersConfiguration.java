package com.coderag;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.ollama.OllamaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	OllamaContainer ollamaContainer() {
		return new OllamaContainer(DockerImageName.parse("ollama/ollama:latest"));
	}

	// One Postgres instance for both the vector store and the plain JDBC queries (e.g.
	// list_indexed_repos) - pgvector is just the postgres image with the extension baked in,
	// not a separate service.
	@Bean
	@ServiceConnection
	PostgreSQLContainer pgvectorContainer() {
		return new PostgreSQLContainer(DockerImageName.parse("pgvector/pgvector:pg16"));
	}

}
