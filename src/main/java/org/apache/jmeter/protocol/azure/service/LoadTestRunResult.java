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
 * Immutable result of triggering a load test run, containing the test run ID
 * and the portal URL for monitoring the run in the Azure Portal.
 */
public final class LoadTestRunResult {
    private final String testRunId;
    private final String portalUrl;

    public LoadTestRunResult(String testRunId, String portalUrl) {
        this.testRunId = testRunId;
        this.portalUrl = portalUrl;
    }

    public String getTestRunId() {
        return testRunId;
    }

    /**
     * The portal URL for viewing the test run in the Azure Portal.
     * May be {@code null} if the API did not return one.
     */
    public String getPortalUrl() {
        return portalUrl;
    }

    @Override
    public String toString() {
        return "LoadTestRunResult{testRunId='" + testRunId + "', portalUrl='" + portalUrl + "'}";
    }
}
