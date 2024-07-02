package com.example.monitoring_bot;

import com.slack.api.Slack;
import com.slack.api.webhook.Payload;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.boot.jdbc.metadata.HikariDataSourcePoolMetadata;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import com.sun.management.OperatingSystemMXBean;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.sql.DataSource;
import java.io.File;
import java.lang.management.*;
import java.util.List;
import java.util.Set;

@Component
public class HealthCheckScheduler {

    @Autowired
    private HealthEndpoint healthEndpoint;

    @Autowired
    private MetricsEndpoint metricsEndpoint;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;
//
//    @Autowired
//    private JdbcOperationsSessionRepository sessionRepository;

    @Value("${slack.webhook.url}")
    private String slackWebhookUrl;

    public void reportHealthToSlack() {
        String healthReport = generateHealthReport();
        sendReportToSlack(healthReport);
    }

    private String generateHealthReport() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        // CPU Load
        double cpuLoad = osBean.getSystemLoadAverage();

        // CPU Usage per Core
        String cpuUsageMetricName = "system.cpu.usage";
        MetricsEndpoint.MetricDescriptor metric = metricsEndpoint.metric(cpuUsageMetricName, null);
        List<MetricsEndpoint.Sample> cpuCoreLoads = metric != null ? metric.getMeasurements() : List.of();

        // Memory Usage
        MemoryUsage heapMemoryUsage = memoryBean.getHeapMemoryUsage();
        long usedHeapMemory = heapMemoryUsage.getUsed();
        long maxHeapMemory = heapMemoryUsage.getMax();
        long totalMemory = ((com.sun.management.OperatingSystemMXBean) osBean).getTotalPhysicalMemorySize();
        long freeMemory = ((com.sun.management.OperatingSystemMXBean) osBean).getFreePhysicalMemorySize();
        long usedMemory = totalMemory - freeMemory;

        // Swap Usage
        long totalSwapSpace = ((com.sun.management.OperatingSystemMXBean) osBean).getTotalSwapSpaceSize();
        long freeSwapSpace = ((com.sun.management.OperatingSystemMXBean) osBean).getFreeSwapSpaceSize();
        long usedSwapSpace = totalSwapSpace - freeSwapSpace;

        // Disk Usage
        File root = new File("/");
        long totalSpace = root.getTotalSpace();
        long freeSpace = root.getFreeSpace();
        long usedSpace = totalSpace - freeSpace;

        // Network Traffic
//        String networkTraffic = NetworkTrafficMonitor.getNetworkTraffic();

        // HTTP Requests
        MetricsEndpoint.MetricDescriptor requestsMetric = metricsEndpoint.metric("http.server.requests", null);
        MetricsEndpoint.Sample requestsPerSecond = requestsMetric != null ?
                requestsMetric.getMeasurements().stream()
                .findFirst().orElse(null) : null;

        // JVM Metrics
        int threadCount = threadBean.getThreadCount();
        long gcCount = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
        long gcTime = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();

        // Database Connections
        HikariDataSourcePoolMetadata poolMetadata = new HikariDataSourcePoolMetadata((HikariDataSource) dataSource);
        int activeConnections = poolMetadata.getActive();
        int maxConnections = poolMetadata.getMax();

        // Sessions
//        int activeSessions = sessionRepository.getSessionCount();

        // Tomcat Metrics
        MBeanServer mBeanServer = ManagementFactory.getPlatformMBeanServer();
        int activeThreads = 0;
        int maxThreads = 0;
        int queuedRequests = 0;

        try {
            Set<ObjectName> threadPools = mBeanServer.queryNames(new ObjectName("Tomcat:type=ThreadPool,*"), null);
            for (ObjectName threadPool : threadPools) {
                activeThreads += (Integer) mBeanServer.getAttribute(threadPool, "currentThreadsBusy");
                maxThreads += (Integer) mBeanServer.getAttribute(threadPool, "maxThreads");
                queuedRequests += (Integer) mBeanServer.getAttribute(threadPool, "queueSize");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Actuator Health Status
        String actuatorHealthStatus = healthEndpoint.health().getStatus().toString();

        // Health Report
        StringBuilder report = new StringBuilder();
        report.append("*Health Check Report*\n\n");
        report.append("CPU Load: ").append(String.format("%.2f", cpuLoad)).append("%\n");
        report.append("CPU Core Loads: ").append(cpuCoreLoads.toString()).append("\n");
        report.append("Memory Usage: ").append(formatSize(usedMemory)).append(" / ").append(formatSize(totalMemory)).append("\n");
        report.append("Heap Memory Usage: ").append(formatSize(usedHeapMemory)).append(" / ").append(formatSize(maxHeapMemory)).append("\n");
        report.append("Swap Usage: ").append(formatSize(usedSwapSpace)).append(" / ").append(formatSize(totalSwapSpace)).append("\n");
        report.append("Disk Usage: ").append(formatSize(usedSpace)).append(" / ").append(formatSize(totalSpace)).append("\n");
        report.append("Free Disk Space: ").append(formatSize(freeSpace)).append("\n");
        report.append("Thread Count: ").append(threadCount).append("\n");
        report.append("GC Count: ").append(gcCount).append("\n");
        report.append("GC Time: ").append(gcTime).append(" ms\n");
        report.append("Requests per Second: ").append(requestsPerSecond != null ? requestsPerSecond.getValue() : "N/A").append("\n");
        report.append("Active DB Connections: ").append(activeConnections).append(" / ").append(maxConnections).append("\n");
//        report.append("Active Sessions: ").append(activeSessions).append("\n");
        report.append("Tomcat Active Threads: ").append(activeThreads).append(" / ").append(maxThreads).append("\n");
        report.append("Tomcat Queued Requests: ").append(queuedRequests).append("\n");
//        report.append("Network Traffic: \n").append(networkTraffic).append("\n");
        report.append("Actuator Health Status: ").append(actuatorHealthStatus).append("\n");

        return report.toString();
    }

    private void sendReportToSlack(String report) {
        try {
            Slack slack = Slack.getInstance();
            Payload payload = Payload.builder().text(report).build();
            slack.send(slackWebhookUrl, payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String formatSize(long size) {
        double kilobytes = (size / 1024.0);
        double megabytes = (kilobytes / 1024.0);
        double gigabytes = (megabytes / 1024.0);

        if (gigabytes > 1) {
            return String.format("%.2f GB", gigabytes);
        } else if (megabytes > 1) {
            return String.format("%.2f MB", megabytes);
        } else {
            return String.format("%.2f KB", kilobytes);
        }
    }
}
