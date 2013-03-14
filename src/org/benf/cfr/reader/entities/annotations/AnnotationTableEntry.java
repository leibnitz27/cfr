package org.benf.cfr.reader.entities.annotations;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/03/2013
 * Time: 06:36
 */
public class AnnotationTableEntry {
    private final String name;
    private final Map<String, ElementValue> elementValueMap;

    public AnnotationTableEntry(String name, Map<String, ElementValue> elementValueMap) {
        this.name = name;
        this.elementValueMap = elementValueMap;
    }
}
