# Jar Compare Utility

This is a simple utility to compare and update two jar files.

## How to Use

1.  **Run the application:**
    -   Execute `./run.sh` in your terminal. This will build the project if needed and start the application.

2.  **Load JAR files:**
    -   **Drag and Drop:** Drag JAR files from your file explorer and drop them onto the application. The first will be the source, the second the destination.
    -   **Manual Selection:** Use the "Open Source Jar" and "Open Destination Jar" buttons.

3.  **Compare:**
    -   Differences will be highlighted in the tree view.

## Key Features

-   **Visual Diffing:** Compare two JAR files and see a color-coded tree view of the differences.
-   **File Management:**
    -   **Update:** Select files in the source tree and right-click to copy them to the destination JAR.
    -   **Add Files:** Add files from your local system into the destination JAR's tree.
    -   **Extract Files:** Drag and drop files or directories from either JAR's tree to your local filesystem to extract them.
    -   **Create Directories:** Add new directories to the destination JAR.
-   **Decompilation and File Viewing:**
    -   **Decompile `.class` files:** Right-click on any `.class` file and select "OpenDecompile" to view the decompiled Java source code.
    -   **View text-based files:** Right-click on any other file (e.g., `.MF`, `.properties`, `.xml`) and select "Open" to view its contents in your default text editor.
-   **Drag and Drop Workflow:** Supports dragging and dropping JARs to open, and dragging files/folders to add or extract.
