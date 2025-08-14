package com.jarcompare;

import javax.swing.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.*;
import java.io.File;
import java.util.List;

public class JarDropTargetListener implements DropTargetListener {

    private final JTree tree;
    private final JarCompareUI ui;

    public JarDropTargetListener(JTree tree, JarCompareUI ui) {
        this.tree = tree;
        this.ui = ui;
    }

    @Override
    public void dragEnter(DropTargetDragEvent dtde) {
    }

    @Override
    public void dragOver(DropTargetDragEvent dtde) {
    }

    @Override
    public void dropActionChanged(DropTargetDragEvent dtde) {
    }

    @Override
    public void dragExit(DropTargetEvent dte) {
    }

    @Override
    public void drop(DropTargetDropEvent dtde) {
        try {
            Transferable transferable = dtde.getTransferable();
            if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                dtde.acceptDrop(DnDConstants.ACTION_COPY);
                List<File> fileList = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
                if (fileList.size() == 1) {
                    File file = fileList.get(0);
                    if (file.getName().toLowerCase().endsWith(".jar")) {
                        ui.setJarFileForTree(tree, file);
                        ui.updateTree(file, tree);
                        ui.updateRenderers();
                        if (ui.getSourceJarFile() != null && ui.getDestJarFile() != null) {
                            ui.compareTrees();
                        }
                    }
                }
                dtde.dropComplete(true);
            } else {
                dtde.rejectDrop();
            }
        } catch (Exception e) {
            e.printStackTrace();
            dtde.rejectDrop();
        }
    }
}
