package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.util.*;
import org.benf.cfr.reader.util.functors.BinaryFunction;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: lee
 * Date: 01/02/2013
 * Time: 16:29
 */
public class Options {
    private final String fileName;    // Ugly because we confuse parameters and state.
    private final String methodName;  // Ugly because we confuse parameters and state.
    private final Map<String, String> opts;

    private static final PermittedOptionProvider.Argument<Integer, Options> SHOWOPS = new PermittedOptionProvider.Argument<Integer, Options>(
            "showops",
            new BinaryFunction<String, Options, Integer>() {
                @Override
                public Integer invoke(String arg, Options state) {
                    if (arg == null) return 0;
                    int x = Integer.parseInt(arg);
                    if (x < 0) throw new IllegalArgumentException("required int >= 0");
                    return x;
                }
            }
    );
    private static final BinaryFunction<String, Options, Troolean> defaultNeitherTrooleanDecoder = new BinaryFunction<String, Options, Troolean>() {
        @Override
        public Troolean invoke(String arg, Options ignore) {
            if (arg == null) return Troolean.NEITHER;
            return Troolean.get(Boolean.parseBoolean(arg));
        }
    };
    private static final BinaryFunction<String, Options, Boolean> defaultTrueBooleanDecoder = new BinaryFunction<String, Options, Boolean>() {
        @Override
        public Boolean invoke(String arg, Options ignore) {
            if (arg == null) return true;
            return Boolean.parseBoolean(arg);
        }
    };

    private static final BinaryFunction<String, Options, Boolean> defaultFalseBooleanDecoder = new BinaryFunction<String, Options, Boolean>() {
        @Override
        public Boolean invoke(String arg, Options ignore) {
            if (arg == null) return false;
            return Boolean.parseBoolean(arg);
        }
    };

    private static class VersionSpecificDefaulter implements BinaryFunction<String, ClassFileVersion, Boolean> {

        public ClassFileVersion versionGreaterThanOrEqual;
        public boolean resultIfGreaterThanOrEqual;

        private VersionSpecificDefaulter(ClassFileVersion versionGreaterThanOrEqual, boolean resultIfGreaterThanOrEqual) {
            this.versionGreaterThanOrEqual = versionGreaterThanOrEqual;
            this.resultIfGreaterThanOrEqual = resultIfGreaterThanOrEqual;
        }

        @Override
        public Boolean invoke(String arg, ClassFileVersion classFileVersion) {
            if (arg != null) return Boolean.parseBoolean(arg);
            if (classFileVersion == null) throw new IllegalStateException(); // ho ho ho.
            return (classFileVersion.equalOrLater(versionGreaterThanOrEqual)) ? resultIfGreaterThanOrEqual : !resultIfGreaterThanOrEqual;
        }
    }

    public static final PermittedOptionProvider.Argument<Boolean, ClassFileVersion> ENUM_SWITCH = new PermittedOptionProvider.Argument<Boolean, ClassFileVersion>(
            "decodeenumswitch", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true));
    public static final PermittedOptionProvider.Argument<Boolean, ClassFileVersion> ENUM_SUGAR = new PermittedOptionProvider.Argument<Boolean, ClassFileVersion>(
            "sugarenums", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true));
    public static final PermittedOptionProvider.Argument<Boolean, ClassFileVersion> STRING_SWITCH = new PermittedOptionProvider.Argument<Boolean, ClassFileVersion>(
            "decodestringswitch", new VersionSpecificDefaulter(ClassFileVersion.JAVA_7, true));
    public static final PermittedOptionProvider.Argument<Boolean, ClassFileVersion> ARRAY_ITERATOR = new PermittedOptionProvider.Argument<Boolean, ClassFileVersion>(
            "arrayiter", new VersionSpecificDefaulter(ClassFileVersion.JAVA_6, true));
    public static final PermittedOptionProvider.Argument<Boolean, ClassFileVersion> COLLECTION_ITERATOR = new PermittedOptionProvider.Argument<Boolean, ClassFileVersion>(
            "collectioniter", new VersionSpecificDefaulter(ClassFileVersion.JAVA_6, true));
    public static final PermittedOptionProvider.Argument<Boolean, ClassFileVersion> REWRITE_LAMBDAS = new PermittedOptionProvider.Argument<Boolean, ClassFileVersion>(
            "decodelambdas", new VersionSpecificDefaulter(ClassFileVersion.JAVA_8, true));
    public static final PermittedOptionProvider.Argument<Boolean, Options> DECOMPILE_INNER_CLASSES = new PermittedOptionProvider.Argument<Boolean, Options>(
            "innerclasses", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> REMOVE_BOILERPLATE = new PermittedOptionProvider.Argument<Boolean, Options>(
            "removeboilerplate", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> REMOVE_INNER_CLASS_SYNTHETICS = new PermittedOptionProvider.Argument<Boolean, Options>(
            "removeinnerclasssynthetics", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> HIDE_BRIDGE_METHODS = new PermittedOptionProvider.Argument<Boolean, Options>(
            "hidebridgemethods", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> LIFT_CONSTRUCTOR_INIT = new PermittedOptionProvider.Argument<Boolean, Options>(
            "liftconstructorinit", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> REMOVE_DEAD_METHODS = new PermittedOptionProvider.Argument<Boolean, Options>(
            "removedeadmethods", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> REMOVE_BAD_GENERICS = new PermittedOptionProvider.Argument<Boolean, Options>(
            "removebadgenerics", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> SUGAR_ASSERTS = new PermittedOptionProvider.Argument<Boolean, Options>(
            "sugarasserts", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> SUGAR_BOXING = new PermittedOptionProvider.Argument<Boolean, Options>(
            "sugarboxing", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> SHOW_CFR_VERSION = new PermittedOptionProvider.Argument<Boolean, Options>(
            "showversion", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> HIDE_CASTS = new PermittedOptionProvider.Argument<Boolean, Options>(
            "hidecasts", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> DECODE_FINALLY = new PermittedOptionProvider.Argument<Boolean, Options>(
            "decodefinally", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> TIDY_MONITORS = new PermittedOptionProvider.Argument<Boolean, Options>(
            "tidymonitors", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> ALLOW_PARTIAL_FAILURE = new PermittedOptionProvider.Argument<Boolean, Options>(
            "allowpartialfailure", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> ALLOW_WHOLE_FAILURE = new PermittedOptionProvider.Argument<Boolean, Options>(
            "allowwholefailure", defaultFalseBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> LENIENT = new PermittedOptionProvider.Argument<Boolean, Options>(
            "lenient", defaultFalseBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> DUMP_CLASS_PATH = new PermittedOptionProvider.Argument<Boolean, Options>(
            "dumpclasspath", defaultFalseBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Options> DECOMPILER_COMMENTS = new PermittedOptionProvider.Argument<Boolean, Options>(
            "comments", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Troolean, Options> FORCE_TOPSORT = new PermittedOptionProvider.Argument<Troolean, Options>(
            "forcetopsort", defaultNeitherTrooleanDecoder);

    public Options(String fileName, String methodName, Map<String, String> opts) {
        this.fileName = fileName;
        this.methodName = methodName;
        this.opts = opts;
    }

    public String getFileName() {
        return fileName;
    }

    public String getMethodName() {
        return methodName;
    }

    public boolean getBooleanOpt(PermittedOptionProvider.Argument<Boolean, Options> argument) {
        return argument.getFn().invoke(opts.get(argument.getName()), this);
    }

    public boolean getBooleanOpt(PermittedOptionProvider.Argument<Boolean, ClassFileVersion> argument, ClassFileVersion classFileVersion) {
        return argument.getFn().invoke(opts.get(argument.getName()), classFileVersion);
    }

    public Troolean getTrooleanOpt(PermittedOptionProvider.Argument<Troolean, Options> argument) {
        return argument.getFn().invoke(opts.get(argument.getName()), this);
    }

    public int getShowOps() {
        return SHOWOPS.getFn().invoke(opts.get(SHOWOPS.getName()), this);
    }

    public boolean isLenient() {
        return getBooleanOpt(LENIENT);
    }

    public boolean hideBridgeMethods() {
        return getBooleanOpt(HIDE_BRIDGE_METHODS);
    }

    public boolean analyseInnerClasses() {
        return getBooleanOpt(DECOMPILE_INNER_CLASSES);
    }

    public boolean removeBoilerplate() {
        return getBooleanOpt(REMOVE_BOILERPLATE);
    }

    public boolean removeInnerClassSynthetics() {
        return getBooleanOpt(REMOVE_INNER_CLASS_SYNTHETICS);
    }

    public boolean rewriteLambdas(ClassFileVersion classFileVersion) {
        return getBooleanOpt(REWRITE_LAMBDAS, classFileVersion);
    }

    public static GetOptSinkFactory<Options> getFactory() {
        return new CFRFactory();
    }

    private static class CFRFactory implements GetOptSinkFactory<Options> {
        @Override
        public List<String> getFlags() {
            return ListFactory.newList();
        }

        @Override
        @SuppressWarnings("unchecked")
        public List<? extends Argument<?, ?>> getArguments() {
            return ListFactory.newList(SHOWOPS, ENUM_SWITCH, ENUM_SUGAR, STRING_SWITCH, ARRAY_ITERATOR,
                    COLLECTION_ITERATOR, DECOMPILE_INNER_CLASSES, REMOVE_BOILERPLATE,
                    REMOVE_INNER_CLASS_SYNTHETICS, REWRITE_LAMBDAS, HIDE_BRIDGE_METHODS, LIFT_CONSTRUCTOR_INIT,
                    REMOVE_DEAD_METHODS, REMOVE_BAD_GENERICS, SUGAR_ASSERTS, SUGAR_BOXING, HIDE_CASTS, SHOW_CFR_VERSION,
                    DECODE_FINALLY, TIDY_MONITORS, ALLOW_PARTIAL_FAILURE, LENIENT, DUMP_CLASS_PATH,
                    DECOMPILER_COMMENTS, ALLOW_WHOLE_FAILURE, FORCE_TOPSORT);
        }

        @Override
        public Options create(List<String> args, Map<String, String> opts) {
            String fname;
            String methodName = null;
            switch (args.size()) {
                case 0:
                    throw new BadParametersException("Insufficient parameters", this);
                case 1:
                    fname = args.get(0);
                    break;
                case 2:
                    fname = args.get(0);
                    methodName = args.get(1);
                    break;
                default:
                    throw new BadParametersException("Too many unqualified parameters", this);
            }
            return new Options(fname, methodName, opts);
        }
    }

}
