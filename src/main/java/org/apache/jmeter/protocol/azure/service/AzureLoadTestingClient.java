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

            // Virtual users: may be at root level or inside loadTestConfiguration
            long vusers = longOrZero(root, "virtualUsers");
            if (vusers == 0) {
                com.fasterxml.jackson.databind.JsonNode ltc = root.get("loadTestConfiguration");
                if (ltc != null) {
                    vusers = longOrZero(ltc, "engineInstances");
                    long optLoad = longOrZero(ltc, "optionalLoadTestConfig");
                    // Some responses have virtualUsers inside optionalLoadTestConfig
                    if (vusers == 0) {
                        vusers = longOrZero(ltc, "virtualUsers");
                    }
                }
            }

            // Duration: may be at root or computed from start/end
            long durationMs = longOrZero(root, "duration");
            if (durationMs == 0) {
                String startStr = textOrEmpty(root, "startDateTime");
                String endStr = textOrEmpty(root, "endDateTime");
                if (!startStr.isEmpty() && !endStr.isEmpty()) {
                    try {
                        java.time.Instant start = java.time.Instant.parse(startStr);
                        java.time.Instant end = java.time.Instant.parse(endStr);
                        durationMs = java.time.Duration.between(start, end).toMillis();
                    } catch (Exception ignore) { }
                }
            }

            TestRunStatus.Builder b = TestRunStatus.builder()
                    .testRunId(textOrEmpty(root, "testRunId"))
                    .displayName(textOrEmpty(root, "displayName"))
                    .status(textOrEmpty(root, "status"))
                    .portalUrl(textOrEmpty(root, "portalUrl"))
                    .startDateTime(textOrEmpty(root, "startDateTime"))
                    .endDateTime(textOrEmpty(root, "endDateTime"))
                    .virtualUsers(vusers)
                    .durationMs(durationMs);

            // Parse test run statistics (nested under testRunStatistics)
            com.fasterxml.jackson.databind.JsonNode stats = root.get("testRunStatistics");
            System.out.println("[AzureClient] testRunStatistics node: " + (stats != null ? stats.toString() : "null"));
            if (stats != null && stats.isObject()) {
                // Aggregate across all sampler/transaction entries
                double totalReqs = 0, successReqs = 0, failedReqs = 0;
                double avgRt = 0, p90 = 0, p95 = 0, p99 = 0, errPct = 0, rps = 0;
                int count = 0;

                var fields = stats.fields();
                while (fields.hasNext()) {
                    var entry = fields.next();
                    com.fasterxml.jackson.databind.JsonNode s = entry.getValue();
                    System.out.println("[AzureClient] Stats entry '" + entry.getKey() + "': " + s.toString());

                    // The API uses "transaction" for total count in some versions,
                    // "sampleCount" in others
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
                }
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
