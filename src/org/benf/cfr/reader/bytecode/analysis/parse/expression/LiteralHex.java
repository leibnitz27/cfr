package org.benf.cfr.reader.bytecode.analysis.parse.expression;

import org.benf.cfr.reader.bytecode.analysis.parse.literal.TypedLiteral;
import org.benf.cfr.reader.util.output.Dumper;

/*
 * It's debatable whether we should have this, or make it a temporary state of the dumper, such
 * that we dump literals immediately under a bitwise operation as hex.
 *
 * Currently, have gone for this, as the maintenance to force a reset if we have
 * 0xff | (0xf + foo(12, 23, 14))
 * means we would need to undo any dumper state not just on the way back up, but also when we left
 * literals immediately inside the tree.  This feels like it's a lot more work than it's worth.
 *
 * Doing things this way also means that if we eventually expose this class, we can allow a UI to tweak
 * this node... ;)
 */
public class LiteralHex extends Literal {

    public LiteralHex(TypedLiteral value) {
        super(value);
    }

    @Override
    public Dumper dumpInner(Dumper d) {
        return value.dumpWithHint(d, TypedLiteral.FormatHint.Hex);
    }
}
