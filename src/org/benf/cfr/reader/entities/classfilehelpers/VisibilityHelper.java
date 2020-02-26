package org.benf.cfr.reader.entities.classfilehelpers;

import org.benf.cfr.reader.bytecode.analysis.types.BindingSuperContainer;
import org.benf.cfr.reader.bytecode.analysis.types.JavaRefTypeInstance;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;

public class VisibilityHelper {

    private static boolean isInnerVisibleTo(JavaTypeInstance maybeCaller, ClassFile classFile) {
        JavaRefTypeInstance thisClass = classFile.getRefClassType();
        /*
         * If this is an inner class of maybeCaller (or the other way around), it's allowed, otherwise
         * not.
         */
        if (maybeCaller.getInnerClassHereInfo().isTransitiveInnerClassOf(thisClass)) return true;
        if (thisClass.getInnerClassHereInfo().isTransitiveInnerClassOf(maybeCaller)) return true;
        return false;
    }

    public static boolean isVisibleTo(JavaRefTypeInstance maybeCaller, ClassFile classFile, boolean accPublic, boolean accPrivate, boolean accProtected) {
        if (accPublic) return true;
        if (maybeCaller.equals(classFile.getClassType())) return true;
        if (accPrivate) {
            return isInnerVisibleTo(maybeCaller, classFile);
        }
        if (accProtected) {
            // If it's derived, it can see it - if it's inner, it can see it.
            BindingSuperContainer bindingSuperContainer = maybeCaller.getBindingSupers();
            // paranoia.
            if (bindingSuperContainer == null) return false;
            if (bindingSuperContainer.containsBase(classFile.getClassType())) return true;
            return isInnerVisibleTo(maybeCaller, classFile);
        }
        // Otherwise, we're left with package visibility.
        if (maybeCaller.getPackageName().equals(classFile.getRefClassType().getPackageName())) return true;
        return false;
    }
}
