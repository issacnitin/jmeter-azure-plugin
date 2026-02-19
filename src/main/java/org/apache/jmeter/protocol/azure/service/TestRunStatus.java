/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jmeter.protocol.azure.service;

/**
 * Snapshot of an Azure Load Testing test run's current status and metrics,
 * parsed from the data-plane API response.
 */
public final class TestRunStatus {
    private final String testRunId;
    private final String displayName;
    private final String status;           // ACCEPTED, PROVISIONING, PROVISIONED, CONFIGURING, EXECUTING, DONE, etc.
    private final String portalUrl;
    private final String startDateTime;
    private final String endDateTime;
    private final long durationMs;         // elapsed duration in millis (0 if not available)
    private final long virtualUsers;
    private final double totalRequests;
    private final double successfulRequests;
    private final double failedRequests;
    private final double avgResponseTimeMs;
    private final double p90ResponseTimeMs;
    private final double p95ResponseTimeMs;
    private final double p99ResponseTimeMs;
    private final double errorPercentage;
    private final double requestsPerSecond;

    private TestRunStatus(Builder builder) {
        this.testRunId = builder.testRunId;
        this.displayName = builder.displayName;
        this.status = builder.status;
        this.portalUrl = builder.portalUrl;
        this.startDateTime = builder.startDateTime;
        this.endDateTime = builder.endDateTime;
        this.durationMs = builder.durationMs;
        this.virtualUsers = builder.virtualUsers;
        this.totalRequests = builder.totalRequests;
        this.successfulRequests = builder.successfulRequests;
        this.failedRequests = builder.failedRequests;
        this.avgResponseTimeMs = builder.avgResponseTimeMs;
        this.p90ResponseTimeMs = builder.p90ResponseTimeMs;
        this.p95ResponseTimeMs = builder.p95ResponseTimeMs;
        this.p99ResponseTimeMs = builder.p99ResponseTimeMs;
        this.errorPercentage = builder.errorPercentage;
        this.requestsPerSecond = builder.requestsPerSecond;
    }

    public String getTestRunId() { return testRunId; }
    public String getDisplayName() { return displayName; }
    public String getStatus() { return status; }
    public String getPortalUrl() { return portalUrl; }
    public String getStartDateTime() { return startDateTime; }
    public String getEndDateTime() { return endDateTime; }
    public long getDurationMs() { return durationMs; }
    public long getVirtualUsers() { return virtualUsers; }
    public double getTotalRequests() { return totalRequests; }
    public double getSuccessfulRequests() { return successfulRequests; }
    public double getFailedRequests() { return failedRequests; }
    public double getAvgResponseTimeMs() { return avgResponseTimeMs; }
    public double getP90ResponseTimeMs() { return p90ResponseTimeMs; }
    public double getP95ResponseTimeMs() { return p95ResponseTimeMs; }
    public double getP99ResponseTimeMs() { return p99ResponseTimeMs; }
    public double getErrorPercentage() { return errorPercentage; }
    public double getRequestsPerSecond() { return requestsPerSecond; }

    /**
     * Whether the test run is still in progress (not yet reached a terminal state).
     * Terminal states: DONE, FAILED, CANCELLED, VALIDATION_FAILURE.
     * CANCELLING is <b>not</b> terminal — the run is still shutting down.
     */
    public boolean isRunning() {
        return status != null
                && !"DONE".equalsIgnoreCase(status)
                && !"FAILED".equalsIgnoreCase(status)
                && !"CANCELLED".equalsIgnoreCase(status)
                && !"VALIDATION_FAILURE".equalsIgnoreCase(status);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private String testRunId = "";
        private String displayName = "";
        private String status = "";
        private String portalUrl = "";
        private String startDateTime = "";
        private String endDateTime = "";
        private long durationMs;
        private long virtualUsers;
        private double totalRequests;
        private double successfulRequests;
        private double failedRequests;
        private double avgResponseTimeMs;
        private double p90ResponseTimeMs;
        private double p95ResponseTimeMs;
        private double p99ResponseTimeMs;
        private double errorPercentage;
        private double requestsPerSecond;

        public Builder testRunId(String v) { this.testRunId = v; return this; }
        public Builder displayName(String v) { this.displayName = v; return this; }
        public Builder status(String v) { this.status = v; return this; }
        public Builder portalUrl(String v) { this.portalUrl = v; return this; }
        public Builder startDateTime(String v) { this.startDateTime = v; return this; }
        public Builder endDateTime(String v) { this.endDateTime = v; return this; }
        public Builder durationMs(long v) { this.durationMs = v; return this; }
        public Builder virtualUsers(long v) { this.virtualUsers = v; return this; }
        public Builder totalRequests(double v) { this.totalRequests = v; return this; }
        public Builder successfulRequests(double v) { this.successfulRequests = v; return this; }
        public Builder failedRequests(double v) { this.failedRequests = v; return this; }
        public Builder avgResponseTimeMs(double v) { this.avgResponseTimeMs = v; return this; }
        public Builder p90ResponseTimeMs(double v) { this.p90ResponseTimeMs = v; return this; }
        public Builder p95ResponseTimeMs(double v) { this.p95ResponseTimeMs = v; return this; }
        public Builder p99ResponseTimeMs(double v) { this.p99ResponseTimeMs = v; return this; }
        public Builder errorPercentage(double v) { this.errorPercentage = v; return this; }
        public Builder requestsPerSecond(double v) { this.requestsPerSecond = v; return this; }
        public TestRunStatus build() { return new TestRunStatus(this); }
    }
}
