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

package org.apache.jmeter.protocol.azure.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.Scene;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dialog that renders the Azure Portal test run page inside an embedded
 * JavaFX {@link WebView}, displayed within a JMeter Swing window.
 */
class LoadTestRunViewerDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(LoadTestRunViewerDialog.class);

    private JFXPanel jfxPanel;

    LoadTestRunViewerDialog(Frame owner, String testRunId, String resourceName, String portalUrl) {
        super(owner, "Load Test Run - " + testRunId, false); // non-modal
        initUI(testRunId, resourceName, portalUrl);
        setSize(new Dimension(1200, 800));
        setLocationRelativeTo(owner);
    }

    private void initUI(String testRunId, String resourceName, String portalUrl) {
        setLayout(new BorderLayout(5, 5));

        // --- Header ---
        JPanel headerPanel = new JPanel(new BorderLayout(5, 2));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(8, 10, 4, 10));

        JLabel titleLabel = new JLabel("Azure Load Test Run: " + testRunId);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 13f));
        headerPanel.add(titleLabel, BorderLayout.WEST);

        JLabel resourceLabel = new JLabel("Resource: " + resourceName);
        resourceLabel.setFont(resourceLabel.getFont().deriveFont(Font.PLAIN, 11f));
        headerPanel.add(resourceLabel, BorderLayout.EAST);

        add(headerPanel, BorderLayout.NORTH);

        // --- JavaFX WebView panel ---
        jfxPanel = new JFXPanel();
        add(jfxPanel, BorderLayout.CENTER);

        // Initialize the JavaFX WebView on the FX thread
        String urlToLoad = (portalUrl != null && !portalUrl.isBlank())
                ? portalUrl
                : "https://portal.azure.com/#view/Microsoft_Azure_CloudNativeTesting/NewOverviewBlade";

        System.out.println("[AzureLoadTest] Loading URL in embedded WebView: " + urlToLoad);
        log.info("Loading URL in embedded WebView: {}", urlToLoad);

        Platform.startup(() -> {
            // Keep JavaFX alive after this dialog is closed so it can be reused
            Platform.setImplicitExit(false);
            createWebView(urlToLoad);
        });

        // --- Bottom buttons ---
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 6));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 10, 6, 10));

        JButton reloadButton = new JButton("Reload");
        reloadButton.addActionListener(e -> Platform.runLater(() -> {
            jfxPanel.getScene().lookup("#webview");
            // Re-load via the WebEngine
            WebView wv = (WebView) jfxPanel.getScene().getRoot();
            wv.getEngine().reload();
        }));
        buttonPanel.add(reloadButton);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        buttonPanel.add(closeButton);

        add(buttonPanel, BorderLayout.SOUTH);
    }

    private void createWebView(String url) {
        Platform.runLater(() -> {
            try {
                WebView webView = new WebView();
                WebEngine engine = webView.getEngine();

                // Enable JavaScript
                engine.setJavaScriptEnabled(true);

                // Log loading progress
                engine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    System.out.println("[AzureLoadTest] WebView state: " + newState
                            + " - URL: " + engine.getLocation());
                    log.info("WebView state: {} - URL: {}", newState, engine.getLocation());
                });

                engine.getLoadWorker().exceptionProperty().addListener((obs, oldEx, newEx) -> {
                    if (newEx != null) {
                        System.out.println("[AzureLoadTest] WebView error: " + newEx.getMessage());
                        log.error("WebView loading error", newEx);
                    }
                });

                Scene scene = new Scene(webView);
                jfxPanel.setScene(scene);

                engine.load(url);
            } catch (Exception e) {
                System.out.println("[AzureLoadTest] Failed to create WebView: " + e.getMessage());
                log.error("Failed to create WebView", e);
                // Show error in the Swing UI
                SwingUtilities.invokeLater(() -> {
                    JLabel errorLabel = new JLabel(
                            "<html><center><br/><b>Failed to load embedded browser</b><br/>"
                            + e.getMessage() + "</center></html>");
                    errorLabel.setHorizontalAlignment(JLabel.CENTER);
                    remove(jfxPanel);
                    add(errorLabel, BorderLayout.CENTER);
                    revalidate();
                });
            }
        });
    }
}
