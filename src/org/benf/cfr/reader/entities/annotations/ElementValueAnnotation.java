package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.output.Dumper;

public class ElementValueAnnotation implements ElementValue {
    private final AnnotationTableEntry annotationTableEntry;

    public ElementValueAnnotation(AnnotationTableEntry annotationTableEntry) {
        this.annotationTableEntry = annotationTableEntry;
    }

    @Override
    public Dumper dump(Dumper d) {
        return annotationTableEntry.dump(d);
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        annotationTableEntry.collectTypeUsages(collector);
    }

    @Override
    public ElementValue withTypeHint(JavaTypeInstance hint) {
        return this;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        else if (obj instanceof ElementValueAnnotation) {
            ElementValueAnnotation other = (ElementValueAnnotation) obj;
            return annotationTableEntry.equals(other.annotationTableEntry);
        }
        else {
            return false;
        }
    }
}
