package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.Troolean;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 13/11/2013
 * Time: 12:55
 */
public interface Options {
    String getFileName();

    String getMethodName();

    boolean optionIsSet(PermittedOptionProvider.ArgumentParam<?, ?> option);

    <T> T getOption(PermittedOptionProvider.ArgumentParam<T, Void> option);

    <T, A> T getOption(PermittedOptionProvider.ArgumentParam<T, A> option, A arg);
}
