package org.benf.cfr.reader.bytecode.analysis.types;

import org.benf.cfr.reader.entities.annotations.AnnotationTableEntry;
import org.benf.cfr.reader.entities.attributes.AttributeParameterAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeInvisibleAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeInvisibleParameterAnnotations;
import org.benf.cfr.reader.entities.attributes.AttributeRuntimeVisibleParameterAnnotations;

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

    private static void addAnnotation(AttributeParameterAnnotations annotations, int idx, StringBuilder sb) {
        if (annotations == null) return;
        List<AnnotationTableEntry> annotationTableEntries = annotations.getAnnotationsForParamIdx(idx);
        if (annotationTableEntries == null || annotationTableEntries.isEmpty()) return;
        for (AnnotationTableEntry annotationTableEntry : annotationTableEntries) {
            annotationTableEntry.getTextInto(sb);
            sb.append(' ');
        }
    }

    public void addAnnotationTextForParameterInto(int idx, StringBuilder sb) {
        addAnnotation(runtimeVisibleParameterAnnotations, idx, sb);
        addAnnotation(runtimeInvisibleParameterAnnotations, idx, sb);
    }

}
