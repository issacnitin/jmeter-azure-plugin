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
     * List all Azure Load Testing resources the authenticated identity can see
     * across all subscriptions.
     *
     * @return a list of {@link LoadTestResource} objects
     */
    public List<LoadTestResource> listResources() {
        log.info("Listing Azure Load Testing resources across all subscriptions...");
        List<LoadTestResource> resources = new ArrayList<>();

        com.azure.core.management.profile.AzureProfile profile =
                new com.azure.core.management.profile.AzureProfile(
                        null, null,
                        com.azure.core.management.AzureEnvironment.AZURE);

        // List all accessible subscriptions first, then query each one
        ResourceManager.Authenticated authenticated =
                ResourceManager.authenticate(credential, profile);

        authenticated.subscriptions().list().forEach(subscription -> {
            String subId = subscription.subscriptionId();
            log.info("Checking subscription: {} ({})", subscription.displayName(), subId);
            try {
                LoadTestManager manager = LoadTestManager.authenticate(credential,
                        new com.azure.core.management.profile.AzureProfile(
                                null, subId,
                                com.azure.core.management.AzureEnvironment.AZURE));

                manager.loadTests().list().forEach(r -> {
                    String id = r.id();
                    String name = r.name();
                    String resourceGroup = extractResourceGroup(id);
                    String subscriptionId = extractSubscriptionId(id);
                    String location = r.regionName();
                    String dataPlaneUri = r.innerModel().dataPlaneUri();
                    if (dataPlaneUri == null || dataPlaneUri.isBlank()) {
                        dataPlaneUri = "https://" + UUID.randomUUID().toString().substring(0, 8)
                                + "." + location + ".cnt-prod.loadtesting.azure.com";
                    }
                    resources.add(new LoadTestResource(id, name, resourceGroup,
                            subscriptionId, location, dataPlaneUri));
                });
            } catch (Exception e) {
                log.warn("Failed to list resources in subscription {}: {}",
                        subId, e.getMessage());
            }
        });

        log.info("Found {} Azure Load Testing resource(s)", resources.size());
        return resources;
    }

    /**
     * Create a test, upload the JMX file, and start a test run on the given
     * Azure Load Testing resource.
     *
     * @param resource    the target Azure Load Testing resource
     * @param jmxFilePath the path of the JMX file to upload
     * @param testName    human-readable name for the test
     * @return the test run ID
     * @throws Exception if anything goes wrong
     */
    public String triggerLoadTest(LoadTestResource resource, String jmxFilePath, String testName) throws Exception {
        String endpoint = resource.getDataPlaneUri();
        if (!endpoint.startsWith("https://")) {
            endpoint = "https://" + endpoint;
        }

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
        log.info("Created test '{}'", testId);

        // --- 3. Upload the JMX file ---
        File jmxFile = new File(jmxFilePath);
        String fileName = jmxFile.getName();
        BinaryData fileData = BinaryData.fromFile(Path.of(jmxFilePath));

        SyncPoller<BinaryData, BinaryData> uploadPoller = adminClient.beginUploadTestFile(
                testId, fileName, fileData, new RequestOptions());
        uploadPoller.waitForCompletion();
        PollResponse<BinaryData> uploadResult = uploadPoller.poll();
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
        SyncPoller<BinaryData, BinaryData> runPoller = runClient.beginTestRun(
                testRunId, runPayload, new RequestOptions());

        // Don't block waiting for the full run to complete – just confirm it started
        PollResponse<BinaryData> runResponse = runPoller.poll();
        log.info("Test run '{}' started – status: {}", testRunId, runResponse.getStatus());

        return testRunId;
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
