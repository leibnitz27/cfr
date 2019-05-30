package org.benf.cfr.reader.apiunreleased;

import org.benf.cfr.reader.api.ClassFileSource;

// TODO : Experimental API - before moving, snip ClassFileSource link.
public interface ClassFileSource2 extends ClassFileSource {
    /**
     * CFR would like to know about all classes contained within the jar at {@code jarPath}
     *
     * @param jarPath path to a jar.
     * @return @{link JarContent} for this jar.
     */
    JarContent addJarContent(String jarPath);
}
