package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.output.Dumper;

public interface ClassFileDumper extends TypeUsageCollectable {
    enum InnerClassDumpType {
        NOT(false),
        INNER_CLASS(true),
        INLINE_CLASS(true);

        final boolean isInnerClass;

        InnerClassDumpType(boolean isInnerClass) {
            this.isInnerClass = isInnerClass;
        }

        public boolean isInnerClass() {
            return isInnerClass;
        }
    }

    Dumper dump(ClassFile classFile, InnerClassDumpType innerClass, Dumper d);

    /*
     * Some dumpers may need to request additional types -
     */
    void collectTypeUsages(TypeUsageCollector collector);

}
