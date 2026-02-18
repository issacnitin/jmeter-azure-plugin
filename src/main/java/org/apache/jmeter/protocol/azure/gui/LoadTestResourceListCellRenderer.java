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
import java.awt.Component;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListCellRenderer;

import org.apache.jmeter.protocol.azure.service.LoadTestResource;

/**
 * Custom list cell renderer for {@link LoadTestResource} items in the
 * resource selection dialog.
 */
class LoadTestResourceListCellRenderer extends JPanel implements ListCellRenderer<LoadTestResource> {
    private static final long serialVersionUID = 1L;

    private final JLabel nameLabel = new JLabel();
    private final JLabel detailLabel = new JLabel();

    LoadTestResourceListCellRenderer() {
        setLayout(new BorderLayout(2, 2));
        setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        nameLabel.setFont(nameLabel.getFont().deriveFont(Font.BOLD));
        detailLabel.setFont(detailLabel.getFont().deriveFont(Font.PLAIN, 11f));
        add(nameLabel, BorderLayout.NORTH);
        add(detailLabel, BorderLayout.SOUTH);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends LoadTestResource> list,
                                                   LoadTestResource value, int index,
                                                   boolean isSelected, boolean cellHasFocus) {
        if (value != null) {
            nameLabel.setText(value.getName());
            detailLabel.setText("Resource Group: " + value.getResourceGroup()
                    + "  |  Location: " + value.getLocation()
                    + "  |  Subscription: " + value.getSubscriptionId());
        } else {
            nameLabel.setText("");
            detailLabel.setText("");
        }

        if (isSelected) {
            setBackground(list.getSelectionBackground());
            setForeground(list.getSelectionForeground());
            nameLabel.setForeground(list.getSelectionForeground());
            detailLabel.setForeground(list.getSelectionForeground());
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
            nameLabel.setForeground(list.getForeground());
            detailLabel.setForeground(list.getForeground());
        }

        setOpaque(true);
        return this;
    }
}
