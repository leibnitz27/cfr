package org.benf.cfr.reader.bytecode.analysis.parse;

import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.DeepCloneable;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.rewriters.ExpressionRewriterFlags;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.*;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.annotated.JavaAnnotatedTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.InferredJavaType;
import org.benf.cfr.reader.entities.exceptions.ExceptionCheck;
import org.benf.cfr.reader.util.TypeUsageCollectable;
import org.benf.cfr.reader.util.output.Dumpable;
import org.benf.cfr.reader.util.output.DumpableWithPrecedence;
import org.benf.cfr.reader.util.output.Dumper;

/*
 * Patterns are introduced very late by op04 rewriters, so don't have most of the
 * support required of LValue/Expression.
 */
public interface Pattern extends Dumpable, TypeUsageCollectable {

    InferredJavaType getInferredJavaType();

//    JavaAnnotatedTypeInstance getAnnotatedCreationType();
//
//    boolean canThrow(ExceptionCheck caught);

    Dumper dump(Dumper d, boolean defines);
}
