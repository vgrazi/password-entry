package com.vgrazi.passwordentry;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;

/**
 * Small always-on-top window with buttons that copy credentials to the clipboard.
 * Needs no special OS permissions (unlike the global key hook / Robot typing),
 * so it works on locked-down Macs. The clipboard is cleared after a timeout,
 * but only if it still holds the value we put there.
 */
class ClipboardWindow {

    private final Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    private final int clearSeconds;
    private final Timer clearTimer;
    private final JLabel status = new JLabel(" ", SwingConstants.CENTER);
    private String lastCopied;

    ClipboardWindow(int clearSeconds) {
        this.clearSeconds = clearSeconds;
        this.clearTimer = new Timer(clearSeconds * 1000, e -> clearIfStillOurs());
        this.clearTimer.setRepeats(false);
    }

    /**
     * @param entries label → value pairs, one button each, in display order
     */
    void show(java.util.LinkedHashMap<String, String> entries) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Password Entry");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setAlwaysOnTop(true);

            JPanel buttons = new JPanel(new GridLayout(0, 1, 4, 4));
            buttons.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
            entries.forEach((label, value) -> {
                if (value == null || value.isEmpty()) {
                    return;
                }
                JButton button = new JButton("Copy " + label);
                button.addActionListener(e -> copy(label, value));
                buttons.add(button);
            });

            JButton close = new JButton("Close Application");
            close.addActionListener(e -> {
                clearIfStillOurs();
                System.exit(0);
            });
            buttons.add(close);

            frame.add(buttons, BorderLayout.CENTER);
            status.setBorder(BorderFactory.createEmptyBorder(0, 8, 8, 8));
            frame.add(status, BorderLayout.SOUTH);

            frame.pack();
            frame.setMinimumSize(new Dimension(240, frame.getHeight()));
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    private void copy(String label, String value) {
        clipboard.setContents(new StringSelection(value), null);
        lastCopied = value;
        clearTimer.restart();
        String paste = PasswordEntryApplication.IS_MAC ? "⌘V" : "Ctrl+V";
        status.setText(label + " copied – paste with " + paste + " (clears in " + clearSeconds + "s)");
    }

    private void clearIfStillOurs() {
        clearTimer.stop();
        if (lastCopied == null) {
            return;
        }
        try {
            Object current = clipboard.getData(DataFlavor.stringFlavor);
            if (lastCopied.equals(current)) {
                clipboard.setContents(new StringSelection(""), null);
                status.setText("Clipboard cleared");
            }
        } catch (Exception ignored) {
            // Clipboard busy or holds non-text content: it's no longer ours, leave it alone
        }
        lastCopied = null;
    }
}
