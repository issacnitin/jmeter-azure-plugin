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
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;

import org.apache.jmeter.protocol.azure.service.AzureLoadTestingClient;
import org.apache.jmeter.protocol.azure.service.LoadTestResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modal dialog that displays Azure Load Testing resources and lets the user
 * select one to run a load test against the currently opened JMX file.
 */
public class AzureLoadTestDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(AzureLoadTestDialog.class);

    private final AzureLoadTestingClient client;
    private final String jmxFilePath;

    private final DefaultListModel<LoadTestResource> listModel = new DefaultListModel<>();
    private final JList<LoadTestResource> resourceList = new JList<>(listModel);
    private final JTextField testNameField = new JTextField(30);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton runButton = new JButton("Run Load Test");
    private final JButton refreshButton = new JButton("Refresh");
    private final JButton cancelButton = new JButton("Cancel");

    public AzureLoadTestDialog(Frame owner, AzureLoadTestingClient client, String jmxFilePath) {
        super(owner, "Azure Load Testing", true);
        this.client = client;
        this.jmxFilePath = jmxFilePath;

        initUI();
        setSize(new Dimension(600, 500));
        setLocationRelativeTo(owner);

        // Start loading resources immediately
        loadResources();
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // --- Header ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        JLabel titleLabel = new JLabel("Select an Azure Load Testing Resource");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel fileLabel = new JLabel("JMX File: " + jmxFilePath);
        fileLabel.setFont(fileLabel.getFont().deriveFont(Font.PLAIN, 11f));
        headerPanel.add(fileLabel, BorderLayout.SOUTH);
        add(headerPanel, BorderLayout.NORTH);

        // --- Resource list ---
        JPanel centerPanel = new JPanel(new BorderLayout(5, 5));
        centerPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        resourceList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        resourceList.setVisibleRowCount(10);
        resourceList.setCellRenderer(new LoadTestResourceListCellRenderer());
        JScrollPane scrollPane = new JScrollPane(resourceList);
        centerPanel.add(scrollPane, BorderLayout.CENTER);

        // Test name input
        JPanel testNamePanel = new JPanel(new BorderLayout(5, 5));
        testNamePanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        testNamePanel.add(new JLabel("Test Name:"), BorderLayout.WEST);
        String defaultTestName = jmxFilePath != null
                ? new java.io.File(jmxFilePath).getName().replace(".jmx", "") + " - Azure Load Test"
                : "JMeter Azure Load Test";
        testNameField.setText(defaultTestName);
        testNamePanel.add(testNameField, BorderLayout.CENTER);
        centerPanel.add(testNamePanel, BorderLayout.SOUTH);

        add(centerPanel, BorderLayout.CENTER);

        // --- Button panel ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        refreshButton.addActionListener(e -> loadResources());
        runButton.addActionListener(e -> triggerTest());
        cancelButton.addActionListener(e -> dispose());
        runButton.setEnabled(false);

        buttonPanel.add(refreshButton);
        buttonPanel.add(runButton);
        buttonPanel.add(cancelButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);

        // Enable run button only when a resource is selected
        resourceList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                runButton.setEnabled(resourceList.getSelectedValue() != null);
            }
        });
    }

    private void loadResources() {
        statusLabel.setText("Loading Azure Load Testing resources...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        refreshButton.setEnabled(false);
        runButton.setEnabled(false);
        listModel.clear();

        new SwingWorker<List<LoadTestResource>, Void>() {
            @Override
            protected List<LoadTestResource> doInBackground() throws Exception {
                return client.listResources();
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                refreshButton.setEnabled(true);
                try {
                    List<LoadTestResource> resources = get();
                    if (resources.isEmpty()) {
                        statusLabel.setText("No Azure Load Testing resources found. "
                                + "Make sure you have the right permissions.");
                    } else {
                        statusLabel.setText(resources.size() + " resource(s) found. Select one and click 'Run Load Test'.");
                        for (LoadTestResource r : resources) {
                            listModel.addElement(r);
                        }
                    }
                } catch (Exception ex) {
                    log.error("Failed to list Azure Load Testing resources", ex);
                    statusLabel.setText("Error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(AzureLoadTestDialog.this,
                            "Failed to list resources:\n" + ex.getMessage(),
                            "Azure Load Testing Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void triggerTest() {
        LoadTestResource selected = resourceList.getSelectedValue();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "Please select a resource first.",
                    "No Resource Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }

        String testName = testNameField.getText().trim();
        if (testName.isEmpty()) {
            testName = "JMeter Load Test";
        }

        statusLabel.setText("Triggering load test on '" + selected.getName() + "'...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        runButton.setEnabled(false);
        refreshButton.setEnabled(false);

        String finalTestName = testName;
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                return client.triggerLoadTest(selected, jmxFilePath, finalTestName);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                refreshButton.setEnabled(true);
                runButton.setEnabled(true);
                try {
                    String testRunId = get();
                    statusLabel.setText("Test run started: " + testRunId);
                    JOptionPane.showMessageDialog(AzureLoadTestDialog.this,
                            "Load test started successfully!\n\n"
                                    + "Test Run ID: " + testRunId + "\n"
                                    + "Resource: " + selected.getName() + "\n\n"
                                    + "You can monitor the test run in the Azure Portal.",
                            "Load Test Started",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    log.error("Failed to trigger load test", ex);
                    statusLabel.setText("Error: " + ex.getMessage());
                    JOptionPane.showMessageDialog(AzureLoadTestDialog.this,
                            "Failed to trigger load test:\n" + ex.getMessage(),
                            "Azure Load Testing Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }
}
