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

import java.time.Duration;

import com.azure.core.credential.TokenCredential;
import com.azure.core.credential.TokenRequestContext;
import com.azure.identity.AzureCliCredentialBuilder;
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
        // Try Azure CLI credential first – fast and deterministic
        System.out.println("[AzureAuth] Attempting Azure CLI credential...");
        log.info("Attempting Azure CLI credential...");
        try {
            TokenCredential cliCred = new AzureCliCredentialBuilder().build();
            TokenRequestContext ctx = new TokenRequestContext()
                    .addScopes("https://management.azure.com/.default");
            // Use a short timeout – if CLI isn't logged in this should fail fast
            cliCred.getToken(ctx).block(Duration.ofSeconds(10));
            System.out.println("[AzureAuth] Azure CLI credential succeeded");
            log.info("Using AzureCliCredential for Azure authentication");
            return cliCred;
        } catch (Exception e) {
            System.out.println("[AzureAuth] Azure CLI credential failed: " + e.getMessage());
            log.info("AzureCliCredential not available: {}", e.getMessage());
        }

        // Fall back to interactive browser login
        System.out.println("[AzureAuth] Falling back to interactive browser login...");
        log.info("Falling back to InteractiveBrowserCredential");
        TokenCredential browserCred = new InteractiveBrowserCredentialBuilder()
                .redirectUrl("http://localhost:8400")
                .build();
        // Eagerly trigger the browser flow so the user logs in now
        try {
            System.out.println("[AzureAuth] Opening browser for Azure login...");
            TokenRequestContext ctx = new TokenRequestContext()
                    .addScopes("https://management.azure.com/.default");
            browserCred.getToken(ctx).block(Duration.ofMinutes(5));
            System.out.println("[AzureAuth] Interactive browser login succeeded");
            log.info("InteractiveBrowserCredential login succeeded");
        } catch (Exception e) {
            System.out.println("[AzureAuth] Interactive browser login failed: " + e.getMessage());
            log.error("InteractiveBrowserCredential login failed", e);
            throw new RuntimeException("Azure authentication failed. "
                    + "Please run 'az login' in a terminal or complete browser login.", e);
        }
        return browserCred;
    }
}
