
package com.jarcompare;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import java.awt.*;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.ByteArrayInputStream;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import javax.swing.tree.TreePath;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.dnd.DropTarget;
import com.strobel.decompiler.Decompiler;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import java.io.StringWriter;
import java.io.FilenameFilter;

public class JarCompareUI extends JFrame {

    private JTree sourceTree;
    private JTree destTree;

    public JarCompareUI() {
        setTitle("Jar Compare Utility");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        // Create panels
        JPanel sourcePanel = new JPanel(new BorderLayout());
        JPanel destPanel = new JPanel(new BorderLayout());

        // Create file choosers
        JButton sourceButton = new JButton("Open Source Jar");
        JButton destButton = new JButton("Open Destination Jar");

        sourceButton.addActionListener(e -> chooseJarFile(sourceTree));
        destButton.addActionListener(e -> chooseJarFile(destTree));

        sourcePanel.add(sourceButton, BorderLayout.NORTH);
        destPanel.add(destButton, BorderLayout.NORTH);

        // Create tree views
        sourceTree = new JTree(new DefaultTreeModel(null));
        destTree = new JTree(new DefaultTreeModel(null));

        // Enable drag and drop
        sourceTree.setDragEnabled(true);
        destTree.setDragEnabled(true);
        sourceTree.setTransferHandler(new TreeTransferHandler());
        destTree.setTransferHandler(new TreeTransferHandler());

        // Add right-click menu
        MouseAdapter mouseAdapter = new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isRightMouseButton(e)) {
                    JTree tree = (JTree) e.getSource();
                    TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                    if (path != null) {
                        tree.setSelectionPath(path);
                        DefaultMutableTreeNode node = (DefaultMutableTreeNode) path.getLastPathComponent();
                        JPopupMenu popupMenu = createPopupMenu(tree, node);
                        popupMenu.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        };

        sourceTree.addMouseListener(mouseAdapter);
        destTree.addMouseListener(mouseAdapter);

        // Add components to panels
        JScrollPane sourceScrollPane = new JScrollPane(sourceTree);
        JScrollPane destScrollPane = new JScrollPane(destTree);
        sourcePanel.add(sourceScrollPane, BorderLayout.CENTER);
        destPanel.add(destScrollPane, BorderLayout.CENTER);

        // Add drop target listeners
        sourceTree.setDropTarget(new DropTarget(sourceTree, new JarDropTargetListener(sourceTree, this)));
        destTree.setDropTarget(new DropTarget(destTree, new JarDropTargetListener(destTree, this)));

        // Add panels to frame
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sourcePanel, destPanel);
        splitPane.setResizeWeight(0.5);
        add(splitPane, BorderLayout.CENTER);
    }

    private JPopupMenu createPopupMenu(JTree tree, DefaultMutableTreeNode node) {
        JPopupMenu popupMenu = new JPopupMenu();
        boolean isClassFile = false;
        boolean isDirectory = false;

        if (node.getUserObject() instanceof JarEntryNode) {
            JarEntryNode jarEntryNode = (JarEntryNode) node.getUserObject();
            isClassFile = jarEntryNode.getName().endsWith(".class");
            if (jarEntryNode.getJarEntry() != null) {
                isDirectory = jarEntryNode.getJarEntry().isDirectory();
            } else {
                isDirectory = true; // Root node
            }
        } else {
            isDirectory = true; // Root node
        }

        // Common menu items for both trees
        if (isClassFile) {
            JMenuItem decompileMenuItem = new JMenuItem("OpenDecompile");
            decompileMenuItem.addActionListener(e -> {
                JarEntryNode jarEntryNode = (JarEntryNode) node.getUserObject();
                String treeType = (tree == sourceTree) ? "Source_" : "Destination_";
                decompileAndOpen(jarEntryNode.getJarEntry(), getJarFileForTree(tree), treeType);
            });
            popupMenu.add(decompileMenuItem);
        } else if (!isDirectory) {
            JMenuItem openMenuItem = new JMenuItem("Open");
            openMenuItem.addActionListener(e -> {
                JarEntryNode jarEntryNode = (JarEntryNode) node.getUserObject();
                String treeType = (tree == sourceTree) ? "Source_" : "Destination_";
                openFileInEditor(jarEntryNode.getJarEntry(), getJarFileForTree(tree), treeType);
            });
            popupMenu.add(openMenuItem);
        }

        if (tree == sourceTree) {
            JMenuItem copyMenuItem = new JMenuItem(isDirectory ? "Copy Inside" : "Copy");
            copyMenuItem.addActionListener(e -> copySelectedEntry());
            copyMenuItem.setEnabled(node.getParent() != null); // Allow copying files and directories
            popupMenu.add(copyMenuItem);
        } else if (tree == destTree) {
            JMenuItem pasteMenuItem = new JMenuItem("Paste Inside");
            pasteMenuItem.addActionListener(e -> pasteEntry());
            pasteMenuItem.setEnabled(isDirectory);
            popupMenu.add(pasteMenuItem);

            JMenuItem addFileMenuItem = new JMenuItem("Add File");
            addFileMenuItem.addActionListener(e -> addFileToSelectedDirectory());
            addFileMenuItem.setEnabled(isDirectory);
            popupMenu.add(addFileMenuItem);

            JMenuItem createDirectoryMenuItem = new JMenuItem("Create Directory");
            createDirectoryMenuItem.addActionListener(e -> createDirectoryInSelectedDirectory());
            createDirectoryMenuItem.setEnabled(isDirectory);
            popupMenu.add(createDirectoryMenuItem);

            JMenuItem deleteMenuItem = new JMenuItem("Delete");
            deleteMenuItem.addActionListener(e -> deleteSelectedEntry());
            deleteMenuItem.setEnabled(node.getParent() != null);
            popupMenu.add(deleteMenuItem);
        }

        return popupMenu;
    }

    public void updateRenderers() {
        DefaultMutableTreeNode sourceRoot = (DefaultMutableTreeNode) sourceTree.getModel().getRoot();
        DefaultMutableTreeNode destRoot = (DefaultMutableTreeNode) destTree.getModel().getRoot();

        if (sourceRoot != null && destRoot != null) {
            sourceTree.setCellRenderer(new DiffTreeCellRenderer(destRoot));
            destTree.setCellRenderer(new DiffTreeCellRenderer(sourceRoot));
            sourceTree.repaint();
            destTree.repaint();
        }
    }

    public void compareTrees() {
        if (sourceJarFile != null) {
            updateTree(sourceJarFile, sourceTree);
        }
        if (destJarFile != null) {
            updateTree(destJarFile, destTree);
        }

        DefaultMutableTreeNode sourceRoot = (DefaultMutableTreeNode) sourceTree.getModel().getRoot();
        DefaultMutableTreeNode destRoot = (DefaultMutableTreeNode) destTree.getModel().getRoot();

        if (sourceRoot != null && destRoot != null) {
            resetComparisonState(sourceRoot);
            compareNodes(sourceRoot, destRoot);
            sourceTree.repaint();
        }
    }

    private void resetComparisonState(DefaultMutableTreeNode node) {
        if (node.getUserObject() instanceof JarEntryNode) {
            ((JarEntryNode) node.getUserObject()).setComparisonState(JarEntryNode.ComparisonState.DEFAULT);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            resetComparisonState((DefaultMutableTreeNode) node.getChildAt(i));
        }
    }

    private void compareNodes(DefaultMutableTreeNode sourceRoot, DefaultMutableTreeNode destRoot) {
        // Traverse dest tree to find extra files
        traverseAndMarkMissing(destRoot, sourceRoot);

        // Traverse source tree to find extra files
        traverseAndMarkExtra(sourceRoot, destRoot);
    }

    private void traverseAndMarkExtra(DefaultMutableTreeNode sourceNode, DefaultMutableTreeNode destRoot) {
        if (sourceNode.getUserObject() instanceof JarEntryNode) {
            JarEntryNode sourceJarNode = (JarEntryNode) sourceNode.getUserObject();
            String sourcePath = getPath(sourceNode);
            DefaultMutableTreeNode destNode = findNodeByPath(destRoot, sourcePath);
            if (destNode == null) {
                sourceJarNode.setComparisonState(JarEntryNode.ComparisonState.EXTRA_IN_SOURCE);
            } else {
                // File exists in both, check for modification
                if (destNode.getUserObject() instanceof JarEntryNode) {
                    JarEntryNode destJarNode = (JarEntryNode) destNode.getUserObject();
                    if (sourceJarNode.getJarEntry() != null && destJarNode.getJarEntry() != null) {
                        if (sourceJarNode.getJarEntry().getSize() != destJarNode.getJarEntry().getSize()) {
                            sourceJarNode.setComparisonState(JarEntryNode.ComparisonState.MODIFIED);
                            destJarNode.setComparisonState(JarEntryNode.ComparisonState.MODIFIED);
                        } else {
                            try (JarFile sourceJar = new JarFile(sourceJarFile); JarFile destJar = new JarFile(destJarFile)) {
                                String sourceHash = getHash(sourceJar, sourceJarNode.getJarEntry());
                                String destHash = getHash(destJar, destJarNode.getJarEntry());
                                if (!sourceHash.equals(destHash)) {
                                    sourceJarNode.setComparisonState(JarEntryNode.ComparisonState.MODIFIED);
                                    destJarNode.setComparisonState(JarEntryNode.ComparisonState.MODIFIED);
                                }
                            } catch (IOException | NoSuchAlgorithmException e) {
                                e.printStackTrace();
                                // Handle exception, maybe mark as modified or show an error
                                sourceJarNode.setComparisonState(JarEntryNode.ComparisonState.MODIFIED);
                                destJarNode.setComparisonState(JarEntryNode.ComparisonState.MODIFIED);
                            }
                        }
                    }
                }
            }
        }
        for (int i = 0; i < sourceNode.getChildCount(); i++) {
            traverseAndMarkExtra((DefaultMutableTreeNode) sourceNode.getChildAt(i), destRoot);
        }
    }

    private void traverseAndMarkMissing(DefaultMutableTreeNode destNode, DefaultMutableTreeNode sourceRoot) {
        if (destNode.getUserObject() instanceof JarEntryNode) {
            JarEntryNode destJarNode = (JarEntryNode) destNode.getUserObject();
            String destPath = getPath(destNode);
            DefaultMutableTreeNode sourceNode = findNodeByPath(sourceRoot, destPath);
            if (sourceNode == null) {
                destJarNode.setComparisonState(JarEntryNode.ComparisonState.EXTRA_IN_DEST);
            }
        }

        for (int i = 0; i < destNode.getChildCount(); i++) {
            traverseAndMarkMissing((DefaultMutableTreeNode) destNode.getChildAt(i), sourceRoot);
        }
    }

    private DefaultMutableTreeNode findNodeByPath(DefaultMutableTreeNode root, String path) {
        String[] pathElements = path.split("/");
        DefaultMutableTreeNode currentNode = root;
        for (String element : pathElements) {
            if (element.isEmpty()) continue;
            DefaultMutableTreeNode foundNode = findNode(currentNode, element);
            if (foundNode == null) {
                return null;
            }
            currentNode = foundNode;
        }
        return currentNode;
    }

    private String getHash(JarFile jarFile, JarEntry jarEntry) throws IOException, NoSuchAlgorithmException {
        if (jarEntry.isDirectory()) {
            return "";
        }
        try (InputStream is = jarFile.getInputStream(jarEntry)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                md.update(buffer, 0, bytesRead);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        }
    }

    private File sourceJarFile;
    private File destJarFile;
    private JarEntry copiedJarEntry;
    private String copiedJarEntryPath;

    public File getJarFileForTree(JTree tree) {
        if (tree == sourceTree) {
            return sourceJarFile;
        } else if (tree == destTree) {
            return destJarFile;
        }
        return null;
    }


    public void setJarFileForTree(JTree tree, File file) {
        if (tree == sourceTree) {
            sourceJarFile = file;
        } else if (tree == destTree) {
            destJarFile = file;
        }
    }

    public File getSourceJarFile() {
        return sourceJarFile;
    }

    public File getDestJarFile() {
        return destJarFile;
    }

    private void copySelectedEntry() {
        TreePath selectedPath = sourceTree.getSelectionPath();
        if (selectedPath == null) {
            JOptionPane.showMessageDialog(this, "No entry selected in source tree.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        if (node.getUserObject() instanceof JarEntryNode) {
            JarEntryNode jarEntryNode = (JarEntryNode) node.getUserObject();
            copiedJarEntry = jarEntryNode.getJarEntry();
            copiedJarEntryPath = getPath(node);
        } else {
            JOptionPane.showMessageDialog(this, "Please select a file or directory to copy.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void pasteEntry() {
        if (copiedJarEntry == null || copiedJarEntryPath == null) {
            JOptionPane.showMessageDialog(this, "No entry copied.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (destJarFile == null) {
            JOptionPane.showMessageDialog(this, "Destination jar not selected.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        TreePath selectedPath = destTree.getSelectionPath();
        String targetPath = "";
        if (selectedPath != null) {
            Object lastComponent = selectedPath.getLastPathComponent();
            if (lastComponent instanceof DefaultMutableTreeNode) {
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) lastComponent;
                if (node.getUserObject() instanceof JarEntryNode) {
                    JarEntryNode jarEntryNode = (JarEntryNode) node.getUserObject();
                    if (jarEntryNode.getJarEntry() != null && jarEntryNode.getJarEntry().isDirectory()) {
                        targetPath = getPath(node);
                    } else {
                        JOptionPane.showMessageDialog(this, "Please select a directory to paste into.", "Error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                } else {
                    // This handles the case where the root node is selected and its user object is not a JarEntryNode
                    targetPath = "";
                }
            } else {
                // This handles the case where the root node is selected and it's not a DefaultMutableTreeNode
                targetPath = "";
            }
        } else {
            // If nothing is selected, paste to the root of the JAR
            targetPath = "";
        }

        String newEntryPath;
        boolean overwriteAll = false;

        try (JarFile sourceJar = new JarFile(sourceJarFile)) {
            if (copiedJarEntry.isDirectory()) {
                // Copying a directory
                String baseCopiedPath = copiedJarEntryPath;
                if (!baseCopiedPath.endsWith("/")) {
                    baseCopiedPath += "/";
                }

                java.util.Enumeration<JarEntry> entries = sourceJar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().startsWith(baseCopiedPath) && !entry.getName().equals(baseCopiedPath)) {
                        String relativePath = entry.getName().substring(baseCopiedPath.length());
                        newEntryPath = targetPath + relativePath;

                        // Check for conflict and ask to overwrite
                        if (!overwriteAll) {
                            try (JarFile destJar = new JarFile(destJarFile)) {
                                if (destJar.getEntry(newEntryPath) != null) {
                                    Object[] options = {"Yes", "No", "Yes to All"};
                                    int dialogResult = JOptionPane.showOptionDialog(this, "Entry " + newEntryPath + " already exists. Overwrite?", "Conflict", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
                                    if (dialogResult == JOptionPane.NO_OPTION) {
                                        continue; // Skip this entry
                                    } else if (dialogResult == 2) { // "Yes to All"
                                        overwriteAll = true;
                                    }
                                }
                            }
                        }

                        InputStream is = sourceJar.getInputStream(entry);
                        addEntryToJar(destJarFile, newEntryPath, is);
                        is.close();
                    }
                }
            } else {
                // Copying a single file
                newEntryPath = targetPath + new File(copiedJarEntryPath).getName();

                // Check for conflict and ask to overwrite
                if (!overwriteAll) {
                    try (JarFile destJar = new JarFile(destJarFile)) {
                        if (destJar.getEntry(newEntryPath) != null) {
                            int dialogResult = JOptionPane.showConfirmDialog(this, "Entry " + newEntryPath + " already exists. Overwrite?", "Conflict", JOptionPane.YES_NO_OPTION);
                            if (dialogResult == JOptionPane.NO_OPTION) {
                                return; // Stop paste operation for single file
                            }
                        }
                    }
                }

                InputStream is = sourceJar.getInputStream(copiedJarEntry);
                addEntryToJar(destJarFile, newEntryPath, is);
                is.close();
            }
            updateTree(destJarFile, destTree);
            compareTrees();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error pasting entry(ies): " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addEntryToJar(File jarFile, String entryPath, InputStream entryContent) throws IOException {
        File tempFile = new File(jarFile.getAbsolutePath() + ".tmp");
        boolean entryFoundAndReplaced = false;

        try (JarFile originalJar = new JarFile(jarFile); JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempFile))) {
            for (java.util.Enumeration<JarEntry> entries = originalJar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().equals(entryPath)) {
                    // This is the entry to be replaced/updated
                    jos.putNextEntry(new JarEntry(entryPath));
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = entryContent.read(buffer)) != -1) {
                        jos.write(buffer, 0, bytesRead);
                    }
                    jos.closeEntry();
                    entryFoundAndReplaced = true;
                } else {
                    // Copy existing entry as is
                    jos.putNextEntry(new JarEntry(entry.getName()));
                    try (InputStream is = originalJar.getInputStream(entry)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            jos.write(buffer, 0, bytesRead);
                        }
                    }
                    jos.closeEntry();
                }
            }

            // If the entry was not found in the original JAR, add it as a new entry
            if (!entryFoundAndReplaced) {
                jos.putNextEntry(new JarEntry(entryPath));
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = entryContent.read(buffer)) != -1) {
                    jos.write(buffer, 0, bytesRead);
                }
                jos.closeEntry();
            }
        }

        if (!jarFile.delete()) {
            throw new IOException("Could not delete original JAR file: " + jarFile.getName());
        }
        if (!tempFile.renameTo(jarFile)) {
            throw new IOException("Could not rename temp JAR file to original: " + tempFile.getName());
        }
    }

    private void chooseJarFile(JTree tree) {
        JFileChooser fileChooser = new JFileChooser();
        
        // Create a panel to hold the text field and paste button
        JPanel accessoryPanel = new JPanel(new BorderLayout());
        JTextField pathField = new JTextField();
        pathField.addActionListener(e -> {
            File file = new File(pathField.getText());
            fileChooser.setSelectedFile(file);
            fileChooser.approveSelection();
        });
        accessoryPanel.add(pathField, BorderLayout.CENTER);
        
        fileChooser.setAccessory(accessoryPanel);

        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            if (tree == sourceTree) {
                sourceJarFile = selectedFile;
            } else if (tree == destTree) {
                destJarFile = selectedFile;
            }
            updateTree(selectedFile, tree);
            updateRenderers();
            if (sourceJarFile != null && destJarFile != null) {
                compareTrees();
            }
        }
    }


    public void updateTree(File jarFile, JTree tree) {
        try (JarFile jar = new JarFile(jarFile)) {
            DefaultMutableTreeNode root = new DefaultMutableTreeNode(new JarEntryNode(jarFile.getName(), null));
            
            // Collect all JarEntries and sort them
            java.util.List<JarEntry> sortedEntries = new java.util.ArrayList<>();
            jar.stream().forEach(sortedEntries::add);
            sortedEntries.sort(java.util.Comparator.comparing(JarEntry::getName));

            sortedEntries.forEach(jarEntry -> {
                String[] path = jarEntry.getName().split("/");
                DefaultMutableTreeNode currentNode = root;
                for (String p : path) {
                    DefaultMutableTreeNode node = findNode(currentNode, p);
                    if (node == null) {
                        node = new DefaultMutableTreeNode(new JarEntryNode(p, jarEntry));
                        currentNode.add(node);
                    }
                    currentNode = node;
                }
            });
            tree.setModel(new DefaultTreeModel(root));
            updateRenderers();
            expandAllNodes(tree);
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error reading jar file", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private DefaultMutableTreeNode findNode(DefaultMutableTreeNode parent, String name) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getChildAt(i);
            if (child.getUserObject() instanceof JarEntryNode) {
                JarEntryNode node = (JarEntryNode) child.getUserObject();
                if (node.getName().equals(name)) {
                    return child;
                }
            }
        }
        return null;
    }

    private String getPath(javax.swing.tree.TreeNode node) {
        StringBuilder path = new StringBuilder();
        Object[] pathObjects = ((DefaultMutableTreeNode)node).getUserObjectPath();
        // Start from the second element to skip the JAR file name (root node)
        for (int i = 1; i < pathObjects.length; i++) {
            Object o = pathObjects[i];
            if (o instanceof JarEntryNode) {
                JarEntryNode jarNode = (JarEntryNode) o;
                path.append(jarNode.getName());
            } else {
                path.append(o.toString());
            }
            if (i < pathObjects.length - 1) { // Add separator for all but the last component
                path.append("/");
            }
        }
        // If it's a directory, ensure it ends with a '/'
        if (node instanceof DefaultMutableTreeNode) {
            DefaultMutableTreeNode mutableNode = (DefaultMutableTreeNode) node;
            if (mutableNode.getUserObject() instanceof JarEntryNode) {
                JarEntry jarEntry = ((JarEntryNode) mutableNode.getUserObject()).getJarEntry();
                if (jarEntry != null && jarEntry.isDirectory() && !path.toString().endsWith("/")) {
                    path.append("/");
                }
            }
        }
        return path.toString();
    }

    private void decompileAndOpen(JarEntry jarEntry, File jarFile, String treeType) {
        if (jarFile == null) {
            return;
        }
        try (JarFile jar = new JarFile(jarFile)) {
            String entryName = jarEntry.getName();
            if (entryName.endsWith(".class")) {
                entryName = entryName.substring(0, entryName.length() - 6);
            }
            StringWriter stringWriter = new StringWriter();
            DecompilerSettings settings = DecompilerSettings.javaDefaults();
            settings.setTypeLoader(new com.strobel.assembler.metadata.JarTypeLoader(jar));
            Decompiler.decompile(
                entryName,
                new PlainTextOutput(stringWriter),
                settings
            );

            String simpleName = new File(jarEntry.getName()).getName();
            if (simpleName.endsWith(".class")) {
                simpleName = simpleName.substring(0, simpleName.length() - 6);
            }
            String tempFileName = treeType + simpleName + ".java";
            File tempFile = new File(System.getProperty("java.io.tmpdir"), tempFileName);
            tempFile.deleteOnExit();

            try (FileOutputStream fos = new FileOutputStream(tempFile)) {
                fos.write(stringWriter.toString().getBytes());
            }

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(tempFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error decompiling or opening file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openFileInEditor(JarEntry jarEntry, File jarFile, String treeType) {
        if (jarFile == null) {
            return;
        }
        try (JarFile jar = new JarFile(jarFile)) {
            String simpleName = new File(jarEntry.getName()).getName();
            String tempFileName = treeType + simpleName;
            File tempFile = new File(System.getProperty("java.io.tmpdir"), tempFileName);
            tempFile.deleteOnExit();

            try (InputStream is = jar.getInputStream(jarEntry);
                 FileOutputStream fos = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(tempFile);
            }
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error opening file: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addFileToSelectedDirectory() {
        if (destJarFile == null) {
            JOptionPane.showMessageDialog(this, "Destination jar not selected", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        TreePath selectedPath = destTree.getSelectionPath();
        if (selectedPath == null) {
            JOptionPane.showMessageDialog(this, "Please select a directory to add the file to.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        String targetDirectoryPath = getPath(node);

        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile();
            String entryPath = targetDirectoryPath + selectedFile.getName();

            try (InputStream is = new FileInputStream(selectedFile)) {
                addEntryToJar(destJarFile, entryPath, is);
                updateTree(destJarFile, destTree);
                compareTrees();
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error adding file to jar", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void createDirectoryInSelectedDirectory() {
        if (destJarFile == null) {
            JOptionPane.showMessageDialog(this, "Destination jar not selected", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        TreePath selectedPath = destTree.getSelectionPath();
        if (selectedPath == null) {
            JOptionPane.showMessageDialog(this, "Please select a directory to create the new directory in.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        String targetDirectoryPath = getPath(node);

        String dirName = JOptionPane.showInputDialog(this, "Enter new directory name:");
        if (dirName != null && !dirName.isEmpty()) {
            String dirPath = targetDirectoryPath + dirName + "/";
            try {
                // Use addEntryToJar for directory creation as well
                addEntryToJar(destJarFile, dirPath, new ByteArrayInputStream(new byte[0]));
                updateTree(destJarFile, destTree);
                compareTrees();
            } catch (IOException e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error creating directory in jar", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    

    private void deleteSelectedEntry() {
        if (destJarFile == null) {
            JOptionPane.showMessageDialog(this, "Destination jar not selected", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        TreePath selectedPath = destTree.getSelectionPath();
        if (selectedPath == null) {
            JOptionPane.showMessageDialog(this, "No entry selected for deletion.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        DefaultMutableTreeNode node = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        if (!(node.getUserObject() instanceof JarEntryNode)) {
            JOptionPane.showMessageDialog(this, "Cannot delete the root JAR entry.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        JarEntryNode jarEntryNode = (JarEntryNode) node.getUserObject();
        String entryPathToDelete = getPath(node);

        int dialogResult = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete " + entryPathToDelete + "?", "Confirm Deletion", JOptionPane.YES_NO_OPTION);
        if (dialogResult == JOptionPane.NO_OPTION) {
            return;
        }

        try {
            if (jarEntryNode.getJarEntry() != null && jarEntryNode.getJarEntry().isDirectory()) {
                // Delete a directory and its contents
                if (!entryPathToDelete.endsWith("/")) {
                    entryPathToDelete += "/";
                }
                java.util.List<String> entriesToRemove = new java.util.ArrayList<>();
                try (JarFile destJar = new JarFile(destJarFile)) {
                    java.util.Enumeration<JarEntry> entries = destJar.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        if (entry.getName().startsWith(entryPathToDelete)) {
                            entriesToRemove.add(entry.getName());
                        }
                    }
                }
                removeEntriesFromJar(destJarFile, entriesToRemove);
            } else {
                // Delete a single file
                java.util.List<String> entriesToRemove = new java.util.ArrayList<>();
                entriesToRemove.add(entryPathToDelete);
                removeEntriesFromJar(destJarFile, entriesToRemove);
            }
            updateTree(destJarFile, destTree);
            compareTrees();
        } catch (IOException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error deleting entry(ies): " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void removeEntriesFromJar(File jarFile, java.util.List<String> entriesToRemove) throws IOException {
        File tempFile = new File(jarFile.getAbsolutePath() + ".tmp");

        try (JarFile originalJar = new JarFile(jarFile); JarOutputStream jos = new JarOutputStream(new FileOutputStream(tempFile))) {
            for (java.util.Enumeration<JarEntry> entries = originalJar.entries(); entries.hasMoreElements();) {
                JarEntry entry = entries.nextElement();
                if (!entriesToRemove.contains(entry.getName())) {
                    // Copy existing entry as is, if it's not in the list to be removed
                    jos.putNextEntry(new JarEntry(entry.getName()));
                    try (InputStream is = originalJar.getInputStream(entry)) {
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = is.read(buffer)) != -1) {
                            jos.write(buffer, 0, bytesRead);
                        }
                    }
                    jos.closeEntry();
                }
            }
        }

        if (!jarFile.delete()) {
            throw new IOException("Could not delete original JAR file: " + jarFile.getName());
        }
        if (!tempFile.renameTo(jarFile)) {
            throw new IOException("Could not rename temp JAR file to original: " + tempFile.getName());
        }
    }

    private void expandAllNodes(JTree tree) {
        int row = 0;
        while (row < tree.getRowCount()) {
            tree.expandRow(row);
            row++;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new JarCompareUI().setVisible(true);
        });
    }
}
