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
public class OptionsImpl implements Options {
    private final String fileName;    // Ugly because we confuse parameters and state.
    private final String methodName;  // Ugly because we confuse parameters and state.
    private final Map<String, String> opts;


    private static final BinaryFunction<String, Void, Integer> default0intDecoder = new BinaryFunction<String, Void, Integer>() {
        @Override
        public Integer invoke(String arg, Void ignore) {
            if (arg == null) return 0;
            int x = Integer.parseInt(arg);
            if (x < 0) throw new IllegalArgumentException("required int >= 0");
            return x;
        }
    };
    private static final BinaryFunction<String, Void, Troolean> defaultNeitherTrooleanDecoder = new BinaryFunction<String, Void, Troolean>() {
        @Override
        public Troolean invoke(String arg, Void ignore) {
            if (arg == null) return Troolean.NEITHER;
            return Troolean.get(Boolean.parseBoolean(arg));
        }
    };
    private static final BinaryFunction<String, Void, Boolean> defaultTrueBooleanDecoder = new BinaryFunction<String, Void, Boolean>() {
        @Override
        public Boolean invoke(String arg, Void ignore) {
            if (arg == null) return true;
            return Boolean.parseBoolean(arg);
        }
    };

    private static final BinaryFunction<String, Void, Boolean> defaultFalseBooleanDecoder = new BinaryFunction<String, Void, Boolean>() {
        @Override
        public Boolean invoke(String arg, Void ignore) {
            if (arg == null) return false;
            return Boolean.parseBoolean(arg);
        }
    };
    private static final BinaryFunction<String, Void, String> defaultNullStringDecoder = new BinaryFunction<String, Void, String>() {
        @Override
        public String invoke(String arg, Void ignore) {
            return arg;
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
    public static final PermittedOptionProvider.Argument<Boolean, Void> DECOMPILE_INNER_CLASSES = new PermittedOptionProvider.Argument<Boolean, Void>(
            "innerclasses", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> REMOVE_BOILERPLATE = new PermittedOptionProvider.Argument<Boolean, Void>(
            "removeboilerplate", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> REMOVE_INNER_CLASS_SYNTHETICS = new PermittedOptionProvider.Argument<Boolean, Void>(
            "removeinnerclasssynthetics", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> HIDE_BRIDGE_METHODS = new PermittedOptionProvider.Argument<Boolean, Void>(
            "hidebridgemethods", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> LIFT_CONSTRUCTOR_INIT = new PermittedOptionProvider.Argument<Boolean, Void>(
            "liftconstructorinit", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> REMOVE_DEAD_METHODS = new PermittedOptionProvider.Argument<Boolean, Void>(
            "removedeadmethods", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> REMOVE_BAD_GENERICS = new PermittedOptionProvider.Argument<Boolean, Void>(
            "removebadgenerics", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> SUGAR_ASSERTS = new PermittedOptionProvider.Argument<Boolean, Void>(
            "sugarasserts", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> SUGAR_BOXING = new PermittedOptionProvider.Argument<Boolean, Void>(
            "sugarboxing", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> SHOW_CFR_VERSION = new PermittedOptionProvider.Argument<Boolean, Void>(
            "showversion", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> HIDE_CASTS = new PermittedOptionProvider.Argument<Boolean, Void>(
            "hidecasts", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> DECODE_FINALLY = new PermittedOptionProvider.Argument<Boolean, Void>(
            "decodefinally", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> TIDY_MONITORS = new PermittedOptionProvider.Argument<Boolean, Void>(
            "tidymonitors", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> ALLOW_PARTIAL_FAILURE = new PermittedOptionProvider.Argument<Boolean, Void>(
            "allowpartialfailure", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> ALLOW_WHOLE_FAILURE = new PermittedOptionProvider.Argument<Boolean, Void>(
            "allowwholefailure", defaultFalseBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> LENIENT = new PermittedOptionProvider.Argument<Boolean, Void>(
            "lenient", defaultFalseBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> DUMP_CLASS_PATH = new PermittedOptionProvider.Argument<Boolean, Void>(
            "dumpclasspath", defaultFalseBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean, Void> DECOMPILER_COMMENTS = new PermittedOptionProvider.Argument<Boolean, Void>(
            "comments", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Troolean, Void> FORCE_TOPSORT = new PermittedOptionProvider.Argument<Troolean, Void>(
            "forcetopsort", defaultNeitherTrooleanDecoder);
    public static final PermittedOptionProvider.Argument<Troolean, Void> FORCE_PRUNE_EXCEPTIONS = new PermittedOptionProvider.Argument<Troolean, Void>(
            "forceexceptionprune", defaultNeitherTrooleanDecoder);
    public static final PermittedOptionProvider.Argument<String, Void> OUTPUT_DIR = new PermittedOptionProvider.Argument<String, Void>(
            "outputdir", defaultNullStringDecoder);
    public static final PermittedOptionProvider.Argument<Integer, Void> SHOWOPS = new PermittedOptionProvider.Argument<Integer, Void>(
            "showops", default0intDecoder);


    public OptionsImpl(String fileName, String methodName, Map<String, String> opts) {
        this.fileName = fileName;
        this.methodName = methodName;
        this.opts = opts;
    }

    @Override
    public String getFileName() {
        return fileName;
    }

    @Override
    public String getMethodName() {
        return methodName;
    }

    @Override
    public <T> T getOption(PermittedOptionProvider.Argument<T, Void> option) {
        return option.getFn().invoke(opts.get(option.getName()), null);
    }

    @Override
    public <T, A> T getOption(PermittedOptionProvider.Argument<T, A> option, A arg) {
        return option.getFn().invoke(opts.get(option.getName()), arg);
    }

    @Override
    public boolean optionIsSet(PermittedOptionProvider.Argument<?, ?> option) {
        return opts.get(option.getName()) != null;
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
                    DECOMPILER_COMMENTS, ALLOW_WHOLE_FAILURE, FORCE_TOPSORT, FORCE_PRUNE_EXCEPTIONS, OUTPUT_DIR);
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
            return new OptionsImpl(fname, methodName, opts);
        }
    }

}
