package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationEntryKind;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationLocation;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationTargetInfo;
import org.benf.cfr.reader.entities.attributes.TypePath;

import java.util.Map;

public class AnnotationTableTypeEntry<T extends TypeAnnotationTargetInfo> extends AnnotationTableEntry {

    private final TypeAnnotationEntryKind kind;
    private final TypeAnnotationLocation location;

    private final T targetInfo;

    private final TypePath typePath;

    public AnnotationTableTypeEntry(TypeAnnotationEntryKind kind, TypeAnnotationLocation location, T targetInfo, TypePath typePath, JavaTypeInstance type, Map<String, ElementValue> elementValueMap) {
        super(type, elementValueMap);
        this.kind = kind;
        this.location = location;
        this.targetInfo = targetInfo;
        this.typePath = typePath;
    }

    public TypePath getTypePath() {
        return typePath;
    }

    public TypeAnnotationEntryKind getKind() {
        return kind;
    }

    public T getTargetInfo() {
        return targetInfo;
    }

}