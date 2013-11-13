package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.Troolean;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/11/2013
 * Time: 12:55
 */
public class MutableOptions implements Options {
    private final Options delegate;

    private Map<PermittedOptionProvider.Argument<Troolean, Options>, Troolean> overrides = MapFactory.newMap();

    public MutableOptions(Options delegate) {
        this.delegate = delegate;
    }

    public boolean override(PermittedOptionProvider.Argument<Troolean, Options> argument, boolean value) {
        Troolean originalValue = delegate.getTrooleanOpt(argument);
        if (originalValue == Troolean.NEITHER) {
            overrides.put(argument, Troolean.get(value));
            return true;
        }
        return false;
    }

    @Override
    public String getFileName() {
        return delegate.getFileName();
    }

    @Override
    public String getMethodName() {
        return delegate.getMethodName();
    }

    @Override
    public boolean getBooleanOpt(PermittedOptionProvider.Argument<Boolean, Options> argument) {
        return delegate.getBooleanOpt(argument);
    }

    @Override
    public boolean getBooleanOpt(PermittedOptionProvider.Argument<Boolean, ClassFileVersion> argument, ClassFileVersion classFileVersion) {
        return delegate.getBooleanOpt(argument, classFileVersion);
    }

    @Override
    public Troolean getTrooleanOpt(PermittedOptionProvider.Argument<Troolean, Options> argument) {
        Troolean override = overrides.get(argument);
        if (override != null) return override;
        return delegate.getTrooleanOpt(argument);
    }

    @Override
    public int getShowOps() {
        return delegate.getShowOps();
    }

    @Override
    public boolean isLenient() {
        return delegate.isLenient();
    }

    @Override
    public boolean hideBridgeMethods() {
        return delegate.hideBridgeMethods();
    }

    @Override
    public boolean analyseInnerClasses() {
        return delegate.analyseInnerClasses();
    }

    @Override
    public boolean removeBoilerplate() {
        return delegate.removeBoilerplate();
    }

    @Override
    public boolean removeInnerClassSynthetics() {
        return delegate.removeInnerClassSynthetics();
    }

    @Override
    public boolean rewriteLambdas(ClassFileVersion classFileVersion) {
        return delegate.rewriteLambdas(classFileVersion);
    }
}
