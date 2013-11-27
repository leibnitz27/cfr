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


    private static final OptionDecoder<Integer> default0intDecoder = new OptionDecoder<Integer>() {
        @Override
        public Integer invoke(String arg, Void ignore) {
            if (arg == null) return 0;
            int x = Integer.parseInt(arg);
            if (x < 0) throw new IllegalArgumentException("required int >= 0");
            return x;
        }

        @Override
        public String getRangeDescription() {
            return "int >= 0";
        }
    };
    private static final OptionDecoder<Troolean> defaultNeitherTrooleanDecoder = new OptionDecoder<Troolean>() {
        @Override
        public Troolean invoke(String arg, Void ignore) {
            if (arg == null) return Troolean.NEITHER;
            return Troolean.get(Boolean.parseBoolean(arg));
        }

        @Override
        public String getRangeDescription() {
            return "boolean";
        }
    };
    private static final OptionDecoder<Boolean> defaultTrueBooleanDecoder = new OptionDecoder<Boolean>() {
        @Override
        public Boolean invoke(String arg, Void ignore) {
            if (arg == null) return true;
            return Boolean.parseBoolean(arg);
        }

        @Override
        public String getRangeDescription() {
            return "boolean";
        }
    };
    private static final OptionDecoder<Boolean> defaultFalseBooleanDecoder = new OptionDecoder<Boolean>() {
        @Override
        public Boolean invoke(String arg, Void ignore) {
            if (arg == null) return false;
            return Boolean.parseBoolean(arg);
        }

        @Override
        public String getRangeDescription() {
            return "boolean";
        }
    };
    private static final OptionDecoder<String> defaultNullStringDecoder = new OptionDecoder<String>() {
        @Override
        public String invoke(String arg, Void ignore) {
            return arg;
        }

        @Override
        public String getRangeDescription() {
            return "";
        }
    };

    private static class VersionSpecificDefaulter implements OptionDecoderParam<Boolean, ClassFileVersion> {

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

        @Override
        public String getRangeDescription() {
            return "boolean";
        }
    }

    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> SUGAR_STRINGBUFFER = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "stringbuffer", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, false));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> SUGAR_STRINGBUILDER = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "stringbuilder", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> ENUM_SWITCH = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "decodeenumswitch", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> ENUM_SUGAR = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "sugarenums", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> STRING_SWITCH = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "decodestringswitch", new VersionSpecificDefaulter(ClassFileVersion.JAVA_7, true));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> ARRAY_ITERATOR = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "arrayiter", new VersionSpecificDefaulter(ClassFileVersion.JAVA_6, true));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> COLLECTION_ITERATOR = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "collectioniter", new VersionSpecificDefaulter(ClassFileVersion.JAVA_6, true));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> REWRITE_LAMBDAS = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "decodelambdas", new VersionSpecificDefaulter(ClassFileVersion.JAVA_8, true));
    public static final PermittedOptionProvider.Argument<Boolean> DECOMPILE_INNER_CLASSES = new PermittedOptionProvider.Argument<Boolean>(
            "innerclasses", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> REMOVE_BOILERPLATE = new PermittedOptionProvider.Argument<Boolean>(
            "removeboilerplate", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> REMOVE_INNER_CLASS_SYNTHETICS = new PermittedOptionProvider.Argument<Boolean>(
            "removeinnerclasssynthetics", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> HIDE_BRIDGE_METHODS = new PermittedOptionProvider.Argument<Boolean>(
            "hidebridgemethods", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> LIFT_CONSTRUCTOR_INIT = new PermittedOptionProvider.Argument<Boolean>(
            "liftconstructorinit", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> REMOVE_DEAD_METHODS = new PermittedOptionProvider.Argument<Boolean>(
            "removedeadmethods", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> REMOVE_BAD_GENERICS = new PermittedOptionProvider.Argument<Boolean>(
            "removebadgenerics", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> SUGAR_ASSERTS = new PermittedOptionProvider.Argument<Boolean>(
            "sugarasserts", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> SUGAR_BOXING = new PermittedOptionProvider.Argument<Boolean>(
            "sugarboxing", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> SHOW_CFR_VERSION = new PermittedOptionProvider.Argument<Boolean>(
            "showversion", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> HIDE_CASTS = new PermittedOptionProvider.Argument<Boolean>(
            "hidecasts", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> DECODE_FINALLY = new PermittedOptionProvider.Argument<Boolean>(
            "decodefinally", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> TIDY_MONITORS = new PermittedOptionProvider.Argument<Boolean>(
            "tidymonitors", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> LENIENT = new PermittedOptionProvider.Argument<Boolean>(
            "lenient", defaultFalseBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> DUMP_CLASS_PATH = new PermittedOptionProvider.Argument<Boolean>(
            "dumpclasspath", defaultFalseBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> DECOMPILER_COMMENTS = new PermittedOptionProvider.Argument<Boolean>(
            "comments", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_TOPSORT = new PermittedOptionProvider.Argument<Troolean>(
            "forcetopsort", defaultNeitherTrooleanDecoder);
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_PRUNE_EXCEPTIONS = new PermittedOptionProvider.Argument<Troolean>(
            "forceexceptionprune", defaultNeitherTrooleanDecoder);
    public static final PermittedOptionProvider.Argument<String> OUTPUT_DIR = new PermittedOptionProvider.Argument<String>(
            "outputdir", defaultNullStringDecoder);
    public static final PermittedOptionProvider.Argument<Integer> SHOWOPS = new PermittedOptionProvider.Argument<Integer>(
            "showops", default0intDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> SILENT = new PermittedOptionProvider.Argument<Boolean>(
            "silent", defaultFalseBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> RECOVER = new PermittedOptionProvider.Argument<Boolean>(
            "recover", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.Argument<Boolean> ECLIPSE = new PermittedOptionProvider.Argument<Boolean>(
            "eclipse", defaultTrueBooleanDecoder);
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> OVERRIDES = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "override", new VersionSpecificDefaulter(ClassFileVersion.JAVA_6, true));


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
    public <T> T getOption(PermittedOptionProvider.ArgumentParam<T, Void> option) {
        return option.getFn().invoke(opts.get(option.getName()), null);
    }

    @Override
    public <T, A> T getOption(PermittedOptionProvider.ArgumentParam<T, A> option, A arg) {
        return option.getFn().invoke(opts.get(option.getName()), arg);
    }

    @Override
    public boolean optionIsSet(PermittedOptionProvider.ArgumentParam<?, ?> option) {
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
        public List<? extends ArgumentParam<?, ?>> getArguments() {
            return ListFactory.newList(SHOWOPS, ENUM_SWITCH, ENUM_SUGAR, STRING_SWITCH, ARRAY_ITERATOR,
                    COLLECTION_ITERATOR, DECOMPILE_INNER_CLASSES, REMOVE_BOILERPLATE,
                    REMOVE_INNER_CLASS_SYNTHETICS, REWRITE_LAMBDAS, HIDE_BRIDGE_METHODS, LIFT_CONSTRUCTOR_INIT,
                    REMOVE_DEAD_METHODS, REMOVE_BAD_GENERICS, SUGAR_ASSERTS, SUGAR_BOXING, HIDE_CASTS, SHOW_CFR_VERSION,
                    DECODE_FINALLY, TIDY_MONITORS, LENIENT, DUMP_CLASS_PATH,
                    DECOMPILER_COMMENTS, FORCE_TOPSORT, FORCE_PRUNE_EXCEPTIONS, OUTPUT_DIR,
                    SUGAR_STRINGBUFFER, SUGAR_STRINGBUILDER, SILENT, RECOVER, ECLIPSE, OVERRIDES);
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
