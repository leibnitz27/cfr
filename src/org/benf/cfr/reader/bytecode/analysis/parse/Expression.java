package org.benf.cfr.reader.bytecode.analysis.parse;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.SSAIdentifiers;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/03/2012
 */
public interface Expression {
    Expression replaceSingleUsageLValues(LValueCollector lValueCollector, SSAIdentifiers ssaIdentifiers);

    boolean isSimple();
}
