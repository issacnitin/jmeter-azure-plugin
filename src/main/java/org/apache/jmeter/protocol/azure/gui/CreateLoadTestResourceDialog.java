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
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import org.apache.jmeter.protocol.azure.service.AzureLoadTestingClient;
import org.apache.jmeter.protocol.azure.service.LoadTestResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modal dialog that lets the user create a new Azure Load Testing resource
 * by providing a resource name and selecting an Azure region.
 * <p>
 * A resource group with the same name will be created in the user's
 * selected subscription, and the Load Testing resource is placed inside it.
 * </p>
 */
public class CreateLoadTestResourceDialog extends JDialog {
    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(CreateLoadTestResourceDialog.class);

    private final AzureLoadTestingClient client;
    private final String subscriptionId;

    private final JTextField resourceNameField = new JTextField(25);
    private final DefaultComboBoxModel<RegionItem> regionModel = new DefaultComboBoxModel<>();
    private final JComboBox<RegionItem> regionCombo = new JComboBox<>(regionModel);
    private final JLabel statusLabel = new JLabel(" ");
    private final JButton createButton = new JButton("Create");
    private final JButton cancelButton = new JButton("Cancel");

    /** The resource that was created, or {@code null} if the user cancelled. */
    private LoadTestResource createdResource;

    public CreateLoadTestResourceDialog(JDialog owner, AzureLoadTestingClient client,
                                        String subscriptionId) {
        super(owner, "Create New Load Testing Resource", true);
        this.client = client;
        this.subscriptionId = subscriptionId;

        initUI();
        setSize(new Dimension(500, 300));
        setLocationRelativeTo(owner);
    }

    /**
     * Returns the newly created resource, or {@code null} if the dialog
     * was cancelled.
     */
    public LoadTestResource getCreatedResource() {
        return createdResource;
    }

    private void initUI() {
        setLayout(new BorderLayout(10, 10));

        // --- Header ---
        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 5, 10));
        JLabel titleLabel = new JLabel("Create a new Azure Load Testing resource");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
        headerPanel.add(titleLabel, BorderLayout.NORTH);

        JLabel infoLabel = new JLabel(
                "<html>A resource group with the same name will be created in the selected subscription.</html>");
        infoLabel.setFont(infoLabel.getFont().deriveFont(Font.PLAIN, 11f));
        headerPanel.add(infoLabel, BorderLayout.SOUTH);
        add(headerPanel, BorderLayout.NORTH);

        // --- Form ---
        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6, 4, 6, 4);
        gbc.anchor = GridBagConstraints.WEST;

        // Resource name
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.fill = GridBagConstraints.NONE;
        formPanel.add(new JLabel("Resource Name:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        formPanel.add(resourceNameField, gbc);

        // Region dropdown
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.weightx = 0;
        formPanel.add(new JLabel("Region:"), gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        populateRegions();
        formPanel.add(regionCombo, gbc);

        add(formPanel, BorderLayout.CENTER);

        // --- Bottom: status + buttons ---
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC, 11f));
        bottomPanel.add(statusLabel, BorderLayout.NORTH);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        createButton.addActionListener(e -> doCreate());
        cancelButton.addActionListener(e -> dispose());
        buttonPanel.add(createButton);
        buttonPanel.add(cancelButton);
        bottomPanel.add(buttonPanel, BorderLayout.SOUTH);

        add(bottomPanel, BorderLayout.SOUTH);
    }

    private void populateRegions() {
        List<String[]> regions = AzureLoadTestingClient.getAvailableRegions();
        for (String[] r : regions) {
            regionModel.addElement(new RegionItem(r[0], r[1]));
        }
        // Default to East US
        regionCombo.setSelectedIndex(0);
    }

    private void doCreate() {
        String name = resourceNameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a resource name.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Basic name validation (Azure resource names: alphanumeric, hyphens, 3-64 chars)
        if (!name.matches("[a-zA-Z0-9][a-zA-Z0-9-]{1,62}[a-zA-Z0-9]?")) {
            JOptionPane.showMessageDialog(this,
                    "Resource name must be 3-64 characters, start/end with alphanumeric,\n"
                            + "and contain only alphanumeric characters and hyphens.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        RegionItem selectedRegion = (RegionItem) regionCombo.getSelectedItem();
        if (selectedRegion == null) {
            JOptionPane.showMessageDialog(this, "Please select a region.",
                    "Validation Error", JOptionPane.WARNING_MESSAGE);
            return;
        }

        statusLabel.setText("Creating resource group and load testing resource...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        createButton.setEnabled(false);
        cancelButton.setEnabled(false);

        String region = selectedRegion.getRegionName();
        new SwingWorker<LoadTestResource, Void>() {
            @Override
            protected LoadTestResource doInBackground() throws Exception {
                return client.createLoadTestResource(subscriptionId, name, region);
            }

            @Override
            protected void done() {
                setCursor(Cursor.getDefaultCursor());
                cancelButton.setEnabled(true);
                try {
                    createdResource = get();
                    statusLabel.setText("Resource '" + createdResource.getName() + "' created successfully!");
                    JOptionPane.showMessageDialog(CreateLoadTestResourceDialog.this,
                            "Load Testing resource '" + createdResource.getName()
                                    + "' created successfully in region '" + createdResource.getLocation() + "'.",
                            "Resource Created",
                            JOptionPane.INFORMATION_MESSAGE);
                    dispose();
                } catch (Exception ex) {
                    log.error("Failed to create load test resource", ex);
                    createButton.setEnabled(true);
                    String msg = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
                    statusLabel.setText("Error: " + msg);
                    JOptionPane.showMessageDialog(CreateLoadTestResourceDialog.this,
                            "Failed to create resource:\n" + msg,
                            "Azure Load Testing Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    /**
     * Simple wrapper to display region display-names in the combo box
     * while preserving the internal region name.
     */
    static final class RegionItem {
        private final String displayName;
        private final String regionName;

        RegionItem(String displayName, String regionName) {
            this.displayName = displayName;
            this.regionName = regionName;
        }

        String getDisplayName() {
            return displayName;
        }

        String getRegionName() {
            return regionName;
        }

        @Override
        public String toString() {
            return displayName + " (" + regionName + ")";
        }
    }
}
