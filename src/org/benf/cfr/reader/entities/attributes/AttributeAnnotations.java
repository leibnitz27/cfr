package org.benf.cfr.reader.entities.attributes;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.entities.*;
import org.benf.cfr.reader.entities.annotations.*;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.bytestream.ByteData;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 18/04/2011
 * Time: 19:01
 * To change this template use File | Settings | File Templates.
 */
public abstract class AttributeAnnotations extends Attribute {

    private static final long OFFSET_OF_ATTRIBUTE_LENGTH = 2;
    private static final long OFFSET_OF_REMAINDER = 6;
    private static final long OFFSET_OF_NUMBER_OF_ANNOTATIONS = 6;
    private static final long OFFSET_OF_ANNOTATION_TABLE = 8;
    private final List<AnnotationTableEntry> annotationTableEntryList = ListFactory.newList();

    private final int length;

    public AttributeAnnotations(ByteData raw, ConstantPool cp) {
        this.length = raw.getS4At(OFFSET_OF_ATTRIBUTE_LENGTH);
        short numAnnotations = raw.getS2At(OFFSET_OF_NUMBER_OF_ANNOTATIONS);
        long offset = OFFSET_OF_ANNOTATION_TABLE;
        for (int x = 0; x < numAnnotations; ++x) {
            Pair<Long, AnnotationTableEntry> ape = AnnotationHelpers.getAnnotation(raw, offset, cp);
            offset = ape.getFirst();
            annotationTableEntryList.add(ape.getSecond());
        }
    }

    public List<AnnotationTableEntry> getAnnotationTableEntryList() {
        return annotationTableEntryList;
    }

    @Override
    public void dump(Dumper d) {
        for (AnnotationTableEntry annotationTableEntry : annotationTableEntryList) {
            annotationTableEntry.dump(d);
            d.newln();
        }
    }


    @Override
    public long getRawByteLength() {
        return OFFSET_OF_REMAINDER + length;
    }

}
