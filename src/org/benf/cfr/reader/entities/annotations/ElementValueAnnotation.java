package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/03/2013
 * Time: 19:24
 */
public class ElementValueAnnotation implements ElementValue {
    private final AnnotationTableEntry annotationTableEntry;

    public ElementValueAnnotation(AnnotationTableEntry annotationTableEntry) {
        this.annotationTableEntry = annotationTableEntry;
    }

    @Override
    public void dump(Dumper d) {
        annotationTableEntry.dump(d);
    }
}
