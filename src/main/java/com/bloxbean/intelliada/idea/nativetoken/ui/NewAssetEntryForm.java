package com.bloxbean.intelliada.idea.nativetoken.ui;

import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.transaction.spec.MultiAsset;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptPubkey;
import com.bloxbean.intelliada.idea.nativetoken.ui.model.AssetModel;
import com.bloxbean.intelliada.idea.nativetoken.ui.model.SecretKeyModel;
import com.bloxbean.intelliada.idea.scripts.service.ScriptChooser;
import com.bloxbean.intelliada.idea.scripts.service.ScriptInfo;
import com.bloxbean.intelliada.idea.scripts.util.ScriptParser;
import com.bloxbean.intelliada.idea.toolwindow.CardanoConsole;
import com.bloxbean.intelliada.idea.util.MessageUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;

public class NewAssetEntryForm {
    private JPanel panel1;
    private JTextField policyScriptTf;
    private JButton internalScriptBrowseBtn;
    private JButton externalScriptBrowseBtn;
    private JTextField policyIdTf;
    private JTextField assetNameTf;
    private JList policyKeysList;
    private JButton addPolicyKeyBtn;
    private JButton removePolicyKeyBtn;
    private JPanel mainPanel;
    private JList assetList;
    private JButton assetRemoveBtn;
    private JTextField assetQtyTf;
    private JButton addAssetBtn;
    private JLabel errorLabel;
    private CardanoConsole console;
    private Project project;
    private DefaultListModel<SecretKeyModel> policyKeysListModel;
    private DefaultListModel<AssetModel>  assetListModel;

    private NativeScript policyScript;

    public NewAssetEntryForm() {

    }

    public void initialize(Project project, CardanoConsole console) {
        this.project = project;
        this.console = console;

        initializeComponents();

        attachPolicyDetailsListener(project, console);
        attachAssetListeners();
    }

    private void initializeComponents() {
        policyKeysListModel = new DefaultListModel<SecretKeyModel>();
        policyKeysList.setModel(policyKeysListModel);
        policyKeysList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        assetListModel = new DefaultListModel<>();
        assetList.setModel(assetListModel);
        assetList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        assetQtyTf.setInputVerifier(new InputVerifier() {
            @Override
            public boolean verify(JComponent input) {
                String str = assetQtyTf.getText();
                try {
                    new BigInteger(str);
                    errorLabel.setText("");
                    return true;
                } catch (Exception e) {
                    errorLabel.setText("Invalid quantity");
                    errorLabel.setForeground(Color.RED);
                    return false;
                }
            }
        });
    }

    private void attachPolicyDetailsListener(Project project, CardanoConsole console) {
        internalScriptBrowseBtn.addActionListener(e -> {
            ScriptInfo scriptInfo = ScriptChooser.selectScript(project);
            if(scriptInfo == null)
                return;
            clearPolicyScript();
            setPolicyScript(scriptInfo.getScript(), scriptInfo.toString());

            //Set policy keys if possible (only for sig)
            if(scriptInfo.getScript() != null) {
                if (StringUtil.isEmpty(scriptInfo.getAddress())
                        && (scriptInfo.getScript() instanceof ScriptPubkey) ) {
                    SecretKey sk = scriptInfo.getSkey();
                    if (sk != null && policyKeysListModel.isEmpty()) {
                        policyKeysListModel.addElement(new SecretKeyModel(sk));
                    }
                }
            }
        });

        externalScriptBrowseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            String baseDir = project.getBasePath();
            if(baseDir != null)
                fc.setCurrentDirectory(new File(baseDir));

            fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
            fc.showDialog(mainPanel, "Select");
            File destination = fc.getSelectedFile();
            if (destination == null || !destination.exists()) {
                Messages.showErrorDialog("Invalid script file: " + destination, "External script");
                return;
            }

            clearPolicyScript();
            try {
                String scriptText = FileUtil.loadFile(destination);
                NativeScript ns = ScriptParser.parse(scriptText);
                setPolicyScript(ns, destination.getAbsolutePath());
            } catch (IOException ex) {
                console.showErrorMessage("Error loading script file", ex);
                MessageUtil.showMessage("Error loading script file : " + ex.getMessage(), "Script Loading error", true);
            } catch (Exception exception) {
                console.showErrorMessage("Error loading script file", exception);
                MessageUtil.showMessage("Error loading script file : " + exception.getMessage(), "Script Loading error", true);
            }
        });

        addPolicyKeyBtn.addActionListener(e -> {
            ScriptInfo scriptInfo = ScriptChooser.selectScript(project, true);
            if(scriptInfo == null)
                return;

            SecretKey skey = scriptInfo.getSkey();
            if(skey != null) {
                if(!policyKeysListModel.contains(skey)) {
                    policyKeysListModel.addElement(new SecretKeyModel(skey));
                }
            }
        });

        removePolicyKeyBtn.addActionListener(e -> {
            int index = policyKeysList.getSelectedIndex();
            if(index == -1)
                return;

            policyKeysListModel.remove(index);
        });
    }

    private void attachAssetListeners() {
        addAssetBtn.addActionListener(e -> {
            String name = assetNameTf.getText();
            String quantity = assetQtyTf.getText();

            BigInteger quantityBI;
            try {
                quantityBI = new BigInteger(quantity);
            } catch (Exception ex) {
                return; //If parse error
            }

            AssetModel assetModel = new AssetModel();
            assetModel.setName(name);
            assetModel.setQuantity(quantityBI);
            assetListModel.addElement(assetModel);
            assetNameTf.setText("");
            assetQtyTf.setText("");
        });

        assetRemoveBtn.addActionListener(e -> {
            int index = assetList.getSelectedIndex();
            if(index == -1)
                return;

            assetListModel.remove(index);
        });
    }

    private void setPolicyScript(NativeScript script, String path) {
        if(script == null)
            return;

        this.policyScript = script;
        if(!StringUtil.isEmpty(path)) {
            policyScriptTf.setText(path);
        }

        policyIdTf.setText("");
        if(policyScript != null) {
            try {
                policyIdTf.setText(policyScript.getPolicyId());
            } catch (CborSerializationException ex) {
                console.showErrorMessage("Error getting policy id", ex);
            }
        } else {
            policyIdTf.setText("");
        }
    }

    private void clearPolicyScript() {
        policyScript = null;
        policyScriptTf.setText("");
        policyIdTf.setText("");
    }

    private String getPolicyId() {
        return policyIdTf.getText();
    }

    private Iterator<AssetModel> getAssets() {
        return assetListModel.elements().asIterator();
    }

    public MultiAsset getMultiAsset() {
        MultiAsset multiAsset = new MultiAsset();
        multiAsset.setPolicyId(getPolicyId());
        multiAsset.setAssets(new ArrayList<>());

        Iterator<AssetModel> assetsItr = getAssets();
        while(assetsItr.hasNext()) {
            AssetModel assetModel = assetsItr.next();

            Asset asset = new Asset();
            asset.setName(assetModel.getName());
            asset.setValue(assetModel.getQuantity());
            multiAsset.getAssets().add(asset);
        }

        return multiAsset;
    }

    public java.util.List<SecretKey> getPolicyKeys() {
        Enumeration<SecretKeyModel> skeyModels = policyKeysListModel.elements();
        java.util.List<SecretKey> sks = new ArrayList<>();
        while (skeyModels.hasMoreElements()) {
            sks.add(skeyModels.nextElement().getSecretKey());
        }
        return sks;
    }

    public NativeScript getPolicyScript() {
        return policyScript;
    }

    public ValidationInfo doValidation() {
        if(getPolicyScript() == null || StringUtil.isEmpty(getPolicyId())) {
            return new ValidationInfo("Please select a valid policy script", policyScriptTf);
        }

        if(assetListModel.isEmpty()) {
            return new ValidationInfo("Please provide asset name and quantity", assetList);
        }

        return null;
    }
}
