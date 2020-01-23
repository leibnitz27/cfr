package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationEntryKind;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationEntryValue;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationLocation;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationTargetInfo;
import org.benf.cfr.reader.entities.attributes.TypePath;

import java.util.Map;

public class AnnotationTableTypeEntry<T extends TypeAnnotationTargetInfo> extends AnnotationTableEntry {
    private final TypeAnnotationEntryValue value;
    private final T targetInfo;
    private final TypePath typePath;

    public AnnotationTableTypeEntry(TypeAnnotationEntryValue value, T targetInfo, TypePath typePath, JavaTypeInstance type, Map<String, ElementValue> elementValueMap) {
        super(type, elementValueMap);
        this.value = value;
        this.targetInfo = targetInfo;
        this.typePath = typePath;
    }

    public TypePath getTypePath() {
        return typePath;
    }

    public TypeAnnotationEntryValue getValue() { return value; }

    public TypeAnnotationEntryKind getKind() {
        return value.getKind();
    }

    public T getTargetInfo() {
        return targetInfo;
    }


}