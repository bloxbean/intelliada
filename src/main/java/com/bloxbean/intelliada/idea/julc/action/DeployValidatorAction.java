package com.bloxbean.intelliada.idea.julc.action;

import com.bloxbean.intelliada.idea.julc.module.pkg.JulcTomlService;
import com.bloxbean.intelliada.idea.julc.service.BlueprintLoadService;
import com.bloxbean.intelliada.idea.julc.service.PlutusBlueprint;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.util.IdeaUtil;
import com.bloxbean.cardano.client.address.AddressProvider;
import com.bloxbean.cardano.client.common.model.Networks;
import com.bloxbean.cardano.client.plutus.spec.PlutusV3Script;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * Action to deploy a compiled julc validator to the devnet.
 * Loads validators from the CIP-57 blueprint and shows a dialog
 * for selecting the validator, configuring datum, and submitting.
 */
public class DeployValidatorAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            e.getPresentation().setVisible(false);
            return;
        }
        JulcTomlService tomlService = JulcTomlService.getInstance(project);
        boolean isJulc = tomlService != null && tomlService.isJulcProject();
        e.getPresentation().setVisible(isJulc);
        e.getPresentation().setEnabled(isJulc);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) return;

        PlutusBlueprint blueprint = BlueprintLoadService.getInstance(project).loadBlueprint();
        if (blueprint == null || blueprint.getValidators() == null || blueprint.getValidators().isEmpty()) {
            Messages.showWarningDialog(project,
                    "No compiled validators found. Run 'julc build' or './gradlew build' first.",
                    "Deploy Validator");
            return;
        }

        DeployDialog dialog = new DeployDialog(project, blueprint.getValidators());
        if (dialog.showAndGet()) {
            PlutusBlueprint.ValidatorInfo selected = dialog.getSelectedValidator();
            if (selected == null) return;

            try {
                PlutusV3Script script = PlutusV3Script.builder()
                        .cborHex(selected.getCompiledCode())
                        .build();

                String scriptAddress = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();

                CardanoConsole console = CardanoConsole.getConsole(project);
                console.clearAndshow();
                console.showInfoMessage("[Deploy] Validator: " + selected.getTitle());
                console.showInfoMessage("[Deploy] Script Hash: " + selected.getHash());
                console.showInfoMessage("[Deploy] Script Address: " + scriptAddress);
                console.showInfoMessage("[Deploy] Size: " + selected.getSizeBytes() + " bytes");
                console.showSuccessMessage("Script address computed. Use the transaction panel to lock funds at this address.");

                IdeaUtil.showNotification(project, "Deploy Validator",
                        "Script address: " + scriptAddress, NotificationType.INFORMATION, null);

            } catch (Exception ex) {
                CardanoConsole console = CardanoConsole.getConsole(project);
                console.showErrorMessage("Failed to compute script address: " + ex.getMessage());
            }
        }
    }

    @NotNull
    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    /**
     * Dialog for selecting a validator to deploy.
     */
    private static class DeployDialog extends DialogWrapper {
        private final List<PlutusBlueprint.ValidatorInfo> validators;
        private JComboBox<PlutusBlueprint.ValidatorInfo> validatorCombo;
        private JLabel hashLabel;
        private JLabel sizeLabel;
        private JLabel addressLabel;

        protected DeployDialog(@Nullable Project project, List<PlutusBlueprint.ValidatorInfo> validators) {
            super(project);
            this.validators = validators;
            init();
            setTitle("Deploy Validator");
        }

        @Override
        protected @Nullable JComponent createCenterPanel() {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setPreferredSize(new Dimension(600, 250));
            GridBagConstraints c = new GridBagConstraints();
            c.anchor = GridBagConstraints.WEST;
            c.insets = new Insets(4, 4, 4, 4);

            // Validator selector
            c.gridx = 0; c.gridy = 0;
            panel.add(new JLabel("Validator:"), c);

            validatorCombo = new JComboBox<>();
            for (PlutusBlueprint.ValidatorInfo v : validators) {
                validatorCombo.addItem(v);
            }
            validatorCombo.setRenderer(new DefaultListCellRenderer() {
                @Override
                public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean sel, boolean focus) {
                    super.getListCellRendererComponent(list, value, index, sel, focus);
                    if (value instanceof PlutusBlueprint.ValidatorInfo v) {
                        setText(v.getTitle() + " (" + v.getSizeBytes() + " bytes)");
                    }
                    return this;
                }
            });
            validatorCombo.addActionListener(e -> updateDetails());
            c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL; c.weightx = 1.0;
            panel.add(validatorCombo, c);

            // Script hash
            c.gridy = 1; c.gridx = 0; c.fill = GridBagConstraints.NONE; c.weightx = 0;
            panel.add(new JLabel("Script Hash:"), c);
            hashLabel = new JLabel("-");
            hashLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            panel.add(hashLabel, c);

            // Size
            c.gridy = 2; c.gridx = 0; c.fill = GridBagConstraints.NONE;
            panel.add(new JLabel("Size:"), c);
            sizeLabel = new JLabel("-");
            c.gridx = 1;
            panel.add(sizeLabel, c);

            // Script address
            c.gridy = 3; c.gridx = 0;
            panel.add(new JLabel("Script Address:"), c);
            addressLabel = new JLabel("-");
            addressLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 10));
            c.gridx = 1; c.fill = GridBagConstraints.HORIZONTAL;
            panel.add(addressLabel, c);

            // Parameterized notice
            c.gridy = 4; c.gridx = 0; c.gridwidth = 2;
            JLabel notice = new JLabel("<html><i>Note: For parameterized validators (@Param), apply parameters before deployment.</i></html>");
            notice.setForeground(Color.GRAY);
            panel.add(notice, c);

            updateDetails();
            return panel;
        }

        private void updateDetails() {
            PlutusBlueprint.ValidatorInfo v = getSelectedValidator();
            if (v == null) return;

            hashLabel.setText(v.getHash());
            sizeLabel.setText(v.getSizeBytes() + " bytes");

            try {
                PlutusV3Script script = PlutusV3Script.builder()
                        .cborHex(v.getCompiledCode())
                        .build();
                String addr = AddressProvider.getEntAddress(script, Networks.testnet()).toBech32();
                addressLabel.setText(addr);
            } catch (Exception ex) {
                addressLabel.setText("(unable to compute)");
            }
        }

        public PlutusBlueprint.ValidatorInfo getSelectedValidator() {
            return (PlutusBlueprint.ValidatorInfo) validatorCombo.getSelectedItem();
        }
    }
}
