package org.benf.cfr.reader.util.configuration;

import org.benf.cfr.reader.entities.ClassFile;

public interface ConfigCallback {
    void configureWith(ClassFile partiallyConstructedClassFile);
}
