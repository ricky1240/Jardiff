package com.jarcompare;

import java.util.jar.JarEntry;

public class JarEntryNode {

    public enum ComparisonState {
        DEFAULT,
        EXTRA_IN_SOURCE,
        EXTRA_IN_DEST,
        MODIFIED
    }

    private final String name;
    private final JarEntry jarEntry;
    private ComparisonState comparisonState = ComparisonState.DEFAULT;

    public JarEntryNode(String name, JarEntry jarEntry) {
        this.name = name;
        this.jarEntry = jarEntry;
    }

    public String getName() {
        return name;
    }

    public JarEntry getJarEntry() {
        return jarEntry;
    }

    public ComparisonState getComparisonState() {
        return comparisonState;
    }

    public void setComparisonState(ComparisonState comparisonState) {
        this.comparisonState = comparisonState;
    }

    @Override
    public String toString() {
        return name;
    }
}
