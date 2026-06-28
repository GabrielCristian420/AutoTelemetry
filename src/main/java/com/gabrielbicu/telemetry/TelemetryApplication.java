package com.gabrielbicu.telemetry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the AutoTelemetry backend.
 *
 * <p>{@code @SpringBootApplication} is a convenience annotation that combines:
 * <ul>
 *   <li>{@code @Configuration}     — marks the class as a source of bean definitions</li>
 *   <li>{@code @EnableAutoConfiguration} — tells Spring Boot to auto-configure beans
 *       based on the dependencies on the classpath (e.g. a DataSource because we have JPA)</li>
 *   <li>{@code @ComponentScan}     — scans this package and below for controllers, services, ...</li>
 * </ul>
 */
@SpringBootApplication
public class TelemetryApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemetryApplication.class, args);
    }

}
