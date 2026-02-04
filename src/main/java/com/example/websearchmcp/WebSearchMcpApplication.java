package com.example.websearchmcp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class WebSearchMcpApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebSearchMcpApplication.class, args);
    }
}
