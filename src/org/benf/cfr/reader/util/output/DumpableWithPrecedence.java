package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 06/03/2014
 * Time: 08:03
 */
public interface DumpableWithPrecedence extends Dumpable {

    Precedence getPrecedence();

    Dumper dumpWithOuterPrecedence(Dumper d, Precedence outerPrecedence);

}
