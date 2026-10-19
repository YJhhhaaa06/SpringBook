package io.github.yjhhhaaa06.springbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SpringBookApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBookApplication.class, args);
    }

}
