package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.output.Dumper;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 09/05/2013
 * Time: 05:50
 */
public interface ClassFileDumper extends TypeUsageCollectable {
    Dumper dump(ClassFile classFile, boolean innerClass, Dumper d);

    /*
     * Some dumpers may need to request additional types -
     */
    void collectTypeUsages(TypeUsageCollector collector);

}
