package com.coderag;

import org.springframework.boot.SpringApplication;

public class TestCodebaseRagMcpApplication {

	public static void main(String[] args) {
		SpringApplication.from(CodebaseRagMcpApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
