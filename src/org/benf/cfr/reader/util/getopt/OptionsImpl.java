package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.util.*;

import java.util.*;

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

        @Override
        public String getDefaultValue() {
            return "0";
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

        @Override
        public String getDefaultValue() {
            return null;
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

        @Override
        public String getDefaultValue() {
            return "true";
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

        @Override
        public String getDefaultValue() {
            return "false";
        }
    };
    private static final OptionDecoder<String> defaultNullStringDecoder = new OptionDecoder<String>() {
        @Override
        public String invoke(String arg, Void ignore) {
            return arg;
        }

        @Override
        public String getRangeDescription() {
            return "string";
        }

        @Override
        public String getDefaultValue() {
            return null;
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

        @Override
        public String getDefaultValue() {
            return "" + resultIfGreaterThanOrEqual + " if class file from version " + versionGreaterThanOrEqual + " or greater";
        }

    }

    private static final String CFR_WEBSITE = "http://www.benf.org/other/cfr/";

    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> SUGAR_STRINGBUFFER = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "stringbuffer", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, false),
            "Convert new Stringbuffer().add.add.add to string + string + string - see " + CFR_WEBSITE + "stringbuilder-vs-concatenation.html");
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> SUGAR_STRINGBUILDER = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "stringbuilder", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true),
            "Convert new Stringbuilder().add.add.add to string + string + string - see " + CFR_WEBSITE + "stringbuilder-vs-concatenation.html");
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> ENUM_SWITCH = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "decodeenumswitch", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true),
            "Re-sugar switch on enum - see " + CFR_WEBSITE + "switch-on-enum.html");
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> ENUM_SUGAR = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "sugarenums", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true),
            "Re-sugar enums - see " + CFR_WEBSITE + "how-are-enums-implemented.html");
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> STRING_SWITCH = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "decodestringswitch", new VersionSpecificDefaulter(ClassFileVersion.JAVA_7, true),
            "Re-sugar switch on String - see " + CFR_WEBSITE + "java7switchonstring.html");
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> ARRAY_ITERATOR = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "arrayiter", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true),
            "Re-sugar array based iteration.");
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> COLLECTION_ITERATOR = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "collectioniter", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true),
            "Re-sugar collection based iteration");
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> REWRITE_LAMBDAS = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "decodelambdas", new VersionSpecificDefaulter(ClassFileVersion.JAVA_8, true),
            "Re-build lambda functions");
    public static final PermittedOptionProvider.Argument<Boolean> DECOMPILE_INNER_CLASSES = new PermittedOptionProvider.Argument<Boolean>(
            "innerclasses", defaultTrueBooleanDecoder,
            "Decompile innter classes");
    public static final PermittedOptionProvider.Argument<Boolean> HIDE_UTF8 = new PermittedOptionProvider.Argument<Boolean>(
            "hideutf", defaultTrueBooleanDecoder,
            "Hide UTF8 characters - quote them instead of showing the raw characters");
    public static final PermittedOptionProvider.Argument<Boolean> HIDE_LONGSTRINGS = new PermittedOptionProvider.Argument<Boolean>(
            "hidelongstrings", defaultFalseBooleanDecoder,
            "Hide very long strings - useful if obfuscators have placed fake code in strings");
    public static final PermittedOptionProvider.Argument<Boolean> REMOVE_BOILERPLATE = new PermittedOptionProvider.Argument<Boolean>(
            "removeboilerplate", defaultTrueBooleanDecoder,
            "Remove boilderplate functions - constructor boilerplate, lambda deserialisation etc");
    public static final PermittedOptionProvider.Argument<Boolean> REMOVE_INNER_CLASS_SYNTHETICS = new PermittedOptionProvider.Argument<Boolean>(
            "removeinnerclasssynthetics", defaultTrueBooleanDecoder,
            "Remove (where possible) implicit outer class references in inner classes");
    public static final PermittedOptionProvider.Argument<Boolean> HIDE_BRIDGE_METHODS = new PermittedOptionProvider.Argument<Boolean>(
            "hidebridgemethods", defaultTrueBooleanDecoder,
            "Hide bridge methods");
    public static final PermittedOptionProvider.Argument<Boolean> LIFT_CONSTRUCTOR_INIT = new PermittedOptionProvider.Argument<Boolean>(
            "liftconstructorinit", defaultTrueBooleanDecoder,
            "Lift initialisation code common to all constructors into member initialisation");
    public static final PermittedOptionProvider.Argument<Boolean> REMOVE_DEAD_METHODS = new PermittedOptionProvider.Argument<Boolean>(
            "removedeadmethods", defaultTrueBooleanDecoder,
            "Remove pointless methods - default constructor etc");
    public static final PermittedOptionProvider.Argument<Boolean> REMOVE_BAD_GENERICS = new PermittedOptionProvider.Argument<Boolean>(
            "removebadgenerics", defaultTrueBooleanDecoder,
            "Hide generics where we've obviously got it wrong, and fallback to non-generic");
    public static final PermittedOptionProvider.Argument<Boolean> SUGAR_ASSERTS = new PermittedOptionProvider.Argument<Boolean>(
            "sugarasserts", defaultTrueBooleanDecoder,
            "Re-sugar assert calls");
    public static final PermittedOptionProvider.Argument<Boolean> SUGAR_BOXING = new PermittedOptionProvider.Argument<Boolean>(
            "sugarboxing", defaultTrueBooleanDecoder,
            "Where possible, remove pointless boxing wrappers");
    public static final PermittedOptionProvider.Argument<Boolean> SHOW_CFR_VERSION = new PermittedOptionProvider.Argument<Boolean>(
            "showversion", defaultTrueBooleanDecoder,
            "Show CFR version used in header (handy to turn off when regression testing)");
    public static final PermittedOptionProvider.Argument<Boolean> DECODE_FINALLY = new PermittedOptionProvider.Argument<Boolean>(
            "decodefinally", defaultTrueBooleanDecoder,
            "Re-sugar finally statements");
    public static final PermittedOptionProvider.Argument<Boolean> TIDY_MONITORS = new PermittedOptionProvider.Argument<Boolean>(
            "tidymonitors", defaultTrueBooleanDecoder,
            "Remove support code for monitors - eg catch blocks just to exit a monitor");
    public static final PermittedOptionProvider.Argument<Boolean> COMMENT_MONITORS = new PermittedOptionProvider.Argument<Boolean>(
            "commentmonitors", defaultFalseBooleanDecoder,
            "Replace monitors with comments - useful if we're completely confused");
    public static final PermittedOptionProvider.Argument<Boolean> LENIENT = new PermittedOptionProvider.Argument<Boolean>(
            "lenient", defaultFalseBooleanDecoder,
            "Be a bit more lenient in situations where we'd normally throw an exception"
    );
    public static final PermittedOptionProvider.Argument<Boolean> DUMP_CLASS_PATH = new PermittedOptionProvider.Argument<Boolean>(
            "dumpclasspath", defaultFalseBooleanDecoder,
            "Dump class path for debugging purposes");
    public static final PermittedOptionProvider.Argument<Boolean> DECOMPILER_COMMENTS = new PermittedOptionProvider.Argument<Boolean>(
            "comments", defaultTrueBooleanDecoder,
            "Output comments describing decompiler status, fallback flags etc");
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_TOPSORT = new PermittedOptionProvider.Argument<Troolean>(
            "forcetopsort", defaultNeitherTrooleanDecoder,
            "Force basic block sorting.  Usually not necessary for code emitted directly from javac, but required in the case of obfuscation (or dex2jar!).  Will be enabled in recovery.");
    public static final PermittedOptionProvider.Argument<Troolean> FOR_LOOP_CAPTURE = new PermittedOptionProvider.Argument<Troolean>(
            "forloopaggcapture", defaultNeitherTrooleanDecoder,
            "Allow for loops to aggresively roll mutations into update section, even if they don't appear to be involved with the predicate");
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_TOPSORT_EXTRA = new PermittedOptionProvider.Argument<Troolean>(
            "forcetopsortaggress", defaultNeitherTrooleanDecoder,
            "Force extra aggressive topsort options");
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_COND_PROPAGATE = new PermittedOptionProvider.Argument<Troolean>(
            "forcecondpropagate", defaultNeitherTrooleanDecoder,
            "Pull results of deterministic jumps back through some constant assignments");
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_RETURNING_IFS = new PermittedOptionProvider.Argument<Troolean>(
            "forcereturningifs", defaultNeitherTrooleanDecoder,
            "Move return up to jump site");
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_PRUNE_EXCEPTIONS = new PermittedOptionProvider.Argument<Troolean>(
            "forceexceptionprune", defaultNeitherTrooleanDecoder,
            "Try to extend and merge exceptions more aggressively");
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_AGGRESSIVE_EXCEPTION_AGG = new PermittedOptionProvider.Argument<Troolean>(
            "aexagg", defaultNeitherTrooleanDecoder,
            "Remove nested exception handlers if they don't change semantics");
    public static final PermittedOptionProvider.Argument<Troolean> RECOVER_TYPECLASHES = new PermittedOptionProvider.Argument<Troolean>(
            "recovertypeclash", defaultNeitherTrooleanDecoder,
            "Split lifetimes where analysis caused type clash");
    public static final PermittedOptionProvider.Argument<Troolean> USE_RECOVERED_ITERATOR_TYPE_HINTS = new PermittedOptionProvider.Argument<Troolean>(
            "recovertypehints", defaultNeitherTrooleanDecoder,
            "Recover type hints for iterators from first pass.");
    public static final PermittedOptionProvider.Argument<String> OUTPUT_DIR = new PermittedOptionProvider.Argument<String>(
            "outputdir", defaultNullStringDecoder,
            "Decompile to files in [directory]");
    public static final PermittedOptionProvider.Argument<Integer> SHOWOPS = new PermittedOptionProvider.Argument<Integer>(
            "showops", default0intDecoder,
            "Show some (cryptic!) debug");
    public static final PermittedOptionProvider.Argument<Boolean> SILENT = new PermittedOptionProvider.Argument<Boolean>(
            "silent", defaultFalseBooleanDecoder,
            "Don't display state while decompiling");
    public static final PermittedOptionProvider.Argument<Boolean> RECOVER = new PermittedOptionProvider.Argument<Boolean>(
            "recover", defaultTrueBooleanDecoder,
            "Allow more and more aggressive options to be set if decompilation fails");
    public static final PermittedOptionProvider.Argument<Boolean> ECLIPSE = new PermittedOptionProvider.Argument<Boolean>(
            "eclipse", defaultTrueBooleanDecoder,
            "Enable transformations to handle eclipse code better");
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> OVERRIDES = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "override", new VersionSpecificDefaulter(ClassFileVersion.JAVA_6, true),
            "Generate @Override annotations (if method is seen to implement interface method, or override a base class method)");
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> SHOW_INFERRABLE = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "showinferrable", new VersionSpecificDefaulter(ClassFileVersion.JAVA_7, false),
            "Decorate methods with explicit types if not implied by arguments.");
    public static final PermittedOptionProvider.Argument<String> HELP = new PermittedOptionProvider.Argument<String>(
            "help", defaultNullStringDecoder,
            "Show help for a given parameter");
    public static final PermittedOptionProvider.Argument<Boolean> ALLOW_CORRECTING = new PermittedOptionProvider.Argument<Boolean>(
            "allowcorrecting", defaultTrueBooleanDecoder,
            "Allow transformations which correct errors, potentially at the cost of altering emitted code behaviour.  An example would be removing impossible (in java!) exception handling - if this has any effect, a warning will be emitted.");
    public static final PermittedOptionProvider.Argument<Boolean> LABELLED_BLOCKS = new PermittedOptionProvider.Argument<Boolean>(
            "labelledblocks", defaultTrueBooleanDecoder,
            "Allow code to be emitted which uses labelled blocks, (handling odd forward gotos)");
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> JAVA_4_CLASS_OBJECTS = new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "j14classobj", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, false),
            "Reverse java 1.4 class object construction");
    public static final PermittedOptionProvider.Argument<Boolean> HIDE_LANG_IMPORTS = new PermittedOptionProvider.Argument<Boolean>(
            "hidelangimports", defaultTrueBooleanDecoder,
            "Hide imports from java.lang.");
    public static final PermittedOptionProvider.Argument<Integer> FORCE_PASS = new PermittedOptionProvider.Argument<Integer>(
            "recpass", default0intDecoder,
            "Decompile specifically with recovery options from pass #X. (really only useful for debugging)", true);
    public static final PermittedOptionProvider.Argument<String> ANALYSE_AS = new PermittedOptionProvider.Argument<String>(
            "analyseas", defaultNullStringDecoder,
            "Force file to be analysed as 'jar' or 'class'");


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
                    REMOVE_DEAD_METHODS, REMOVE_BAD_GENERICS, SUGAR_ASSERTS, SUGAR_BOXING, SHOW_CFR_VERSION,
                    DECODE_FINALLY, TIDY_MONITORS, LENIENT, DUMP_CLASS_PATH,
                    DECOMPILER_COMMENTS, FORCE_TOPSORT, FORCE_TOPSORT_EXTRA, FORCE_PRUNE_EXCEPTIONS, OUTPUT_DIR,
                    SUGAR_STRINGBUFFER, SUGAR_STRINGBUILDER, SILENT, RECOVER, ECLIPSE, OVERRIDES, SHOW_INFERRABLE,
                    FORCE_AGGRESSIVE_EXCEPTION_AGG, FORCE_COND_PROPAGATE, HIDE_UTF8, HIDE_LONGSTRINGS, COMMENT_MONITORS,
                    ALLOW_CORRECTING, LABELLED_BLOCKS, JAVA_4_CLASS_OBJECTS, HIDE_LANG_IMPORTS, FORCE_PASS,
                    RECOVER_TYPECLASHES, USE_RECOVERED_ITERATOR_TYPE_HINTS,
                    FORCE_RETURNING_IFS, ANALYSE_AS, FOR_LOOP_CAPTURE, HELP);
        }

        @Override
        public Options create(List<String> args, Map<String, String> opts) {
            String fname = null;
            String methodName = null;
            switch (args.size()) {
                case 0:
                    break;
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
