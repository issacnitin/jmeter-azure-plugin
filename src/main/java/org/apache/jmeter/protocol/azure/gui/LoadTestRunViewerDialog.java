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
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.net.URI;
import java.text.DecimalFormat;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;

import org.apache.jmeter.protocol.azure.service.AzureLoadTestingClient;
import org.apache.jmeter.protocol.azure.service.TestRunStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Native Swing dialog that polls the Azure Load Testing API for test run status
 * and displays real-time metrics in a dashboard layout.
 */
class LoadTestRunViewerDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(LoadTestRunViewerDialog.class);
    private static final DecimalFormat NUM_FMT = new DecimalFormat("#,##0");
    private static final DecimalFormat DEC_FMT = new DecimalFormat("#,##0.0");
    private static final DecimalFormat PCT_FMT = new DecimalFormat("0.00");
    private static final int POLL_INTERVAL_MS = 5_000;

    private final AzureLoadTestingClient client;
    private final String endpoint;
    private final String testRunId;

    // Status labels
    private final JLabel statusValueLabel = createValueLabel();
    private final JLabel durationValueLabel = createValueLabel();
    private final JLabel vusersValueLabel = createValueLabel();

    // Metric labels
    private final JLabel totalRequestsLabel = createValueLabel();
    private final JLabel successfulRequestsLabel = createValueLabel();
    private final JLabel failedRequestsLabel = createValueLabel();
    private final JLabel rpsLabel = createValueLabel();
    private final JLabel errorPctLabel = createValueLabel();

    // Response time labels
    private final JLabel avgRtLabel = createValueLabel();
    private final JLabel p90RtLabel = createValueLabel();
    private final JLabel p95RtLabel = createValueLabel();
    private final JLabel p99RtLabel = createValueLabel();

    private final JLabel lastUpdatedLabel = new JLabel(" ");
    private JButton cancelButton;
    private Timer pollingTimer;

    LoadTestRunViewerDialog(Frame owner, AzureLoadTestingClient client,
                            String endpoint, String testRunId, String resourceName, String portalUrl) {
        super(owner, "Load Test Run - " + testRunId, true); // modal
        this.client = client;
        this.endpoint = endpoint;
        this.testRunId = testRunId;

        initUI(testRunId, resourceName, portalUrl);
        setSize(new Dimension(750, 520));
        setLocationRelativeTo(owner);

        // Start polling
        startPolling();
    }

    private void initUI(String testRunId, String resourceName, String portalUrl) {
        setLayout(new BorderLayout(8, 8));
        getContentPane().setBackground(new Color(245, 245, 245));

        // --- Header ---
        JPanel headerPanel = new JPanel(new BorderLayout(5, 2));
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 12));
        headerPanel.setOpaque(false);

        JLabel titleLabel = new JLabel("Azure Load Test Dashboard");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        JPanel infoPanel = new JPanel(new GridLayout(2, 1, 0, 2));
        infoPanel.setOpaque(false);
        infoPanel.add(new JLabel("Test Run: " + testRunId + "    |    Resource: " + resourceName));
        if (portalUrl != null && !portalUrl.isBlank()) {
            JLabel linkLabel = new JLabel(
                    "<html>Portal URL: <a href=\"\">"
                            + escapeHtml(portalUrl) + "</a></html>");
            linkLabel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            linkLabel.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    try {
                        Desktop.getDesktop().browse(new URI(portalUrl));
                    } catch (Exception ex) {
                        log.warn("Failed to open portal URL", ex);
                    }
                }
            });
            infoPanel.add(linkLabel);
        }
        headerPanel.add(infoPanel, BorderLayout.CENTER);

        add(headerPanel, BorderLayout.NORTH);

        // --- Main dashboard ---
        JPanel dashPanel = new JPanel(new GridLayout(3, 1, 8, 8));
        dashPanel.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        dashPanel.setOpaque(false);

        // Row 1: Status
        JPanel statusPanel = createSection("Test Run Status");
        statusPanel.setLayout(new GridLayout(1, 3, 12, 0));
        statusPanel.add(createMetricCard("Status", statusValueLabel));
        statusPanel.add(createMetricCard("Duration", durationValueLabel));
        statusPanel.add(createMetricCard("Virtual Users", vusersValueLabel));
        dashPanel.add(statusPanel);

        // Row 2: Request metrics
        JPanel requestPanel = createSection("Request Metrics");
        requestPanel.setLayout(new GridLayout(1, 5, 10, 0));
        requestPanel.add(createMetricCard("Total", totalRequestsLabel));
        requestPanel.add(createMetricCard("Successful", successfulRequestsLabel));
        requestPanel.add(createMetricCard("Failed", failedRequestsLabel));
        requestPanel.add(createMetricCard("Req/s", rpsLabel));
        requestPanel.add(createMetricCard("Error %", errorPctLabel));
        dashPanel.add(requestPanel);

        // Row 3: Response times
        JPanel rtPanel = createSection("Response Times (ms)");
        rtPanel.setLayout(new GridLayout(1, 4, 12, 0));
        rtPanel.add(createMetricCard("Average", avgRtLabel));
        rtPanel.add(createMetricCard("p90", p90RtLabel));
        rtPanel.add(createMetricCard("p95", p95RtLabel));
        rtPanel.add(createMetricCard("p99", p99RtLabel));
        dashPanel.add(rtPanel);

        add(dashPanel, BorderLayout.CENTER);

        // --- Footer ---
        JPanel footerPanel = new JPanel(new BorderLayout());
        footerPanel.setBorder(BorderFactory.createEmptyBorder(2, 12, 8, 12));
        footerPanel.setOpaque(false);

        lastUpdatedLabel.setFont(lastUpdatedLabel.getFont().deriveFont(Font.ITALIC, 10f));
        lastUpdatedLabel.setForeground(Color.GRAY);
        footerPanel.add(lastUpdatedLabel, BorderLayout.WEST);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setOpaque(false);

        cancelButton = new JButton("Cancel Test");
        cancelButton.setForeground(Color.RED);
        cancelButton.addActionListener(e -> cancelTest());
        buttonPanel.add(cancelButton);

        JButton refreshButton = new JButton("Refresh Now");
        refreshButton.addActionListener(e -> fetchAndUpdate());
        buttonPanel.add(refreshButton);

        JButton closeButton = new JButton("Close");
        closeButton.addActionListener(e -> {
            stopPolling();
            dispose();
        });
        buttonPanel.add(closeButton);

        footerPanel.add(buttonPanel, BorderLayout.EAST);
        add(footerPanel, BorderLayout.SOUTH);

        // Initialize all values to loading state
        setAllLabels("...");
    }

    private void startPolling() {
        // Fetch immediately
        fetchAndUpdate();

        // Then poll periodically
        pollingTimer = new Timer(POLL_INTERVAL_MS, e -> fetchAndUpdate());
        pollingTimer.setRepeats(true);
        pollingTimer.start();
    }

    private void stopPolling() {
        if (pollingTimer != null) {
            pollingTimer.stop();
            pollingTimer = null;
        }
    }

    private void fetchAndUpdate() {
        new SwingWorker<TestRunStatus, Void>() {
            @Override
            protected TestRunStatus doInBackground() {
                return client.getTestRunStatus(endpoint, testRunId);
            }

            @Override
            protected void done() {
                try {
                    TestRunStatus s = get();
                    updateUI(s);

                    // Stop polling once the test is done
                    if (!s.isRunning()) {
                        stopPolling();
                        cancelButton.setEnabled(false);
                        lastUpdatedLabel.setText("Test completed. Final results shown.");
                    } else {
                        lastUpdatedLabel.setText("Auto-refreshing every "
                                + (POLL_INTERVAL_MS / 1000) + "s  |  Last update: "
                                + java.time.LocalTime.now().withNano(0));
                    }
                } catch (Exception ex) {
                    log.error("Error polling test run status", ex);
                    lastUpdatedLabel.setText("Error: " + ex.getMessage());
                }
            }
        }.execute();
    }

    private void updateUI(TestRunStatus s) {
        // Status section
        statusValueLabel.setText(formatStatus(s.getStatus()));
        statusValueLabel.setForeground(statusColor(s.getStatus()));

        if (s.getDurationMs() > 0) {
            long secs = s.getDurationMs() / 1000;
            durationValueLabel.setText(String.format("%d:%02d:%02d", secs / 3600, (secs % 3600) / 60, secs % 60));
        } else if (!s.getStartDateTime().isEmpty()) {
            durationValueLabel.setText("In progress...");
        } else {
            durationValueLabel.setText("--");
        }

        vusersValueLabel.setText(s.getVirtualUsers() > 0 ? NUM_FMT.format(s.getVirtualUsers()) : "--");

        // Request metrics
        totalRequestsLabel.setText(s.getTotalRequests() > 0 ? NUM_FMT.format(s.getTotalRequests()) : "--");
        successfulRequestsLabel.setText(s.getSuccessfulRequests() > 0 ? NUM_FMT.format(s.getSuccessfulRequests()) : "--");
        successfulRequestsLabel.setForeground(s.getSuccessfulRequests() > 0 ? new Color(0, 128, 0) : Color.DARK_GRAY);
        failedRequestsLabel.setText(s.getFailedRequests() > 0 ? NUM_FMT.format(s.getFailedRequests()) : "--");
        failedRequestsLabel.setForeground(s.getFailedRequests() > 0 ? Color.RED : Color.DARK_GRAY);
        rpsLabel.setText(s.getRequestsPerSecond() > 0 ? DEC_FMT.format(s.getRequestsPerSecond()) : "--");
        errorPctLabel.setText(s.getErrorPercentage() > 0 ? PCT_FMT.format(s.getErrorPercentage()) + "%" : "--");
        errorPctLabel.setForeground(s.getErrorPercentage() > 5 ? Color.RED : Color.DARK_GRAY);

        // Response times
        avgRtLabel.setText(s.getAvgResponseTimeMs() > 0 ? DEC_FMT.format(s.getAvgResponseTimeMs()) : "--");
        p90RtLabel.setText(s.getP90ResponseTimeMs() > 0 ? DEC_FMT.format(s.getP90ResponseTimeMs()) : "--");
        p95RtLabel.setText(s.getP95ResponseTimeMs() > 0 ? DEC_FMT.format(s.getP95ResponseTimeMs()) : "--");
        p99RtLabel.setText(s.getP99ResponseTimeMs() > 0 ? DEC_FMT.format(s.getP99ResponseTimeMs()) : "--");
    }

    private void setAllLabels(String text) {
        statusValueLabel.setText(text);
        durationValueLabel.setText(text);
        vusersValueLabel.setText(text);
        totalRequestsLabel.setText(text);
        successfulRequestsLabel.setText(text);
        failedRequestsLabel.setText(text);
        rpsLabel.setText(text);
        errorPctLabel.setText(text);
        avgRtLabel.setText(text);
        p90RtLabel.setText(text);
        p95RtLabel.setText(text);
        p99RtLabel.setText(text);
    }

    private static String formatStatus(String status) {
        if (status == null || status.isEmpty()) return "UNKNOWN";
        return status.toUpperCase();
    }

    private static Color statusColor(String status) {
        if (status == null) return Color.DARK_GRAY;
        return switch (status.toUpperCase()) {
            case "EXECUTING" -> new Color(0, 100, 200);
            case "DONE" -> new Color(0, 128, 0);
            case "FAILED", "VALIDATION_FAILURE" -> Color.RED;
            case "CANCELLED", "CANCELLING" -> new Color(180, 100, 0);
            case "PROVISIONING", "PROVISIONED", "CONFIGURING", "CONFIGURED" -> new Color(128, 128, 0);
            default -> Color.DARK_GRAY;
        };
    }

    private static JPanel createSection(String title) {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(200, 200, 200)),
                title);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 12f));
        panel.setBorder(border);
        return panel;
    }

    private static JPanel createMetricCard(String label, JLabel valueLabel) {
        JPanel card = new JPanel(new BorderLayout(0, 2));
        card.setOpaque(false);
        card.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));

        JLabel nameLabel = new JLabel(label, SwingConstants.CENTER);
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.PLAIN, 11f));
        nameLabel.setForeground(Color.GRAY);
        card.add(nameLabel, BorderLayout.NORTH);

        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    private static JLabel createValueLabel() {
        JLabel label = new JLabel("--", SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
        label.setForeground(Color.DARK_GRAY);
        return label;
    }

    private void cancelTest() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to cancel this test run?",
                "Cancel Test Run",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        cancelButton.setEnabled(false);
        cancelButton.setText("Cancelling...");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() {
                client.cancelTestRun(endpoint, testRunId);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    lastUpdatedLabel.setText("Cancel request sent. Waiting for status update...");
                    fetchAndUpdate();
                } catch (Exception ex) {
                    log.error("Failed to cancel test run", ex);
                    cancelButton.setEnabled(true);
                    cancelButton.setText("Cancel Test");
                    JOptionPane.showMessageDialog(LoadTestRunViewerDialog.this,
                            "Failed to cancel test run:\n" + ex.getMessage(),
                            "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void dispose() {
        stopPolling();
        super.dispose();
    }
}
