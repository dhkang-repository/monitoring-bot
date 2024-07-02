package com.example.monitoring_bot;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointProperties;
import org.springframework.boot.actuate.health.HealthContributorRegistry;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.HealthEndpointGroups;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.context.annotation.Bean;

public class HealthMetricsConfig {
    @Bean
    public HealthEndpoint healthEndpoint(HealthContributorRegistry healthContributorRegistry,
                                         HealthEndpointGroups groups,
                                         HealthEndpointProperties properties) {
        return new HealthEndpoint(healthContributorRegistry, groups, properties.getLogging().getSlowIndicatorThreshold());
    }

    @Bean
    public MetricsEndpoint metricsEndpoint(MeterRegistry registry) {
        return new MetricsEndpoint(registry);
    }
}
