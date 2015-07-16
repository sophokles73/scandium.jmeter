package org.eclipse.californium.scandium.server;

import java.util.logging.Level;
import java.util.logging.LogManager;

import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

@ComponentScan
@Configuration
public class Application {

	public static void main(String[] args) {
		SLF4JBridgeHandler.removeHandlersForRootLogger(); 
		SLF4JBridgeHandler.install();
//		LogManager.getLogManager().getLogger("").setLevel(Level.ALL);
		SpringApplication.run(Application.class, args);
	}

}
