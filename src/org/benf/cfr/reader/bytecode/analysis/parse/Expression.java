package org.benf.cfr.reader.bytecode.analysis.parse;

import org.benf.cfr.reader.bytecode.analysis.parse.utils.LValueCollector;

import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: lee
 * Date: 15/03/2012
 * Time: 18:03
 * To change this template use File | Settings | File Templates.
 */
public interface Expression {
    Expression replaceSingleUsageLValues(LValueCollector lValueCollector);
    boolean isSimple();
}
