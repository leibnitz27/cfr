package org.benf.cfr.reader.util.configuration;

import org.benf.cfr.reader.entities.ClassFile;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 02/05/2013
 * Time: 06:09
 */
public interface ConfigCallback {
    void configureWith(ClassFile partiallyConstructedClassFile);
}
