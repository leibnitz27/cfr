package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.SetFactory;
import org.benf.cfr.reader.util.Troolean;

import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/11/2013
 * Time: 12:55
 */
public class MutableOptions implements Options {
    private final Options delegate;

    private Map<String, String> overrides = MapFactory.newMap();

    public MutableOptions(Options delegate) {
        this.delegate = delegate;
    }

    public boolean override(PermittedOptionProvider.Argument<Troolean, Void> argument, boolean value) {
        Troolean originalValue = delegate.getOption(argument);
        if (originalValue == Troolean.NEITHER) {
            overrides.put(argument.getName(), Troolean.get(value).toString());
            return true;
        }
        return false;
    }

    @Override
    public boolean optionIsSet(PermittedOptionProvider.Argument<?, ?> option) {
        if (overrides.containsKey(option.getName())) return true;
        return delegate.optionIsSet(option);
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
    public <T> T getOption(PermittedOptionProvider.Argument<T, Void> option) {
        String override = overrides.get(option.getName());
        if (override != null) {
            return option.getFn().invoke(override, null);
        }
        return delegate.getOption(option);
    }

    @Override
    public <T, A> T getOption(PermittedOptionProvider.Argument<T, A> option, A arg) {
        String override = overrides.get(option.getName());
        if (override != null) {
            return option.getFn().invoke(override, arg);
        }
        return delegate.getOption(option, arg);
    }
}
