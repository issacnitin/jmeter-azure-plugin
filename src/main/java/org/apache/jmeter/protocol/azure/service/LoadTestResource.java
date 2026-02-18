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
 * Immutable record representing an Azure Load Testing resource discovered
 * via the Azure Resource Manager API.
 */
public final class LoadTestResource {
    private final String id;
    private final String name;
    private final String resourceGroup;
    private final String subscriptionId;
    private final String location;
    private final String dataPlaneUri;

    public LoadTestResource(String id, String name, String resourceGroup,
                            String subscriptionId, String location, String dataPlaneUri) {
        this.id = id;
        this.name = name;
        this.resourceGroup = resourceGroup;
        this.subscriptionId = subscriptionId;
        this.location = location;
        this.dataPlaneUri = dataPlaneUri;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getResourceGroup() {
        return resourceGroup;
    }

    public String getSubscriptionId() {
        return subscriptionId;
    }

    public String getLocation() {
        return location;
    }

    /**
     * The data-plane URI for this load test resource,
     * e.g. {@code https://<unique-id>.<region>.cnt-prod.loadtesting.azure.com}
     */
    public String getDataPlaneUri() {
        return dataPlaneUri;
    }

    @Override
    public String toString() {
        return name + " (" + resourceGroup + " / " + location + ")";
    }
}
