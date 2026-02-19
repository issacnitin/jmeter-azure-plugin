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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.azure.core.credential.TokenCredential;
import com.azure.core.http.rest.RequestOptions;
import com.azure.core.util.BinaryData;
import com.azure.core.util.polling.LongRunningOperationStatus;
import com.azure.core.util.polling.PollResponse;
import com.azure.core.util.polling.SyncPoller;
import com.azure.developer.loadtesting.LoadTestAdministrationClient;
import com.azure.developer.loadtesting.LoadTestAdministrationClientBuilder;
import com.azure.developer.loadtesting.LoadTestRunClient;
import com.azure.developer.loadtesting.LoadTestRunClientBuilder;
import com.azure.resourcemanager.loadtesting.LoadTestManager;
import com.azure.resourcemanager.resources.ResourceManager;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client that wraps Azure Load Testing management-plane and data-plane SDKs
 * to list resources, create tests, upload JMX files, and trigger test runs.
 */
public final class AzureLoadTestingClient {
    private static final Logger log = LoggerFactory.getLogger(AzureLoadTestingClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TokenCredential credential;

    public AzureLoadTestingClient(TokenCredential credential) {
        this.credential = credential;
    }

    /**
     * List all Azure subscriptions the authenticated identity can access.
     *
     * @return a list of {@link AzureSubscription} objects
     */
    public List<AzureSubscription> listSubscriptions() {
        System.out.println("[AzureClient] Listing Azure subscriptions...");
        log.info("Listing Azure subscriptions...");
        List<AzureSubscription> subscriptions = new ArrayList<>();

        com.azure.core.management.profile.AzureProfile profile =
                new com.azure.core.management.profile.AzureProfile(
                        null, null,
                        com.azure.core.management.AzureEnvironment.AZURE);

        System.out.println("[AzureClient] Authenticating with ResourceManager...");
        ResourceManager.Authenticated authenticated =
                ResourceManager.authenticate(credential, profile);

        System.out.println("[AzureClient] Enumerating subscriptions...");
        authenticated.subscriptions().list().forEach(subscription -> {
            String subId = subscription.subscriptionId();
            String displayName = subscription.displayName();
            System.out.println("[AzureClient]   Found subscription: " + displayName + " (" + subId + ")");
            log.info("Found subscription: {} ({})", displayName, subId);
            subscriptions.add(new AzureSubscription(subId, displayName));
        });

        System.out.println("[AzureClient] Found " + subscriptions.size() + " subscription(s)");
        log.info("Found {} subscription(s)", subscriptions.size());
        return subscriptions;
    }

    /**
     * List Azure Load Testing resources in a specific subscription.
     *
     * @param subscriptionId the subscription to query
     * @return a list of {@link LoadTestResource} objects
     */
    public List<LoadTestResource> listResourcesInSubscription(String subscriptionId) {
        System.out.println("[AzureClient] Listing Load Testing resources in subscription " + subscriptionId + "...");
        log.info("Listing Load Testing resources in subscription {}...", subscriptionId);
        List<LoadTestResource> resources = new ArrayList<>();

        try {
            LoadTestManager manager = LoadTestManager.authenticate(credential,
                    new com.azure.core.management.profile.AzureProfile(
                            null, subscriptionId,
                            com.azure.core.management.AzureEnvironment.AZURE));

            manager.loadTests().list().forEach(r -> {
                String id = r.id();
                String name = r.name();
                String resourceGroup = extractResourceGroup(id);
                String subId = extractSubscriptionId(id);
                String location = r.regionName();
                String dataPlaneUri = r.innerModel().dataPlaneUri();
                if (dataPlaneUri == null || dataPlaneUri.isBlank()) {
                    dataPlaneUri = "https://" + UUID.randomUUID().toString().substring(0, 8)
                            + "." + location + ".cnt-prod.loadtesting.azure.com";
                }
                System.out.println("[AzureClient]   Found resource: " + name + " in " + resourceGroup + " (" + location + ")");
                log.info("Found resource: {} in {} ({})", name, resourceGroup, location);
                resources.add(new LoadTestResource(id, name, resourceGroup,
                        subId, location, dataPlaneUri));
            });
        } catch (Exception e) {
            System.out.println("[AzureClient] Failed to list resources in subscription " + subscriptionId + ": " + e.getMessage());
            log.warn("Failed to list resources in subscription {}: {}", subscriptionId, e.getMessage());
        }

        System.out.println("[AzureClient] Found " + resources.size() + " resource(s) in subscription " + subscriptionId);
        log.info("Found {} resource(s) in subscription {}", resources.size(), subscriptionId);
        return resources;
    }

    /**
     * List Azure regions where Azure Load Testing is available.
     *
     * @return a list of region display-name / region-name pairs
     */
    public static List<String[]> getAvailableRegions() {
        // Azure Load Testing supported regions (display name, region name)
        return List.of(
                new String[]{"East US", "eastus"},
                new String[]{"East US 2", "eastus2"},
                new String[]{"West US", "westus"},
                new String[]{"West US 2", "westus2"},
                new String[]{"West US 3", "westus3"},
                new String[]{"Central US", "centralus"},
                new String[]{"North Central US", "northcentralus"},
                new String[]{"South Central US", "southcentralus"},
                new String[]{"North Europe", "northeurope"},
                new String[]{"West Europe", "westeurope"},
                new String[]{"UK South", "uksouth"},
                new String[]{"UK West", "ukwest"},
                new String[]{"France Central", "francecentral"},
                new String[]{"Germany West Central", "germanywestcentral"},
                new String[]{"Switzerland North", "switzerlandnorth"},
                new String[]{"Southeast Asia", "southeastasia"},
                new String[]{"East Asia", "eastasia"},
                new String[]{"Japan East", "japaneast"},
                new String[]{"Japan West", "japanwest"},
                new String[]{"Australia East", "australiaeast"},
                new String[]{"Australia Southeast", "australiasoutheast"},
                new String[]{"Brazil South", "brazilsouth"},
                new String[]{"Canada Central", "canadacentral"},
                new String[]{"Central India", "centralindia"},
                new String[]{"South India", "southindia"},
                new String[]{"Korea Central", "koreacentral"},
                new String[]{"South Africa North", "southafricanorth"},
                new String[]{"UAE North", "uaenorth"}
        );
    }

    /**
     * Create a new resource group and an Azure Load Testing resource inside it.
     *
     * @param subscriptionId the subscription in which to create both
     * @param resourceName   the name for both the resource group and the load test resource
     * @param region         the Azure region name (e.g. "eastus")
     * @return the newly created {@link LoadTestResource}
     * @throws Exception if creation fails
     */
    public LoadTestResource createLoadTestResource(String subscriptionId, String resourceName, String region) throws Exception {
        System.out.println("[AzureClient] Creating resource group '" + resourceName + "' in region '" + region + "'...");
        log.info("Creating resource group '{}' in region '{}'...", resourceName, region);

        com.azure.core.management.profile.AzureProfile profile =
                new com.azure.core.management.profile.AzureProfile(
                        null, subscriptionId,
                        com.azure.core.management.AzureEnvironment.AZURE);

        // Create or get the resource group
        ResourceManager resourceManager = ResourceManager.authenticate(credential, profile)
                .withSubscription(subscriptionId);

        resourceManager.resourceGroups()
                .define(resourceName)
                .withRegion(region)
                .create();
        System.out.println("[AzureClient] Resource group '" + resourceName + "' created/verified.");
        log.info("Resource group '{}' created/verified.", resourceName);

        // Create the Azure Load Testing resource
        System.out.println("[AzureClient] Creating Azure Load Testing resource '" + resourceName + "'...");
        log.info("Creating Azure Load Testing resource '{}'...", resourceName);

        LoadTestManager manager = LoadTestManager.authenticate(credential, profile);

        com.azure.resourcemanager.loadtesting.models.LoadTestResource loadTestResource =
                manager.loadTests()
                        .define(resourceName)
                        .withRegion(region)
                        .withExistingResourceGroup(resourceName)
                        .create();

        String id = loadTestResource.id();
        String name = loadTestResource.name();
        String loc = loadTestResource.regionName();
        String dataPlaneUri = loadTestResource.dataPlaneUri();
        if (dataPlaneUri == null || dataPlaneUri.isBlank()) {
            dataPlaneUri = "https://" + UUID.randomUUID().toString().substring(0, 8)
                    + "." + loc + ".cnt-prod.loadtesting.azure.com";
        }

        System.out.println("[AzureClient] Created Load Testing resource: " + name + " (dataPlaneUri=" + dataPlaneUri + ")");
        log.info("Created Load Testing resource: {} (dataPlaneUri={})", name, dataPlaneUri);

        return new LoadTestResource(id, name, resourceName, subscriptionId, loc, dataPlaneUri);
    }

    /**
     * Create a test, upload the JMX file, and start a test run on the given
     * Azure Load Testing resource.
     *
     * @param resource    the target Azure Load Testing resource
     * @param jmxFilePath the path of the JMX file to upload
     * @param testName    human-readable name for the test
     * @return a {@link LoadTestRunResult} containing the test run ID and portal URL
     * @throws Exception if anything goes wrong
     */
    public LoadTestRunResult triggerLoadTest(LoadTestResource resource, String jmxFilePath, String testName) throws Exception {
        String endpoint = resource.getDataPlaneUri();
        if (!endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }

        System.out.println("[AzureClient] Triggering load test on resource '" + resource.getName() + "' at endpoint '" + endpoint + "'");
        log.info("Triggering load test on resource '{}' at endpoint '{}'", resource.getName(), endpoint);

        // --- 1. Create the admin client ---
        LoadTestAdministrationClient adminClient = new LoadTestAdministrationClientBuilder()
                .credential(credential)
                .endpoint(endpoint)
                .buildClient();

        // --- 2. Create or update a test ---
        String testId = "jmeter-" + UUID.randomUUID().toString().substring(0, 8);
        ObjectNode testBody = MAPPER.createObjectNode();
        testBody.put("displayName", testName);
        testBody.put("description", "Load test triggered from Apache JMeter GUI");
        ObjectNode loadTestConfig = testBody.putObject("loadTestConfiguration");
        loadTestConfig.put("engineInstances", 1);
        testBody.putObject("environmentVariables");

        BinaryData testPayload = BinaryData.fromString(MAPPER.writeValueAsString(testBody));
        adminClient.createOrUpdateTestWithResponse(testId, testPayload, new RequestOptions());
        System.out.println("[AzureClient] Created test '" + testId + "'");
        log.info("Created test '{}'", testId);

        // --- 3. Upload the JMX file ---
        File jmxFile = new File(jmxFilePath);
        String fileName = jmxFile.getName();
        BinaryData fileData = BinaryData.fromFile(Path.of(jmxFilePath));

        System.out.println("[AzureClient] Uploading JMX file '" + fileName + "'...");
        SyncPoller<BinaryData, BinaryData> uploadPoller = adminClient.beginUploadTestFile(
                testId, fileName, fileData, new RequestOptions());
        uploadPoller.waitForCompletion();
        PollResponse<BinaryData> uploadResult = uploadPoller.poll();
        System.out.println("[AzureClient] Uploaded JMX file '" + fileName + "' – status: " + uploadResult.getStatus());
        log.info("Uploaded JMX file '{}' – status: {}", fileName, uploadResult.getStatus());

        if (LongRunningOperationStatus.FAILED.equals(uploadResult.getStatus())) {
            throw new RuntimeException("JMX file upload failed: " + uploadResult.getValue());
        }

        // --- 4. Start a test run ---
        LoadTestRunClient runClient = new LoadTestRunClientBuilder()
                .credential(credential)
                .endpoint(endpoint)
                .buildClient();

        String testRunId = "run-" + UUID.randomUUID().toString().substring(0, 8);
        ObjectNode runBody = MAPPER.createObjectNode();
        runBody.put("testId", testId);
        runBody.put("displayName", testName + " - Run");
        runBody.put("description", "Triggered from Apache JMeter at " + java.time.Instant.now());

        BinaryData runPayload = BinaryData.fromString(MAPPER.writeValueAsString(runBody));
        System.out.println("[AzureClient] Starting test run '" + testRunId + "'...");
        SyncPoller<BinaryData, BinaryData> runPoller = runClient.beginTestRun(
                testRunId, runPayload, new RequestOptions());

        // Don't block waiting for the full run to complete – just confirm it started
        PollResponse<BinaryData> runResponse = runPoller.poll();
        System.out.println("[AzureClient] Test run '" + testRunId + "' started – status: " + runResponse.getStatus());
        log.info("Test run '{}' started – status: {}", testRunId, runResponse.getStatus());

        // --- 5. Extract portalUrl from the test run response ---
        String portalUrl = null;
        try {
            BinaryData responseData = runResponse.getValue();
            if (responseData != null) {
                String json = responseData.toString();
                System.out.println("[AzureClient] Test run response: " + json);
                log.info("Test run response: {}", json);
                com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(json);
                com.fasterxml.jackson.databind.JsonNode portalUrlNode = root.get("portalUrl");
                if (portalUrlNode != null && !portalUrlNode.isNull()) {
                    portalUrl = portalUrlNode.asText();
                    System.out.println("[AzureClient] Portal URL: " + portalUrl);
                    log.info("Portal URL: {}", portalUrl);
                } else {
                    System.out.println("[AzureClient] portalUrl not found in response, fetching test run...");
                    // Try fetching the test run to get the portalUrl
                    BinaryData getResponse = runClient.getTestRunWithResponse(testRunId, new RequestOptions())
                            .getValue();
                    if (getResponse != null) {
                        String getJson = getResponse.toString();
                        System.out.println("[AzureClient] Get test run response: " + getJson);
                        log.info("Get test run response: {}", getJson);
                        com.fasterxml.jackson.databind.JsonNode getRoot = MAPPER.readTree(getJson);
                        com.fasterxml.jackson.databind.JsonNode urlNode = getRoot.get("portalUrl");
                        if (urlNode != null && !urlNode.isNull()) {
                            portalUrl = urlNode.asText();
                            System.out.println("[AzureClient] Portal URL (from GET): " + portalUrl);
                            log.info("Portal URL (from GET): {}", portalUrl);
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("[AzureClient] Failed to extract portalUrl: " + e.getMessage());
            log.warn("Failed to extract portalUrl from test run response", e);
        }

        return new LoadTestRunResult(testRunId, portalUrl, endpoint);
    }

    /**
     * Stop / cancel a running test run.
     *
     * @param endpoint  the data-plane endpoint
     * @param testRunId the test run ID to cancel
     */
    public void cancelTestRun(String endpoint, String testRunId) {
        LoadTestRunClient runClient = new LoadTestRunClientBuilder()
                .credential(credential)
                .endpoint(endpoint)
                .buildClient();

        System.out.println("[AzureClient] Cancelling test run '" + testRunId + "'...");
        log.info("Cancelling test run '{}'", testRunId);
        runClient.stopTestRunWithResponse(testRunId, new RequestOptions());
        System.out.println("[AzureClient] Cancel request sent for test run '" + testRunId + "'");
        log.info("Cancel request sent for test run '{}'", testRunId);
    }

    /**
     * Query the current status and metrics for a test run.
     * <p>
     * Uses the test-run GET response for status / timing and the
     * {@code testRunStatistics} block when available (typically after the test
     * completes).  During execution, falls back to the <b>Metrics API</b>
     * ({@code listMetrics}) for live VirtualUsers, ResponseTime (avg / p90 /
     * p95 / p99), RequestsPerSecond, and Errors.
     *
     * @param endpoint  the data-plane endpoint
     * @param testRunId the test run ID
     * @return a {@link TestRunStatus} snapshot
     */
    public TestRunStatus getTestRunStatus(String endpoint, String testRunId) {
        LoadTestRunClient runClient = new LoadTestRunClientBuilder()
                .credential(credential)
                .endpoint(endpoint)
                .buildClient();

        try {
            BinaryData response = runClient.getTestRunWithResponse(testRunId, new RequestOptions())
                    .getValue();
            String json = response.toString();
            System.out.println("[AzureClient] Test run status response: " + json);
            com.fasterxml.jackson.databind.JsonNode root = MAPPER.readTree(json);

            String status = textOrEmpty(root, "status");
            String startStr = textOrEmpty(root, "startDateTime");
            String endStr = textOrEmpty(root, "endDateTime");

            // Virtual users from loadTestConfiguration (config / engine count)
            long vusers = longOrZero(root, "virtualUsers");
            if (vusers == 0) {
                com.fasterxml.jackson.databind.JsonNode ltc = root.get("loadTestConfiguration");
                if (ltc != null) {
                    vusers = longOrZero(ltc, "engineInstances");
                    if (vusers == 0) {
                        vusers = longOrZero(ltc, "virtualUsers");
                    }
                }
            }

            // Duration: compute from start to end, or start to now while running
            long durationMs = longOrZero(root, "duration");
            if (durationMs == 0 && !startStr.isEmpty()) {
                try {
                    java.time.Instant start = java.time.Instant.parse(startStr);
                    java.time.Instant end = endStr.isEmpty()
                            ? java.time.Instant.now()
                            : java.time.Instant.parse(endStr);
                    durationMs = java.time.Duration.between(start, end).toMillis();
                } catch (Exception ignore) { }
            }

            TestRunStatus.Builder b = TestRunStatus.builder()
                    .testRunId(textOrEmpty(root, "testRunId"))
                    .displayName(textOrEmpty(root, "displayName"))
                    .status(status)
                    .portalUrl(textOrEmpty(root, "portalUrl"))
                    .startDateTime(startStr)
                    .endDateTime(endStr)
                    .virtualUsers(vusers)
                    .durationMs(durationMs);

            // --- 1. Try testRunStatistics (populated after test completion) ---
            boolean hasStats = parseTestRunStatistics(root, b);

            // --- 2. Fall back to the Metrics API for live data ---
            if (!hasStats && isMetricsEligible(status)) {
                fetchLiveMetrics(runClient, testRunId, startStr, endStr, b);
            }

            return b.build();
        } catch (Exception e) {
            System.out.println("[AzureClient] Failed to get test run status: " + e.getMessage());
            log.warn("Failed to get test run status: {}", e.getMessage());
            return TestRunStatus.builder()
                    .testRunId(testRunId)
                    .status("UNKNOWN")
                    .build();
        }
    }

    // ------------------------------------------------------------ //
    //  testRunStatistics parsing (available after test completion)  //
    // ------------------------------------------------------------ //

    /**
     * Parse the {@code testRunStatistics} block from the test-run response.
     *
     * @return {@code true} if meaningful statistics were found
     */
    private static boolean parseTestRunStatistics(com.fasterxml.jackson.databind.JsonNode root,
                                                  TestRunStatus.Builder b) {
        com.fasterxml.jackson.databind.JsonNode stats = root.get("testRunStatistics");
        System.out.println("[AzureClient] testRunStatistics node: "
                + (stats != null ? stats.toString() : "null"));
        if (stats == null || !stats.isObject() || stats.isEmpty()) {
            return false;
        }

        double totalReqs = 0, successReqs = 0, failedReqs = 0;
        double avgRt = 0, p90 = 0, p95 = 0, p99 = 0, errPct = 0, rps = 0;
        int count = 0;

        var fields = stats.fields();
        while (fields.hasNext()) {
            var entry = fields.next();
            com.fasterxml.jackson.databind.JsonNode s = entry.getValue();
            System.out.println("[AzureClient] Stats entry '" + entry.getKey() + "': " + s.toString());

            double sampleCount = doubleOrZero(s, "sampleCount");
            double txnCount = doubleOrZero(s, "transaction");
            double entryTotal = sampleCount > 0 ? sampleCount : txnCount;
            double errorCount = doubleOrZero(s, "errorCount");

            totalReqs += entryTotal;
            failedReqs += errorCount;
            successReqs += entryTotal - errorCount;

            avgRt += doubleOrZero(s, "meanResTime");
            p90 += doubleOrZero(s, "pct1ResTime");
            p95 += doubleOrZero(s, "pct2ResTime");
            p99 += doubleOrZero(s, "pct3ResTime");
            errPct += doubleOrZero(s, "errorPct");
            rps += doubleOrZero(s, "throughput");
            count++;
        }

        if (count > 0) {
            b.totalRequests(totalReqs)
                    .successfulRequests(successReqs)
                    .failedRequests(failedReqs)
                    .avgResponseTimeMs(avgRt / count)
                    .p90ResponseTimeMs(p90 / count)
                    .p95ResponseTimeMs(p95 / count)
                    .p99ResponseTimeMs(p99 / count)
                    .errorPercentage(errPct / count)
                    .requestsPerSecond(rps);
            return true;
        }
        return false;
    }

    // ------------------------------------------------- //
    //  Live Metrics API (works during test execution)   //
    // ------------------------------------------------- //

    private static boolean isMetricsEligible(String status) {
        if (status == null) return false;
        String s = status.toUpperCase();
        return "EXECUTING".equals(s) || "DONE".equals(s) || "DEPROVISIONING".equals(s);
    }

    /**
     * Fetch real-time metrics from the Azure Load Testing Metrics API and
     * populate the builder with live values for VirtualUsers, ResponseTime
     * (average, p90, p95, p99), RequestsPerSecond, and Errors.
     */
    private void fetchLiveMetrics(LoadTestRunClient runClient, String testRunId,
                                  String startDateTime, String endDateTime,
                                  TestRunStatus.Builder b) {
        if (startDateTime == null || startDateTime.isEmpty()) {
            return;
        }

        try {
            String end = (endDateTime == null || endDateTime.isEmpty())
                    ? java.time.Instant.now().toString()
                    : endDateTime;
            String timespan = startDateTime + "/" + end;
            String ns = "LoadTestRunMetrics";

            System.out.println("[AzureClient] Fetching live metrics, timespan=" + timespan);

            // VirtualUsers
            double vu = getLatestMetricValue(runClient, testRunId,
                    "VirtualUsers", ns, timespan, "Average");
            if (vu > 0) {
                b.virtualUsers((long) vu);
            }

            // ResponseTime – Average
            double avgRt = getLatestMetricValue(runClient, testRunId,
                    "ResponseTime", ns, timespan, "Average");
            if (avgRt > 0) {
                b.avgResponseTimeMs(avgRt);
            }

            // ResponseTime – Percentile90
            double p90 = getLatestMetricValue(runClient, testRunId,
                    "ResponseTime", ns, timespan, "Percentile90");
            if (p90 > 0) {
                b.p90ResponseTimeMs(p90);
            }

            // ResponseTime – Percentile95
            double p95 = getLatestMetricValue(runClient, testRunId,
                    "ResponseTime", ns, timespan, "Percentile95");
            if (p95 > 0) {
                b.p95ResponseTimeMs(p95);
            }

            // ResponseTime – Percentile99
            double p99 = getLatestMetricValue(runClient, testRunId,
                    "ResponseTime", ns, timespan, "Percentile99");
            if (p99 > 0) {
                b.p99ResponseTimeMs(p99);
            }

            // TotalRequests (use Total aggregation to get cumulative count)
            double totalReqs = getLatestMetricValue(runClient, testRunId,
                    "TotalRequests", ns, timespan, "Total");
            if (totalReqs > 0) {
                b.totalRequests(totalReqs);

                // Compute RPS from total requests and elapsed time
                try {
                    java.time.Instant startInstant = java.time.Instant.parse(startDateTime);
                    java.time.Instant endInstant = (endDateTime == null || endDateTime.isEmpty())
                            ? java.time.Instant.now()
                            : java.time.Instant.parse(endDateTime);
                    double elapsedSecs = java.time.Duration.between(startInstant, endInstant).toMillis() / 1000.0;
                    if (elapsedSecs > 0) {
                        b.requestsPerSecond(totalReqs / elapsedSecs);
                    }
                } catch (Exception ignore) { }
            }

            // Errors (use Total aggregation to get cumulative error count)
            double errorCount = getLatestMetricValue(runClient, testRunId,
                    "Errors", ns, timespan, "Total");
            if (totalReqs > 0) {
                b.failedRequests(errorCount);
                b.successfulRequests(totalReqs - errorCount);
                double errPct = (errorCount / totalReqs) * 100.0;
                b.errorPercentage(errPct);
            }

            System.out.println("[AzureClient] Live metrics: vu=" + vu
                    + " avgRt=" + avgRt + " p90=" + p90 + " p95=" + p95 + " p99=" + p99
                    + " totalReqs=" + totalReqs + " errors=" + errorCount);
        } catch (Exception e) {
            System.out.println("[AzureClient] Failed to fetch live metrics: " + e.getMessage());
            log.warn("Failed to fetch live metrics: {}", e.getMessage());
        }
    }

    /**
     * Call the Metrics API for a single metric and return the <b>latest</b>
     * data-point value across all returned time-series.
     *
     * @param aggregation aggregation type (e.g. "Average", "Percentile90") or {@code null}
     * @return the latest metric value, or {@code 0} if unavailable
     */
    private double getLatestMetricValue(LoadTestRunClient runClient, String testRunId,
                                        String metricName, String namespace,
                                        String timespan, String aggregation) {
        try {
            RequestOptions options = new RequestOptions();
            if (aggregation != null) {
                options.addQueryParam("aggregation", aggregation);
            }

            double latestValue = 0;
            String latestTimestamp = "";

            for (BinaryData item : runClient.listMetrics(testRunId, metricName,
                    namespace, timespan, options)) {
                com.fasterxml.jackson.databind.JsonNode node = MAPPER.readTree(item.toString());

                // Each item is a TimeSeriesElement with "data" array and "dimensionValues"
                com.fasterxml.jackson.databind.JsonNode data = node.get("data");
                if (data != null && data.isArray()) {
                    for (int i = data.size() - 1; i >= 0; i--) {
                        com.fasterxml.jackson.databind.JsonNode point = data.get(i);
                        double val = doubleOrZero(point, "value");
                        String ts = textOrEmpty(point, "timestamp");
                        if (ts.isEmpty()) {
                            ts = textOrEmpty(point, "timeStamp");
                        }
                        if (val > 0 && ts.compareTo(latestTimestamp) >= 0) {
                            latestValue = val;
                            latestTimestamp = ts;
                            break; // data is chronological; last non-zero wins
                        }
                    }
                }

                // Some response shapes put value directly on the element
                double directVal = doubleOrZero(node, "value");
                if (directVal > 0 && latestValue == 0) {
                    latestValue = directVal;
                }
            }

            System.out.println("[AzureClient] Metric " + metricName
                    + " (" + aggregation + "): " + latestValue);
            return latestValue;
        } catch (Exception e) {
            System.out.println("[AzureClient] Error fetching metric " + metricName
                    + " (" + aggregation + "): " + e.getMessage());
            log.debug("Error fetching metric {} ({}): {}", metricName, aggregation, e.getMessage());
            return 0;
        }
    }

    private static String textOrEmpty(com.fasterxml.jackson.databind.JsonNode root, String field) {
        com.fasterxml.jackson.databind.JsonNode n = root.get(field);
        return (n != null && !n.isNull()) ? n.asText() : "";
    }

    private static long longOrZero(com.fasterxml.jackson.databind.JsonNode root, String field) {
        com.fasterxml.jackson.databind.JsonNode n = root.get(field);
        return (n != null && !n.isNull()) ? n.asLong(0) : 0;
    }

    private static double doubleOrZero(com.fasterxml.jackson.databind.JsonNode root, String field) {
        com.fasterxml.jackson.databind.JsonNode n = root.get(field);
        return (n != null && !n.isNull()) ? n.asDouble(0) : 0;
    }

    private static String extractResourceGroup(String armId) {
        if (armId == null) {
            return "";
        }
        String[] parts = armId.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("resourceGroups".equalsIgnoreCase(parts[i]) || "resourcegroups".equalsIgnoreCase(parts[i])) {
                return parts[i + 1];
            }
        }
        return "";
    }

    private static String extractSubscriptionId(String armId) {
        if (armId == null) {
            return "";
        }
        String[] parts = armId.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("subscriptions".equalsIgnoreCase(parts[i])) {
                return parts[i + 1];
            }
        }
        return "";
    }
}
