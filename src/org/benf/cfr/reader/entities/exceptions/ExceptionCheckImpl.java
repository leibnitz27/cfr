package org.benf.cfr.reader.entities.exceptions;

import org.benf.cfr.reader.bytecode.analysis.parse.Expression;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.AbstractMemberFunctionInvokation;
import org.benf.cfr.reader.bytecode.analysis.parse.expression.ConstructorInvokationSimple;
import org.benf.cfr.reader.bytecode.analysis.types.*;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.CannotLoadClassException;
import org.benf.cfr.reader.util.collections.SetFactory;
import org.benf.cfr.reader.util.collections.SetUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

public class ExceptionCheckImpl implements ExceptionCheck {
    private final Set<JavaRefTypeInstance> caughtChecked = SetFactory.newSet();
    private final Set<JavaRefTypeInstance> caughtUnchecked = SetFactory.newSet();
    private final boolean mightUseUnchecked;
    private final boolean missingInfo;
    private final DCCommonState dcCommonState;
    private final JavaRefTypeInstance runtimeExceptionType;

    public ExceptionCheckImpl(DCCommonState dcCommonState, Set<JavaRefTypeInstance> caught) {
        this.dcCommonState = dcCommonState;
        runtimeExceptionType = dcCommonState.getClassTypeOrNull(TypeConstants.runtimeExceptionPath);
        if (runtimeExceptionType == null) {
            mightUseUnchecked = true;
            missingInfo = true;
            return;
        }

        boolean lmightUseUnchecked = false;
        boolean lmissinginfo = false;
        for (JavaRefTypeInstance ref : caught) {
            BindingSuperContainer superContainer = ref.getBindingSupers();
            if (superContainer == null) {
                lmightUseUnchecked = true;
                lmissinginfo = true;
                continue;
            }
            Map<JavaRefTypeInstance, ?> supers = superContainer.getBoundSuperClasses();
            if (supers == null) {
                lmightUseUnchecked = true;
                lmissinginfo = true;
                continue;
            }
            if (supers.containsKey(runtimeExceptionType)) {
                lmightUseUnchecked = true;
                caughtUnchecked.add(ref);
            } else {
                caughtChecked.add(ref);
            }
        }
        mightUseUnchecked = lmightUseUnchecked;
        missingInfo = lmissinginfo;
    }

    private boolean checkAgainstInternal(Set<? extends JavaTypeInstance> thrown) {
        if (thrown.isEmpty()) return false;

        for (JavaTypeInstance thrownType : thrown) {
            try {
                ClassFile thrownClassFile = dcCommonState.getClassFile(thrownType);
                if (thrownClassFile == null) return true;
                BindingSuperContainer bindingSuperContainer = thrownClassFile.getBindingSupers();
                if (bindingSuperContainer == null) return true;
                Map<JavaRefTypeInstance, ?> boundSuperClasses = bindingSuperContainer.getBoundSuperClasses();
                if (boundSuperClasses == null) return true;
                if (SetUtil.hasIntersection(caughtChecked, boundSuperClasses.keySet())) return true;
            } catch (CannotLoadClassException e) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean checkAgainst(Set<? extends JavaTypeInstance> thrown) {
        try {
            return checkAgainstInternal(thrown);
        } catch (Exception e) {
            return true;
        }
    }

    // Might this throw in a way which means it can't be moved into the exception block?
    @Override
    public boolean checkAgainst(AbstractMemberFunctionInvokation functionInvokation) {
        if (mightUseUnchecked) return true;
        JavaTypeInstance type = functionInvokation.getClassTypeInstance();
        try {
            ClassFile classFile = dcCommonState.getClassFile(type);
            Method method = classFile.getMethodByPrototype(functionInvokation.getMethodPrototype());
            return checkAgainstInternal(method.getThrownTypes());
        } catch (NoSuchMethodException e) {
            return true;
        } catch (CannotLoadClassException e) {
            return true;
        }
    }

    @Override
    public boolean checkAgainstException(Expression expression) {
        if (missingInfo) return true;
        /*
         * If this exception is a new checked, then see if we're catching it.  If it's not a checked
         * or we can't tell what it is, default to not being able to handle it.
         */
        if (!(expression instanceof ConstructorInvokationSimple)) return true;
        ConstructorInvokationSimple constructorInvokation = (ConstructorInvokationSimple) expression;
        JavaTypeInstance type = constructorInvokation.getTypeInstance();
        Map<JavaRefTypeInstance, ?> boundSuperClasses = null;
        try {
            ClassFile classFile = dcCommonState.getClassFile(type);
            if (classFile == null) return true;
            BindingSuperContainer bindingSuperContainer = classFile.getBindingSupers();
            if (bindingSuperContainer == null) return true;
            boundSuperClasses = bindingSuperContainer.getBoundSuperClasses();
            if (boundSuperClasses == null) return true;
            // This is a runtime exception, we're not catching those, cool.
        } catch (CannotLoadClassException e) {
            return true;
        }
        /*
         * Ok, if we're catching runtime exceptions,
         */
        Collection<JavaRefTypeInstance> throwingBases = boundSuperClasses.keySet();
        if (SetUtil.hasIntersection(caughtChecked, throwingBases)) return true;
        if (SetUtil.hasIntersection(caughtUnchecked, throwingBases)) return true;
        return false;
    }

    @Override
    public boolean mightCatchUnchecked() {
        return mightUseUnchecked;
    }
}
