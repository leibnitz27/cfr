package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.entities.annotations.AnnotationTableEntry;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.attributes.AttributeMap;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeInvisibleAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeInvisibleParameterAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleParameterAnnotations;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationEntryValue;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationTargetInfo;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Set;

public class MethodPrototypeAnnotationsHelper {
    private final AttributeMap attributeMap;
    private final TypeAnnotationHelper typeAnnotationHelper;

    public MethodPrototypeAnnotationsHelper(AttributeMap attributes) {
        this.attributeMap = attributes;
        this.typeAnnotationHelper = TypeAnnotationHelper.create(attributes,
                TypeAnnotationEntryValue.type_generic_method_constructor,
                TypeAnnotationEntryValue.type_ret_or_new,
                TypeAnnotationEntryValue.type_receiver,
                TypeAnnotationEntryValue.type_throws,
                TypeAnnotationEntryValue.type_formal
                );
    }

    static boolean dumpAnnotationTableEntries(
            List<? extends AnnotationTableTypeEntry> annotationTableTypeEntries,
                                           List<AnnotationTableEntry> annotationTableEntries,
                                           Dumper d) {
        Set<JavaTypeInstance> collisions = (annotationTableEntries != null && annotationTableTypeEntries != null) ? SetFactory.<JavaTypeInstance>newSet() : null;
        boolean t1 = dumpAnnotationTableEntries(annotationTableTypeEntries, collisions, d);
        boolean t2 = dumpAnnotationTableEntries(annotationTableEntries, collisions, d);
        return t1 || t2;
    }

    private static boolean dumpAnnotationTableEntries(List<? extends AnnotationTableEntry> entries, Set<JavaTypeInstance> collisions, Dumper d) {
        if (entries == null) return false;
        boolean done = false;
        for (AnnotationTableEntry entry : entries) {
            if (collisions != null && !collisions.add(entry.getClazz())) {
                continue;
            }
            done = true;
            entry.dump(d);
            d.print(' ');
        }
        return done;
    }

    // TODO: Linear scans here, could be replaced with index.
    public List<AnnotationTableTypeEntry> getTypeTargetAnnotations(final TypeAnnotationEntryValue target) {
        if (typeAnnotationHelper == null) return null;
        List<AnnotationTableTypeEntry> res = Functional.filter(typeAnnotationHelper.getEntries(), new Predicate<AnnotationTableTypeEntry>() {
            @Override
            public boolean test(AnnotationTableTypeEntry in) {
                return in.getValue() == target;
            }
        });
        if (res.isEmpty()) return null;
        return res;
    }

    public List<AnnotationTableEntry> getMethodAnnotations() {
        return MiscAnnotations.BasicAnnotations(attributeMap);
    }

    private List<AnnotationTableEntry> getParameterAnnotations(int idx) {
        AttributeRuntimeVisibleParameterAnnotations a1 = attributeMap.getByName(AttributeRuntimeVisibleParameterAnnotations.ATTRIBUTE_NAME);
        AttributeRuntimeInvisibleParameterAnnotations a2 = attributeMap.getByName(AttributeRuntimeInvisibleParameterAnnotations.ATTRIBUTE_NAME);
        List<AnnotationTableEntry> e1 = a1 == null ? null : a1.getAnnotationsForParamIdx(idx);
        List<AnnotationTableEntry> e2 = a2 == null ? null : a2.getAnnotationsForParamIdx(idx);
        return ListFactory.combinedOptimistic(e1,e2);
    }

    private List<AnnotationTableTypeEntry> getTypeParameterAnnotations(final int paramIdx) {
        List<AnnotationTableTypeEntry> typeEntries = getTypeTargetAnnotations(TypeAnnotationEntryValue.type_formal);
        if (typeEntries == null) return null;
        typeEntries = Functional.filter(typeEntries, new Predicate<AnnotationTableTypeEntry>() {
            @Override
            public boolean test(AnnotationTableTypeEntry in) {
                return ((TypeAnnotationTargetInfo.TypeAnnotationFormalParameterTarget)in.getTargetInfo()).getIndex() == paramIdx;
            }
        });
        if (typeEntries.isEmpty()) return null;
        return typeEntries;
    }

    void dumpParamType(JavaTypeInstance arg, final int paramIdx, Dumper d) {
        List<AnnotationTableEntry> entries = getParameterAnnotations(paramIdx);
        List<AnnotationTableTypeEntry> typeEntries = getTypeParameterAnnotations(paramIdx);
        if (typeEntries == null && entries == null) {
            d.dump(arg);
            return;
        }
        JavaAnnotatedTypeInstance jat = arg.getAnnotatedInstance();
        TypeAnnotationHelper.apply(jat, typeEntries, entries, new DecompilerComments());
        d.dump(jat);
    }
}
