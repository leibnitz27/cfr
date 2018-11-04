package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.entities.annotations.AnnotationTableTypeEntry;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.Predicate;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;
import java.util.Map;

public abstract class AttributeTypeAnnotations extends Attribute {

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_REMAINDER = 6;
    private static final long OFFSET_OF_NUMBER_OF_ANNOTATIONS = 6;
    private static final long OFFSET_OF_ANNOTATION_TABLE = 8;
    private final Map<TypeAnnotationEntryKind, List<AnnotationTableTypeEntry>> annotationTableEntryData = MapFactory.newLazyMap(new UnaryFunction<TypeAnnotationEntryKind, List<AnnotationTableTypeEntry>>() {
        @Override
        public List<AnnotationTableTypeEntry> invoke(TypeAnnotationEntryKind arg) {
            return ListFactory.newList();
        }
    });

    private final int length;


    public AttributeTypeAnnotations(ByteData raw, ConstantPool cp) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        int numAnnotations = raw.getU2At(OFFSET_OF_NUMBER_OF_ANNOTATIONS);
        long offset = OFFSET_OF_ANNOTATION_TABLE;
        for (int x = 0; x < numAnnotations; ++x) {
            Pair<Long, AnnotationTableTypeEntry> ape = AnnotationHelpers.getTypeAnnotation(raw, offset, cp);
            offset = ape.getFirst();
            AnnotationTableTypeEntry entry = ape.getSecond();
            annotationTableEntryData.get(entry.getKind()).add(entry);
        }
    }

    @Override
    public Dumper dump(Dumper d) {
        for (List<AnnotationTableTypeEntry> annotationTableEntryList : annotationTableEntryData.values()) {
            for (AnnotationTableTypeEntry annotationTableEntry : annotationTableEntryList) {
                annotationTableEntry.dump(d);
                d.newln();
            }
        }
        return d;
    }


    @Override
    public long getRawByteLength() {
        return OFFSET_OF_REMAINDER + length;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        for (List<AnnotationTableTypeEntry> annotationTableEntryList : annotationTableEntryData.values()) {
            for (AnnotationTableTypeEntry annotationTableEntry : annotationTableEntryList) {
                annotationTableEntry.collectTypeUsages(collector);
            }
        }
    }

    public List<AnnotationTableTypeEntry<TypeAnnotationTargetInfo.TypeAnnotationLocalVarTarget>> getLocalVariableAnnotations(final int offset, final int slot, final int tolerance) {
        // CFR may hold the offset the variable was /created/ at, which is 1 before it becomes valid.
        if (!annotationTableEntryData.containsKey(TypeAnnotationEntryKind.localvar_target)) return ListFactory.newList();
        List<AnnotationTableTypeEntry> entries = annotationTableEntryData.get(TypeAnnotationEntryKind.localvar_target);

        entries = Functional.filter(entries, new Predicate<AnnotationTableTypeEntry>() {
            @Override
            public boolean test(AnnotationTableTypeEntry in) {
                TypeAnnotationTargetInfo.TypeAnnotationLocalVarTarget tgt = (TypeAnnotationTargetInfo.TypeAnnotationLocalVarTarget)in.getTargetInfo();
                return tgt.matches(offset, slot, tolerance);
            }
        });
        // Naughty cast, but we know it's true.
        //noinspection unchecked
        return (List<AnnotationTableTypeEntry<TypeAnnotationTargetInfo.TypeAnnotationLocalVarTarget>>)(Object)entries;
    }
}
