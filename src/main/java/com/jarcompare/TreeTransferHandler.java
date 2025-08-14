package com.jarcompare;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class TreeTransferHandler extends TransferHandler {

    private static final DataFlavor JAVA_FILE_LIST_FLAVOR = DataFlavor.javaFileListFlavor;

    @Override
    public int getSourceActions(JComponent c) {
        return COPY;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        JTree tree = (JTree) c;
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
        if (!(node.getUserObject() instanceof JarEntryNode)) {
            return null;
        }

        JarEntryNode jarEntryNode = (JarEntryNode) node.getUserObject();
        JarEntry jarEntry = jarEntryNode.getJarEntry();
        if (jarEntry == null) {
            return null;
        }

        try {
            File tempFile = createTempFileFromJarEntry(jarEntry, tree);
            if (tempFile == null) {
                return null;
            }
            List<File> fileList = new ArrayList<>();
            fileList.add(tempFile);
            return new FileTransferable(fileList);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        // Do not delete temp files here, as the OS needs them for the drag operation.
        // The OS will clean up the temp files.
    }

    private File createTempFileFromJarEntry(JarEntry jarEntry, JTree tree) throws IOException {
        JarCompareUI ui = (JarCompareUI) SwingUtilities.getWindowAncestor(tree);
        File jarFile = ui.getJarFileForTree(tree);
        if (jarFile == null) {
            return null;
        }

        try (JarFile jf = new JarFile(jarFile)) {
            if (jarEntry.isDirectory()) {
                File tempDir = new File(System.getProperty("java.io.tmpdir"), jarEntry.getName());
                tempDir.mkdirs();
                jf.stream()
                        .filter(entry -> entry.getName().startsWith(jarEntry.getName()))
                        .forEach(entry -> {
                            try {
                                File outputFile = new File(tempDir.getParent(), entry.getName());
                                if (entry.isDirectory()) {
                                    outputFile.mkdirs();
                                } else {
                                    outputFile.getParentFile().mkdirs();
                                    try (InputStream is = jf.getInputStream(entry);
                                         FileOutputStream fos = new FileOutputStream(outputFile)) {
                                        byte[] buffer = new byte[1024];
                                        int bytesRead;
                                        while ((bytesRead = is.read(buffer)) != -1) {
                                            fos.write(buffer, 0, bytesRead);
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                return new File(System.getProperty("java.io.tmpdir"), jarEntry.getName().split("/")[0]);
            } else {
                File tempFile = new File(System.getProperty("java.io.tmpdir"), jarEntry.getName().substring(jarEntry.getName().lastIndexOf('/') + 1));
                try (InputStream is = jf.getInputStream(jarEntry);
                     FileOutputStream fos = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = is.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }
                return tempFile;
            }
        }
    }

    private class FileTransferable implements Transferable {
        private List<File> fileList;

        public FileTransferable(List<File> fileList) {
            this.fileList = fileList;
        }

        @Override
        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{JAVA_FILE_LIST_FLAVOR};
        }

        @Override
        public boolean isDataFlavorSupported(DataFlavor flavor) {
            return JAVA_FILE_LIST_FLAVOR.equals(flavor);
        }

        @Override
        public Object getTransferData(DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (!isDataFlavorSupported(flavor)) {
                throw new UnsupportedFlavorException(flavor);
            }
            return fileList;
        }

        public void deleteTempFiles() {
            for (File file : fileList) {
                deleteRecursively(file);
            }
        }

        private void deleteRecursively(File file) {
            if (file.isDirectory()) {
                for (File child : file.listFiles()) {
                    deleteRecursively(child);
                }
            }
            file.delete();
        }
    }
}
