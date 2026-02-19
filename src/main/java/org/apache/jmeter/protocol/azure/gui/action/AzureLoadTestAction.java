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

package org.apache.jmeter.protocol.azure.gui.action;

import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.MenuElement;

import com.google.auto.service.AutoService;

import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.action.AbstractAction;
import org.apache.jmeter.gui.action.ActionNames;
import org.apache.jmeter.gui.action.ActionRouter;
import org.apache.jmeter.gui.action.Command;
import org.apache.jmeter.gui.plugin.MenuCreator;
import org.apache.jmeter.protocol.azure.gui.AzureLoadTestDialog;
import org.apache.jmeter.protocol.azure.service.AzureAuthService;
import org.apache.jmeter.protocol.azure.service.AzureLoadTestingClient;

import com.azure.core.credential.TokenCredential;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JMeter action that adds a "Run on Azure Load Testing" item to the Tools menu.
 * <p>
 * When triggered, it authenticates with Azure, lists available Azure Load
 * Testing resources, and allows the user to trigger a load test using the
 * currently opened JMX file.
 * </p>
 */
@AutoService({Command.class, MenuCreator.class})
public class AzureLoadTestAction extends AbstractAction implements MenuCreator {
    private static final Logger log = LoggerFactory.getLogger(AzureLoadTestAction.class);

    /** Action command string for this action. */
    public static final String AZURE_LOAD_TEST = "azure_load_test";

    private static final Set<String> commands = new HashSet<>();
    static {
        commands.add(AZURE_LOAD_TEST);
    }

    @Override
    public Set<String> getActionNames() {
        return commands;
    }

    @Override
    public void doAction(ActionEvent e) {
        log.info("Azure Load Testing action triggered");

        // Get the current JMX file path
        GuiPackage guiPackage = GuiPackage.getInstance();
        String jmxFilePath = guiPackage.getTestPlanFile();

        if (jmxFilePath == null || jmxFilePath.isBlank()) {
            // Prompt user to save first
            int result = JOptionPane.showConfirmDialog(
                    guiPackage.getMainFrame(),
                    "The test plan must be saved before running on Azure Load Testing.\n"
                            + "Would you like to save it now?",
                    "Save Test Plan",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.QUESTION_MESSAGE);
            if (result == JOptionPane.YES_OPTION) {
                ActionRouter.getInstance().doActionNow(
                        new ActionEvent(e.getSource(), e.getID(), ActionNames.SAVE));
                jmxFilePath = guiPackage.getTestPlanFile();
            }
            if (jmxFilePath == null || jmxFilePath.isBlank()) {
                JOptionPane.showMessageDialog(guiPackage.getMainFrame(),
                        "Cannot run on Azure Load Testing without a saved JMX file.",
                        "Azure Load Testing",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
        }

        // Authenticate with Azure
        TokenCredential credential;
        try {
            credential = AzureAuthService.getCredential();
        } catch (Exception ex) {
            log.error("Azure authentication failed", ex);
            JOptionPane.showMessageDialog(guiPackage.getMainFrame(),
                    "Azure authentication failed:\n" + ex.getMessage(),
                    "Azure Authentication Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Open the resource selection dialog
        AzureLoadTestingClient client = new AzureLoadTestingClient(credential);
        Frame mainFrame = guiPackage.getMainFrame();
        AzureLoadTestDialog dialog = new AzureLoadTestDialog(mainFrame, client, jmxFilePath);
        dialog.setVisible(true);
    }

    // ---- MenuCreator implementation ----

    @Override
    public JMenuItem[] getMenuItemsAtLocation(MENU_LOCATION location) {
        if (location == MENU_LOCATION.RUN) {
            JMenuItem menuItem = new JMenuItem("Run on Azure Load Testing", KeyEvent.VK_UNDEFINED);
            menuItem.setName(AZURE_LOAD_TEST);
            menuItem.setActionCommand(AZURE_LOAD_TEST);
            menuItem.setToolTipText("Upload the current JMX file and trigger a load test on Azure Load Testing");
            menuItem.addActionListener(ActionRouter.getInstance());
            return new JMenuItem[]{menuItem};
        }
        return new JMenuItem[0];
    }

    @Override
    public JMenu[] getTopLevelMenus() {
        return new JMenu[0];
    }

    @Override
    public boolean localeChanged(MenuElement menu) {
        return false;
    }

    @Override
    public void localeChanged() {
        // NOOP
    }
}
