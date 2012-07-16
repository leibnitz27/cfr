package org.benf.cfr.reader.bytecode.analysis.parse;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueRewriter;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueUsageCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;
import org.benf.cfr.reader.bytecode.analysis.types.discovery.KnownJavaType;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/03/2012
 */
public interface Expression {
    Expression replaceSingleUsageLValues(LValueRewriter lValueRewriter, SSAIdentifiers ssaIdentifiers, StatementContainer statementContainer);

    boolean isSimple();

    void collectUsedLValues(LValueUsageCollector lValueUsageCollector);

    boolean canPushDownInto();

    Expression pushDown(Expression toPush, Expression parent);

    KnownJavaType getKnownType();
}
