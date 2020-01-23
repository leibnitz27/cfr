package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.entities.annotations.AnnotationTableEntry;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.attributes.AttributeMap;
import org.benf.cfr.reader.entities.attributes.AttributeParameterAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeInvisibleAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeInvisibleParameterAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeInvisibleTypeAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleParameterAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleTypeAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeTypeAnnotations;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationEntryKind;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationEntryValue;
import org.benf.cfr.reader.entities.attributes.TypeAnnotationTargetInfo;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class MethodPrototypeAnnotationsHelper {
    private final AttributeRuntimeVisibleParameterAnnotations runtimeVisibleParameterAnnotations;
    private final AttributeRuntimeInvisibleParameterAnnotations runtimeInvisibleParameterAnnotations;
    private final TypeAnnotationHelper typeAnnotationHelper;

    public MethodPrototypeAnnotationsHelper(AttributeMap attributes) {
        this.runtimeVisibleParameterAnnotations = attributes.getByName(AttributeRuntimeVisibleParameterAnnotations.ATTRIBUTE_NAME);
        this.runtimeInvisibleParameterAnnotations = attributes.getByName(AttributeRuntimeInvisibleParameterAnnotations.ATTRIBUTE_NAME);
        this.typeAnnotationHelper = TypeAnnotationHelper.create(attributes,
                TypeAnnotationEntryValue.type_generic_method_constructor,
                TypeAnnotationEntryValue.type_ret_or_new,
                TypeAnnotationEntryValue.type_receiver,
                TypeAnnotationEntryValue.type_throws,
                TypeAnnotationEntryValue.type_formal
                );
    }

    private static void addAnnotation(AttributeParameterAnnotations annotations, int idx, Dumper d) {
        if (annotations == null) return;
        List<AnnotationTableEntry> annotationTableEntries = annotations.getAnnotationsForParamIdx(idx);
        dumpAnnotationTableEntries(annotationTableEntries, d);
    }

    static void dumpAnnotationTableEntries(List<? extends AnnotationTableEntry> annotationTableEntries, Dumper d) {
        if (annotationTableEntries == null || annotationTableEntries.isEmpty()) return;
        for (AnnotationTableEntry annotationTableEntry : annotationTableEntries) {
            annotationTableEntry.dump(d);
            d.print(' ');
        }
    }

    void addAnnotationTextForParameterInto(int idx, Dumper d) {
        if (typeAnnotationHelper == null) {
            // This is a subset of type annotations - todo - what if there's both?
            addAnnotation(runtimeVisibleParameterAnnotations, idx, d);
            addAnnotation(runtimeInvisibleParameterAnnotations, idx, d);
        }
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

    void dumpParamType(JavaTypeInstance arg, final int paramIdx, Dumper d) {
        List<AnnotationTableTypeEntry> params = getTypeTargetAnnotations(TypeAnnotationEntryValue.type_formal);
        if (params == null) {
            d.dump(arg);
            return;
        }
        params = Functional.filter(params, new Predicate<AnnotationTableTypeEntry>() {
            @Override
            public boolean test(AnnotationTableTypeEntry in) {
                return ((TypeAnnotationTargetInfo.TypeAnnotationFormalParameterTarget)in.getTargetInfo()).getIndex() == paramIdx;
            }
        });
        JavaAnnotatedTypeInstance jat = arg.getAnnotatedInstance();
        TypeAnnotationHelper.apply(jat, params, new DecompilerComments());
        d.dump(jat);
    }
}
