package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.entities.annotations.AnnotationTableEntry;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.attributes.AttributeMap;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeInvisibleTypeAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleTypeAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeTypeAnnotations;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationEntryValue;
import org.benf.cfr.reader.entities.attributes.TypePathPart;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;

import java.util.List;
import java.util.Set;

public class TypeAnnotationHelper {
    private final List<AnnotationTableTypeEntry> entries;

    private TypeAnnotationHelper(List<AnnotationTableTypeEntry> entries) {
        this.entries = entries;
    }

    public static TypeAnnotationHelper create(AttributeMap map, TypeAnnotationEntryValue ... tkeys) {
        String[] keys = new String[] {
            AttributeRuntimeVisibleTypeAnnotations.ATTRIBUTE_NAME,
            AttributeRuntimeInvisibleTypeAnnotations.ATTRIBUTE_NAME
        };
        List<AnnotationTableTypeEntry> res = ListFactory.newList();
        for (String key : keys) {
            AttributeTypeAnnotations ann = map.getByName(key);
            if (ann == null) continue;
            List<AnnotationTableTypeEntry> tmp = ann.getAnnotationsFor(tkeys);
            if (tmp != null) {
                res.addAll(tmp);
            }
        }
        if (!res.isEmpty()) return new TypeAnnotationHelper(res);
        return null;
    }
    
    public static void apply(JavaAnnotatedTypeInstance annotatedTypeInstance, List<? extends AnnotationTableTypeEntry> typeEntries, DecompilerComments comments) {
        if (typeEntries != null) {
            for (AnnotationTableTypeEntry typeEntry : typeEntries) {
                apply(annotatedTypeInstance, typeEntry, comments);
            }
        }
    }

    public static void apply(JavaAnnotatedTypeInstance annotatedTypeInstance, AnnotationTableTypeEntry entry, Set<JavaTypeInstance> collisions, DecompilerComments comments) {
        if (entry == null) return;
        JavaAnnotatedTypeIterator iterator = annotatedTypeInstance.pathIterator();
        List<TypePathPart> segments = entry.getTypePath().segments;
        if (collisions != null && segments.isEmpty()) {
            collisions.add(entry.getClazz());
        }
        for (TypePathPart part : segments) {
            iterator = part.apply(iterator, comments);
        }
        iterator.apply(entry);
    }
    
    private static void apply(JavaAnnotatedTypeInstance annotatedTypeInstance, AnnotationTableTypeEntry typeEntry, DecompilerComments comments) {
        JavaAnnotatedTypeIterator iterator = annotatedTypeInstance.pathIterator();
        List<TypePathPart> segments = typeEntry.getTypePath().segments;
        for (TypePathPart part : segments) {
            iterator = part.apply(iterator, comments);
        }
        iterator.apply(typeEntry);
    }

    // TODO : Find usages of this, ensure linear scans are small.
    public List<AnnotationTableTypeEntry> getEntries() {
        return entries;
    }
}
