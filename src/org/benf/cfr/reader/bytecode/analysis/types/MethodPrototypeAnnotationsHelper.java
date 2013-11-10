package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.annotations.AnnotationTableEntry;
import org.benf.cfr.reader.entities.attributes.AttributeParameterAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeInvisibleParameterAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleParameterAnnotations;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 22/03/2013
 * Time: 17:14
 */
public class MethodPrototypeAnnotationsHelper {
    private final AttributeRuntimeVisibleParameterAnnotations runtimeVisibleParameterAnnotations;
    private final AttributeRuntimeInvisibleParameterAnnotations runtimeInvisibleParameterAnnotations;

    public MethodPrototypeAnnotationsHelper(AttributeRuntimeVisibleParameterAnnotations runtimeVisibleParameterAnnotations, AttributeRuntimeInvisibleParameterAnnotations runtimeInvisibleParameterAnnotations) {
        this.runtimeVisibleParameterAnnotations = runtimeVisibleParameterAnnotations;
        this.runtimeInvisibleParameterAnnotations = runtimeInvisibleParameterAnnotations;
    }

    private static void addAnnotation(AttributeParameterAnnotations annotations, int idx, Dumper d) {
        if (annotations == null) return;
        List<AnnotationTableEntry> annotationTableEntries = annotations.getAnnotationsForParamIdx(idx);
        if (annotationTableEntries == null || annotationTableEntries.isEmpty()) return;
        for (AnnotationTableEntry annotationTableEntry : annotationTableEntries) {
            annotationTableEntry.dump(d);
            d.print(' ');
        }
    }

    public void addAnnotationTextForParameterInto(int idx, Dumper d) {
        addAnnotation(runtimeVisibleParameterAnnotations, idx, d);
        addAnnotation(runtimeInvisibleParameterAnnotations, idx, d);
    }

}
