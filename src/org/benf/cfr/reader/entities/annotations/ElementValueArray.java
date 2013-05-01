package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.util.output.CommaHelp;
import org.benf.cfr.reader.util.output.Dumper;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/03/2013
 * Time: 19:24
 */
public class ElementValueArray implements ElementValue {
    private final List<ElementValue> content;

    public ElementValueArray(List<ElementValue> content) {
        this.content = content;
    }

    @Override
    public Dumper dump(Dumper d) {
        d.print('{');
        boolean first = true;
        for (ElementValue value : content) {
            first = CommaHelp.comma(first, d);
            value.dump(d);
        }
        d.print('}');
        return d;
    }
}
