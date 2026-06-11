package com.rdas.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI rdasOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Reference Data Aggregation Service (RDAS)")
                        .description("""
                                Single source of truth for country, currency, language and geographical reference data.
                                
                                RDAS wraps the CountryInfo SOAP service behind a clean REST/JSON API with:
                                - Pagination & sorting on all list endpoints
                                - Dynamic filtering by name, continent, currency, language
                                - Redis caching (24h TTL) to eliminate direct SOAP traffic
                                - Circuit breaker for SOAP service resilience
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("RDAS Team")
                                .email("rdas-support@example.com"))
                        .license(new License().name("MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local"),
                        new Server().url("https://rdas.example.com").description("Production")
                ));
    }
}
