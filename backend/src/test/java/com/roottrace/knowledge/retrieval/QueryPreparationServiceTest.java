package com.roottrace.knowledge.retrieval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueryPreparationServiceTest {

    private final QueryPreparationService service = new QueryPreparationService();

    @Test
    void buildQuery_ShouldPreserveTechnicalTokens() {
        String title = "Connection timeout";
        String description = "We are seeing a java.net.SocketTimeoutException when connecting to the database. The pool is HikariPool-1 and it is throwing SQLState 08001.";
        String serviceName = "user-service";
        String env = "production";
        String errorMessage = "java.net.SocketTimeoutException: Read timed out";

        String query = service.buildQuery(title, description, serviceName, env, errorMessage);

        assertThat(query).contains("Connection timeout");
        assertThat(query).contains("user-service");
        assertThat(query).contains("java.net.SocketTimeoutException");
        assertThat(query).contains("HikariPool-1");
        assertThat(query).contains("SQLState 08001");
        assertThat(query).contains("Read timed out");
    }

    @Test
    void extractTechnicalTokens_ShouldHandleEmptyInputs() {
        String query = service.buildQuery("", null, null, null, null);
        assertThat(query).isEmpty();
    }
}
