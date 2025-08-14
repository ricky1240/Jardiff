
package com.jarcompare;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.jar.JarEntry;

public class DiffTreeCellRenderer extends DefaultTreeCellRenderer {

    private final DefaultMutableTreeNode destRoot;

    public DiffTreeCellRenderer(DefaultMutableTreeNode destRoot) {
        this.destRoot = destRoot;
    }

    @Override
    public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) value;
        if (node.getUserObject() instanceof JarEntryNode) {
            JarEntryNode jarEntryNode = (JarEntryNode) node.getUserObject();
            JarEntry jarEntry = jarEntryNode.getJarEntry();
            if (jarEntry != null) {
                long size = jarEntry.getSize();
                long time = jarEntry.getTime();
                String text = String.format("<html><table><tr><td width='200'>%s</td><td width='100'>%d bytes</td><td>%s</td></tr></table></html>",
                        jarEntryNode.getName(),
                        size,
                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(time));
                setText(text);
            }
        }

        if (node.getUserObject() instanceof JarEntryNode) {
            JarEntryNode jarEntryNode = (JarEntryNode) node.getUserObject();
            switch (jarEntryNode.getComparisonState()) {
                case EXTRA_IN_SOURCE:
                    setForeground(Color.RED);
                    break;
                case EXTRA_IN_DEST:
                    setForeground(new Color(0, 128, 0));
                    break;
                case MODIFIED:
                    setForeground(Color.ORANGE);
                    break;
                default:
                    setForeground(Color.BLACK);
                    break;
            }
        }

        return this;
    }

}
