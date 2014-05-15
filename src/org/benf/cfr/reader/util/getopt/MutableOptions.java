package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.Troolean;

import java.util.Map;

public class MutableOptions implements Options {
    private final Options delegate;

    private Map<String, String> overrides = MapFactory.newMap();

    public MutableOptions(Options delegate) {
        this.delegate = delegate;
    }

    public boolean override(PermittedOptionProvider.ArgumentParam<Troolean, Void> argument, Troolean value) {
        Troolean originalValue = delegate.getOption(argument);
        if (originalValue == Troolean.NEITHER) {
            overrides.put(argument.getName(), value.toString());
            return true;
        }
        return false;
    }

    public boolean override(PermittedOptionProvider.ArgumentParam<Boolean, Void> argument, boolean value) {
        Boolean originalValue = delegate.getOption(argument);
        if (originalValue != value) {
            overrides.put(argument.getName(), Boolean.toString(value));
            return true;
        }
        return false;
    }

    @Override
    public boolean optionIsSet(PermittedOptionProvider.ArgumentParam<?, ?> option) {
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
    public <T> T getOption(PermittedOptionProvider.ArgumentParam<T, Void> option) {
        String override = overrides.get(option.getName());
        if (override != null) {
            return option.getFn().invoke(override, null);
        }
        return delegate.getOption(option);
    }

    @Override
    public <T, A> T getOption(PermittedOptionProvider.ArgumentParam<T, A> option, A arg) {
        String override = overrides.get(option.getName());
        if (override != null) {
            return option.getFn().invoke(override, arg);
        }
        return delegate.getOption(option, arg);
    }
}
