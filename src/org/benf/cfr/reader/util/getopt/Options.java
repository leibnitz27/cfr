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

    boolean optionIsSet(PermittedOptionProvider.Argument<?, ?> option);

    <T> T getOption(PermittedOptionProvider.Argument<T, ?> option);

    boolean getBooleanOpt(PermittedOptionProvider.Argument<Boolean, Options> argument);

    boolean getBooleanOpt(PermittedOptionProvider.Argument<Boolean, ClassFileVersion> argument, ClassFileVersion classFileVersion);

    Troolean getTrooleanOpt(PermittedOptionProvider.Argument<Troolean, ?> argument);

    int getShowOps();

    boolean isLenient();

    boolean hideBridgeMethods();

    boolean analyseInnerClasses();

    boolean removeBoilerplate();

    boolean removeInnerClassSynthetics();

    boolean rewriteLambdas(ClassFileVersion classFileVersion);
}
