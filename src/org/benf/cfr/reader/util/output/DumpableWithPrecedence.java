package org.benf.cfr.reader.util.output;

import org.benf.cfr.reader.bytecode.analysis.parse.expression.misc.Precedence;
import org.benf.cfr.reader.util.Troolean;

public interface DumpableWithPrecedence extends Dumpable {

    Precedence getPrecedence();

    Dumper dumpWithOuterPrecedence(Dumper d, Precedence outerPrecedence, Troolean isLhs);

}
