package com.bloxbean.intelliada.idea.julc.repl;

import com.bloxbean.intelliada.idea.julc.configuration.JulcConfigurationHelperService;
import com.bloxbean.intelliada.idea.julc.configuration.JulcSDK;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.GeneralCommandLine;
import com.intellij.execution.process.OSProcessHandler;
import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Interactive julc REPL panel.
 * Manages a julc repl subprocess and provides input/output UI.
 */
public class JulcReplPanel {
    private static final Logger LOG = Logger.getInstance(JulcReplPanel.class);
    private static final String ANSI_ESCAPE_REGEX = "\u001B\\[[;\\d]*[A-Za-z]";

    private final Project project;
    private JPanel mainPanel;
    private JTextArea outputArea;
    private JTextField inputField;
    private JButton sendBtn;
    private JButton restartBtn;

    private OSProcessHandler processHandler;
    private OutputStream processStdin;
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;

    public JulcReplPanel(Project project) {
        this.project = project;
        initComponents();
        startRepl();
    }

    public JPanel getMainPanel() {
        return mainPanel;
    }

    private void initComponents() {
        mainPanel = new JPanel(new BorderLayout(2, 2));

        // Output area
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        outputArea.setBackground(new Color(30, 30, 30));
        outputArea.setForeground(new Color(200, 200, 200));
        outputArea.setCaretColor(Color.WHITE);
        JScrollPane scrollPane = new JScrollPane(outputArea);
        mainPanel.add(scrollPane, BorderLayout.CENTER);

        // Input panel
        JPanel inputPanel = new JPanel(new BorderLayout(2, 0));

        JLabel promptLabel = new JLabel("julc> ");
        promptLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
        inputPanel.add(promptLabel, BorderLayout.WEST);

        inputField = new JTextField();
        inputField.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        inputField.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                    sendInput();
                } else if (e.getKeyCode() == KeyEvent.VK_UP) {
                    navigateHistory(-1);
                } else if (e.getKeyCode() == KeyEvent.VK_DOWN) {
                    navigateHistory(1);
                }
            }
        });
        inputPanel.add(inputField, BorderLayout.CENTER);

        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        sendBtn = new JButton("Send");
        sendBtn.addActionListener(e -> sendInput());
        btnPanel.add(sendBtn);

        restartBtn = new JButton("Restart");
        restartBtn.addActionListener(e -> restartRepl());
        btnPanel.add(restartBtn);

        inputPanel.add(btnPanel, BorderLayout.EAST);
        mainPanel.add(inputPanel, BorderLayout.SOUTH);
    }

    private void startRepl() {
        JulcSDK sdk = JulcConfigurationHelperService.getCompilerLocalSDK(project);
        if (sdk == null) {
            appendOutput("julc SDK not configured. Please configure it via julc > Configuration.\n");
            inputField.setEnabled(false);
            sendBtn.setEnabled(false);
            return;
        }

        try {
            List<String> cmd = sdk.getJulcCommand();
            cmd.add("repl");
            cmd.add("--no-jline");

            GeneralCommandLine commandLine = new GeneralCommandLine(cmd);
            commandLine.setWorkDirectory(project.getBasePath());
            commandLine.setRedirectErrorStream(true);

            processHandler = new OSProcessHandler(commandLine);
            processStdin = processHandler.getProcess().getOutputStream();

            processHandler.addProcessListener(new ProcessAdapter() {
                @Override
                public void onTextAvailable(@NotNull ProcessEvent event, @NotNull Key outputType) {
                    String text = stripAnsi(event.getText());
                    SwingUtilities.invokeLater(() -> appendOutput(text));
                }

                @Override
                public void processTerminated(@NotNull ProcessEvent event) {
                    SwingUtilities.invokeLater(() -> {
                        appendOutput("\n[REPL process exited with code " + event.getExitCode() + "]\n");
                        inputField.setEnabled(false);
                        sendBtn.setEnabled(false);
                    });
                }
            });

            processHandler.startNotify();
            inputField.setEnabled(true);
            sendBtn.setEnabled(true);
            inputField.requestFocusInWindow();

        } catch (ExecutionException e) {
            appendOutput("Failed to start julc REPL: " + e.getMessage() + "\n");
            LOG.warn("Failed to start julc REPL", e);
        }
    }

    private void sendInput() {
        String input = inputField.getText();
        if (input.isEmpty()) return;

        appendOutput("julc> " + input + "\n");
        history.add(input);
        historyIndex = history.size();
        inputField.setText("");

        if (processStdin != null) {
            try {
                processStdin.write((input + "\n").getBytes(StandardCharsets.UTF_8));
                processStdin.flush();
            } catch (Exception e) {
                appendOutput("[Error sending input: " + e.getMessage() + "]\n");
            }
        }
    }

    private void restartRepl() {
        if (processHandler != null && !processHandler.isProcessTerminated()) {
            processHandler.destroyProcess();
        }
        outputArea.setText("");
        startRepl();
    }

    private void navigateHistory(int direction) {
        if (history.isEmpty()) return;
        historyIndex += direction;
        historyIndex = Math.max(0, Math.min(historyIndex, history.size()));

        if (historyIndex < history.size()) {
            inputField.setText(history.get(historyIndex));
        } else {
            inputField.setText("");
        }
    }

    private void appendOutput(String text) {
        outputArea.append(text);
        outputArea.setCaretPosition(outputArea.getDocument().getLength());
    }

    private String stripAnsi(String text) {
        return text.replaceAll(ANSI_ESCAPE_REGEX, "");
    }
}
