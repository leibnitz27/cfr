package org.benf.cfr.reader.entities.annotations;

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
    public void getTextInto(StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (ElementValue value : content) {
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            value.getTextInto(sb);
        }
        sb.append('}');
    }
}
