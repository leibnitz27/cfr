package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.entities.constantpool.ConstantPool;
import org.benf.cfr.reader.entities.annotations.AnnotationTableEntry;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.collections.ListFactory;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

public abstract class AttributeParameterAnnotations extends Attribute implements TypeUsageCollectable {

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_REMAINDER = 6;
    private static final long OFFSET_OF_NUMBER_OF_PARAMETERS = 6;
    private static final long OFFSET_OF_ANNOTATION_NAME_TABLE = 7;

    private final List<List<AnnotationTableEntry>> annotationTableEntryListList;
    private final int length;

    public AttributeParameterAnnotations(ByteData raw, ConstantPool cp) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        byte numParameters = raw.getS1At(OFFSET_OF_NUMBER_OF_PARAMETERS);
        long offset = OFFSET_OF_ANNOTATION_NAME_TABLE;
        annotationTableEntryListList = ListFactory.newList();
        for (int x = 0; x < numParameters; ++x) {
            List<AnnotationTableEntry> annotationTableEntryList = ListFactory.newList();

            int numAnnotations = raw.getU2At(offset);
            offset += 2;
            for (int y = 0; y < numAnnotations; ++y) {
                Pair<Long, AnnotationTableEntry> ape = AnnotationHelpers.getAnnotation(raw, offset, cp);
                offset = ape.getFirst();
                annotationTableEntryList.add(ape.getSecond());
            }
            annotationTableEntryListList.add(annotationTableEntryList);
        }
    }

    public List<AnnotationTableEntry> getAnnotationsForParamIdx(int idx) {
        if (idx < 0 || idx >= annotationTableEntryListList.size()) return null;
        return annotationTableEntryListList.get(idx);
    }

    @Override
    public Dumper dump(Dumper d) {
        return d;
    }

    @Override
    public long getRawByteLength() {
        return OFFSET_OF_REMAINDER + length;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {
        for (List<AnnotationTableEntry> annotationTableEntryList : annotationTableEntryListList) {
            for (AnnotationTableEntry annotationTableEntry : annotationTableEntryList) {
                annotationTableEntry.collectTypeUsages(collector);
            }
        }
    }

}
