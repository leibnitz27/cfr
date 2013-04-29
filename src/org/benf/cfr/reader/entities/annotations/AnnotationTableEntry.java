package org.benf.cfr.reader.entities.annotations;

import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ConstantPool;
import org.benf.cfr.reader.util.output.CommaHelp;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 14/03/2013
 * Time: 06:36
 */
public class AnnotationTableEntry {
    private final JavaTypeInstance clazz;
    private final Map<String, ElementValue> elementValueMap;

    public AnnotationTableEntry(JavaTypeInstance clazz, Map<String, ElementValue> elementValueMap) {
        this.clazz = clazz;
        this.elementValueMap = elementValueMap;
    }

    public void getTextInto(StringBuilder sb) {
        sb.append('@').append(clazz.toString());
        if (elementValueMap != null && !elementValueMap.isEmpty()) {
            sb.append('(');
            boolean first = true;
            for (Map.Entry<String, ElementValue> elementValueEntry : elementValueMap.entrySet()) {
                first = CommaHelp.comma(first, sb);
                sb.append(elementValueEntry.getKey()).append('=');
                elementValueEntry.getValue().getTextInto(sb);
            }
            sb.append(')');
        }
    }
}
