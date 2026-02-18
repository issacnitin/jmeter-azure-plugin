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

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.identity.InteractiveBrowserCredentialBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Service for authenticating with Azure using the Azure Identity SDK.
 * <p>
 * Tries {@link com.azure.identity.DefaultAzureCredential} first (supports
 * environment variables, managed identity, Azure CLI, etc.). If that is not
 * configured, falls back to interactive browser login.
 * </p>
 */
public final class AzureAuthService {
    private static final Logger log = LoggerFactory.getLogger(AzureAuthService.class);

    private static volatile TokenCredential cachedCredential;

    private AzureAuthService() {
        // utility class
    }

    /**
     * Obtain an Azure {@link TokenCredential}.
     * <p>
     * On the first call the user will be prompted to log in via the system browser
     * (unless environment-based credentials are already available).  Subsequent
     * calls return the cached credential.
     * </p>
     *
     * @return a {@link TokenCredential} that can be used with Azure SDK clients
     */
    public static TokenCredential getCredential() {
        if (cachedCredential != null) {
            return cachedCredential;
        }
        synchronized (AzureAuthService.class) {
            if (cachedCredential != null) {
                return cachedCredential;
            }
            cachedCredential = createCredential();
            return cachedCredential;
        }
    }

    /**
     * Clear the cached credential so the next call to {@link #getCredential()}
     * triggers a fresh login.
     */
    public static synchronized void clearCredential() {
        cachedCredential = null;
    }

    private static TokenCredential createCredential() {
        // Try DefaultAzureCredential first – works when Azure CLI / env vars / managed identity is set up
        try {
            TokenCredential defaultCred = new DefaultAzureCredentialBuilder().build();
            log.info("Using DefaultAzureCredential for Azure authentication");
            return defaultCred;
        } catch (Exception e) {
            log.info("DefaultAzureCredential not available, falling back to interactive browser login", e);
        }

        // Fall back to interactive browser login
        TokenCredential browserCred = new InteractiveBrowserCredentialBuilder()
                .redirectUrl("http://localhost:8400")
                .build();
        log.info("Using InteractiveBrowserCredential for Azure authentication");
        return browserCred;
    }
}
