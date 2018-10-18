package org.benf.cfr.reader.util.getopt;

public interface Options {

    boolean optionIsSet(PermittedOptionProvider.ArgumentParam<?, ?> option);

    <T> T getOption(PermittedOptionProvider.ArgumentParam<T, Void> option);

    <T, A> T getOption(PermittedOptionProvider.ArgumentParam<T, A> option, A arg);
}
