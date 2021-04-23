package org.benf.cfr.reader.entities;

import org.benf.cfr.reader.bytecode.analysis.opgraph.Op04StructuredStatement;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.util.DecompilerComments;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.collections.CollectionUtils;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.EnumSet;

/*
 * Not a real method - a method we've had to introduce in order to handle something that can't be properly represented
 * in java.
 */
public class FakeMethod implements TypeUsageCollectable, Dumpable {
    private final String name;
    private final EnumSet<AccessFlagMethod> accessFlags;
    private final JavaTypeInstance returnType;
    private final Op04StructuredStatement structuredStatement;
    private final DecompilerComments comments;

    public FakeMethod(String name, EnumSet<AccessFlagMethod> accessFlags, JavaTypeInstance returnType, Op04StructuredStatement structuredStatement, DecompilerComments comments) {
        this.name = name;
        this.accessFlags = accessFlags;
        this.returnType = returnType;
        this.structuredStatement = structuredStatement;
        this.comments = comments;
    }

    @Override
    public void collectTypeUsages(TypeUsageCollector collector) {

    }

    @Override
    public Dumper dump(Dumper d) {
        if (comments != null) {
            comments.dump(d);
        }
        if (accessFlags != null) {
            String prefix = CollectionUtils.join(accessFlags, " ");
            if (!prefix.isEmpty()) {
                d.keyword(prefix);
                d.separator(" ");
            }
        }
        // At this point there's no need for any implementations that carry arguments.
        d.dump(returnType).separator(" ").methodName(name, null, false, true).separator("() ");
        structuredStatement.dump(d);
        return d;
    }

    public String getName() {
        return name;
    }
}
