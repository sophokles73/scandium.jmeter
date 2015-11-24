package org.eclipse.californium.scandium.server;

import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan
@Configuration
@EnableAutoConfiguration
public class Application {

	public static void main(String[] args) {
		SLF4JBridgeHandler.removeHandlersForRootLogger(); 
		SLF4JBridgeHandler.install();
		SpringApplication.run(Application.class, args);
	}

}
