package dev.jasper.remote.ui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.*;

/** Compact connection progress, with retry after failure. Contains no network work. */
public final class ConnectionPanel extends JPanel {
    final JLabel message = new JLabel("Preparing connection…");
    final JProgressBar progress = new JProgressBar();
    final JButton retry = new JButton("Retry"), cancel = new JButton("Cancel");

    public ConnectionPanel(String name, String address, Runnable retryAction, Runnable cancelAction) {
        super(new BorderLayout(0, 16));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        var heading = new JPanel(new java.awt.GridLayout(0, 1, 0, 5));
        var title = new JLabel(name) {
            @Override public void updateUI() {
                super.updateUI();
                var base = UIManager.getFont("Label.font");
                setFont(base.deriveFont(Font.BOLD, base.getSize2D() + 3));
            }
        };
        title.putClientProperty("html.disable", true);
        var target = new JLabel(address); target.putClientProperty("html.disable", true);
        heading.add(title); heading.add(target); add(heading, BorderLayout.NORTH);
        var body = new JPanel(new BorderLayout(0, 8));
        message.putClientProperty("html.disable", true);
        body.add(message, BorderLayout.NORTH); body.add(progress, BorderLayout.SOUTH); add(body, BorderLayout.CENTER);
        var buttons = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 6, 0));
        buttons.add(retry); buttons.add(cancel); add(buttons, BorderLayout.SOUTH);
        retry.addActionListener(event -> retryAction.run()); cancel.addActionListener(event -> cancelAction.run());
        working();
    }
    @Override public Dimension getPreferredSize() {
        Dimension size = super.getPreferredSize();
        return new Dimension(Math.max(400, size.width), Math.max(190, size.height));
    }
    public void working() { progress.setVisible(true); progress.setIndeterminate(true); retry.setVisible(false); cancel.setText("Cancel"); status("Preparing connection…"); }
    public void status(String text) { message.setText(text); message.setToolTipText(text); }
    public void failed(String text) { status(text); progress.setIndeterminate(false); progress.setVisible(false); retry.setVisible(true); cancel.setText("Close"); }
    public void finished() { progress.setIndeterminate(false); }
}
