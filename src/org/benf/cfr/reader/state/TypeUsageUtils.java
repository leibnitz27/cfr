package org.benf.cfr.reader.state;

import org.benf.cfr.reader.bytecode.analysis.types.InnerClassInfo;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.util.ListFactory;
import org.benf.cfr.reader.util.MiscConstants;
import org.benf.cfr.reader.util.output.CommaHelp;

import java.util.LinkedList;

public class TypeUsageUtils {
    public static String generateInnerClassShortName(final JavaRefTypeInstance clazz, JavaRefTypeInstance analysisType) {
        LinkedList<JavaRefTypeInstance> classStack = ListFactory.newLinkedList();

        boolean analysisTypeFound = false;
        if (clazz.getRawName().startsWith(analysisType.getRawName())) {
            // In case we don't have full info.
            analysisTypeFound = true;
        }
        JavaRefTypeInstance currentClass = clazz;
        do {
            InnerClassInfo innerClassInfo = currentClass.getInnerClassHereInfo();
            if (!innerClassInfo.isAnonymousClass()) {
                classStack.addFirst(currentClass);
            }
            if (!innerClassInfo.isInnerClass()) {
                break;
            }
            currentClass = innerClassInfo.getOuterClass();
            if (currentClass.equals(analysisType)) {
                analysisTypeFound = true;
                break;  // We don't want to go any further back than here!
            }
        } while (true);
        /*
         * Local inner class.  We want the smallest postfix.  We can drop the local class, but because we're not doing
         * imports for local classes, we can't drop any more.
         */
        if (analysisTypeFound == currentClass.equals(analysisType)) {
            StringBuilder sb = new StringBuilder();
            boolean first = true;
            for (JavaRefTypeInstance stackClass : classStack) {
                first = CommaHelp.dot(first, sb);
                sb.append(stackClass.getRawShortName());
            }
            return sb.toString();
        } else {
            // string approximation.
            String clazzRawName = clazz.getRawName();
            // Cheat using $.
            String analysisTypeRawName = analysisType.getRawName();

            if (clazzRawName.equals(analysisTypeRawName)) {
                int idx = clazzRawName.lastIndexOf('.');
                if (idx >= 1 && idx < (clazzRawName.length() - 1)) {
                    return clazzRawName.substring(idx + 1);
                }
            }

            if (analysisTypeRawName.length() >= (clazzRawName.length() - 1)) {
                return clazzRawName;
            }
            return clazzRawName.substring(analysisType.getRawName().length() + 1);
        }
    }
}
