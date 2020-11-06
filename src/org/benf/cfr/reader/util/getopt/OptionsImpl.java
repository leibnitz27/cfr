package org.benf.cfr.reader.util.getopt;

import org.benf.cfr.reader.state.OsInfo;
import org.benf.cfr.reader.util.AnalysisType;
import org.benf.cfr.reader.util.ClassFileVersion;
import org.benf.cfr.reader.util.StringUtils;
import org.benf.cfr.reader.util.Troolean;
import org.benf.cfr.reader.util.collections.ListFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OptionsImpl implements Options {
    private final Map<String, String> opts;

    private static class DefaultingIntDecoder implements OptionDecoder<Integer> {
        final Integer defaultValue;

        private DefaultingIntDecoder(Integer defaultValue) {
            this.defaultValue = defaultValue;
        }

        @Override
        public Integer invoke(String arg, Void ignore, Options ignore2) {
            if (arg == null) return defaultValue;
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
            return "" + defaultValue;
        }
    }
    private static final OptionDecoder<Integer> default0intDecoder = new DefaultingIntDecoder(0);

    private static class DefaultNullEnumDecoder<EnumType extends Enum<EnumType>> implements OptionDecoder<EnumType> {
        private final Class<EnumType> clazz;

        DefaultNullEnumDecoder(Class<EnumType> clazz) {
            this.clazz = clazz;
        }

        @Override
        public String getRangeDescription() {
            return "One of " + Arrays.toString(clazz.getEnumConstants());
        }

        @Override
        public String getDefaultValue() {
            return null;
        }

        @Override
        public EnumType invoke(String arg1, Void arg2, Options arg3) {
            if (arg1 == null) return null;
            return Enum.valueOf(clazz, arg1);
        }
    }

    private static final OptionDecoder<Troolean> defaultNeitherTrooleanDecoder = new OptionDecoder<Troolean>() {
        @Override
        public Troolean invoke(String arg, Void ignore, Options ignore2) {
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

    private static class DefaultingBooleanDecoder implements OptionDecoder<Boolean> {
        private final boolean val;
        DefaultingBooleanDecoder(boolean val) {
            this.val = val;
        }

        @Override
        public Boolean invoke(String arg, Void ignore, Options ignore2) {
            return arg == null ? val : Boolean.parseBoolean(arg);
        }

        @Override
        public String getRangeDescription() {
            return "boolean";
        }

        @Override
        public String getDefaultValue() {
            return Boolean.toString(val);
        }
    }

    private static final OptionDecoder<Boolean> defaultTrueBooleanDecoder = new DefaultingBooleanDecoder(true);

    private static final OptionDecoder<Boolean> defaultFalseBooleanDecoder = new DefaultingBooleanDecoder(false);

    private static class DefaultChainBooleanDecoder implements OptionDecoder<Boolean> {

        private final PermittedOptionProvider.Argument<Boolean> chain;
        private final boolean negate;

        DefaultChainBooleanDecoder(PermittedOptionProvider.Argument<Boolean> chain, boolean negate) {
            this.chain = chain;
            this.negate = negate;
        }

        @Override
        public String getRangeDescription() {
            return "boolean";
        }

        @Override
        public String getDefaultValue() {
            return "Value of option '" + chain.getName() + "'";
        }

        @Override
        public Boolean invoke(String arg, Void arg2, Options options) {
            if (arg == null) {
                return (!negate) == options.getOption(chain);
            }
            return java.lang.Boolean.parseBoolean(arg);
        }
    }

    private static final OptionDecoder<String> defaultNullStringDecoder = new OptionDecoder<String>() {
        @Override
        public String invoke(String arg, Void ignore, Options ignore2) {
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

    private static final OptionDecoder<ClassFileVersion> defaultNullClassFileVersionDecoder = new OptionDecoder<ClassFileVersion>() {
        @Override
        public ClassFileVersion invoke(String arg, Void ignore, Options ignore2) {
            if (arg == null) return null;
            ClassFileVersion res = ClassFileVersion.parse(arg);
            if (res.before(ClassFileVersion.JAVA_1_0)) {
                throw new IllegalArgumentException("Class file version too early.");
            }
            return res;
        }

        @Override
        public String getRangeDescription() {
            StringBuilder res = new StringBuilder();
            res.append("string, specifying either java version as 'j6', 'j1.0', or classfile as '56', '56.65535'\n\nList of known versions:");
            res.append("\n\n");
            for (Map.Entry<String, ClassFileVersion> ver : ClassFileVersion.getByName().entrySet()) {
                res.append(ver.getKey()).append("\t: ").append(ver.getValue()).append("\n");
            }
            return res.toString();
        }

        @Override
        public String getDefaultValue() {
            return null;
        }

    };

    private static class VersionSpecificDefaulter implements OptionDecoderParam<Boolean, ClassFileVersion> {

        ClassFileVersion versionGreaterThanOrEqual;
        boolean resultIfGreaterThanOrEqual;

        private VersionSpecificDefaulter(ClassFileVersion versionGreaterThanOrEqual, boolean resultIfGreaterThanOrEqual) {
            this.versionGreaterThanOrEqual = versionGreaterThanOrEqual;
            this.resultIfGreaterThanOrEqual = resultIfGreaterThanOrEqual;
        }

        @Override
        public Boolean invoke(String arg, ClassFileVersion classFileVersion, Options ignore2) {
            if (arg != null) return Boolean.parseBoolean(arg);
            if (classFileVersion == null) throw new IllegalStateException(); // ho ho ho.
            return (classFileVersion.equalOrLater(versionGreaterThanOrEqual)) == resultIfGreaterThanOrEqual;
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

    public static class ExperimentalVersionSpecificDefaulter implements OptionDecoderParam<Boolean, ClassFileVersion> {

        ClassFileVersion versionGreaterThanOrEqual;
        private ClassFileVersion[] experimentalVersions;
        boolean resultIfGreaterThanOrEqual;

        private ExperimentalVersionSpecificDefaulter(ClassFileVersion versionGreaterThanOrEqual, boolean resultIfGreaterThanOrEqual, ClassFileVersion ... experimentalVersions) {
            this.versionGreaterThanOrEqual = versionGreaterThanOrEqual;
            this.experimentalVersions = experimentalVersions;
            this.resultIfGreaterThanOrEqual = resultIfGreaterThanOrEqual;
        }

        @Override
        public Boolean invoke(String arg, ClassFileVersion classFileVersion, Options options) {
            if (arg != null) return Boolean.parseBoolean(arg);
            // If preview features is set to false, return false;
            if (!options.getOption(OptionsImpl.PREVIEW_FEATURES)) return false;
            if (classFileVersion == null) throw new IllegalStateException(); // ho ho ho.
            if (isExperimentalIn(classFileVersion)) return resultIfGreaterThanOrEqual;
            return (classFileVersion.equalOrLater(versionGreaterThanOrEqual)) == resultIfGreaterThanOrEqual;
        }

        public boolean isExperimentalIn(ClassFileVersion classFileVersion) {
            if (!classFileVersion.isExperimental()) return false;
            for (ClassFileVersion experimental : experimentalVersions) {
                if (experimental.sameMajor(classFileVersion)) return true;
            }
            return false;
        }

        @Override
        public String getRangeDescription() {
            return "boolean";
        }

        @Override
        public String getDefaultValue() {
            boolean first = true;
            StringBuilder sb = new StringBuilder();
            for (ClassFileVersion experimental : experimentalVersions) {
                first = StringUtils.comma(first, sb);
                sb.append(experimental);
            }
            return "" + resultIfGreaterThanOrEqual + " if class file from version " + versionGreaterThanOrEqual + " or greater, or experimental in " + sb.toString();
        }

    }

    private static final String CFR_WEBSITE = "https://benf.org/other/cfr/";

    // Look, I don't like reflection.  ;)
    private static List<PermittedOptionProvider.ArgumentParam<?,?>> all = ListFactory.newList();
    
    private static <T extends PermittedOptionProvider.ArgumentParam<?, ?>> T register(T in) {
        all.add(in);
        return in;
    }

    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> SUGAR_STRINGBUFFER = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "stringbuffer", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, false),
            "Convert new StringBuffer().append.append.append to string + string + string - see " + CFR_WEBSITE + "stringbuilder-vs-concatenation.html"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> SUGAR_STRINGBUILDER = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "stringbuilder", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true),
            "Convert new StringBuilder().append.append.append to string + string + string - see " + CFR_WEBSITE + "stringbuilder-vs-concatenation.html"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> SUGAR_STRINGCONCATFACTORY = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "stringconcat", new VersionSpecificDefaulter(ClassFileVersion.JAVA_9, true),
            "Convert usages of StringConcatFactor to string + string + string - see " + CFR_WEBSITE + "java9stringconcat.html"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> ENUM_SWITCH = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "decodeenumswitch", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true),
            "Re-sugar switch on enum - see " + CFR_WEBSITE + "switch-on-enum.html"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> ENUM_SUGAR = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "sugarenums", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true),
            "Re-sugar enums - see " + CFR_WEBSITE + "how-are-enums-implemented.html"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> STRING_SWITCH = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "decodestringswitch", new VersionSpecificDefaulter(ClassFileVersion.JAVA_7, true),
            "Re-sugar switch on String - see " + CFR_WEBSITE + "java7switchonstring.html"));
    public static final PermittedOptionProvider.Argument<Boolean> PREVIEW_FEATURES = register(new PermittedOptionProvider.Argument<Boolean>(
            "previewfeatures", defaultTrueBooleanDecoder,
            "Decompile preview features if class was compiled with 'javac --enable-preview'"));
    public static final ExperimentalVersionSpecificDefaulter switchExpressionVersion = new ExperimentalVersionSpecificDefaulter(ClassFileVersion.JAVA_13, true, ClassFileVersion.JAVA_12);
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> SWITCH_EXPRESSION = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "switchexpression", switchExpressionVersion,
            "Re-sugar switch expression"));
    private static final ExperimentalVersionSpecificDefaulter recordTypesVersion = new ExperimentalVersionSpecificDefaulter(ClassFileVersion.JAVA_14, true, ClassFileVersion.JAVA_14);
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> RECORD_TYPES = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "recordtypes", recordTypesVersion,
            "Re-sugar record types"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> INSTANCEOF_PATTERN = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "instanceofpattern", recordTypesVersion,
            "Re-sugar instanceof pattern matches"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> ARRAY_ITERATOR = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "arrayiter", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true),
            "Re-sugar array based iteration"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> COLLECTION_ITERATOR = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "collectioniter", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, true),
            "Re-sugar collection based iteration"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> REWRITE_TRY_RESOURCES = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "tryresources", new VersionSpecificDefaulter(ClassFileVersion.JAVA_7, true),
            "Reconstruct try-with-resources"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> REWRITE_LAMBDAS = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "decodelambdas", new VersionSpecificDefaulter(ClassFileVersion.JAVA_8, true),
            "Re-build lambda functions"));
    public static final PermittedOptionProvider.Argument<Boolean> DECOMPILE_INNER_CLASSES = register(new PermittedOptionProvider.Argument<Boolean>(
            "innerclasses", defaultTrueBooleanDecoder,
            "Decompile inner classes"));
    public static final PermittedOptionProvider.Argument<Boolean> FORBID_METHOD_SCOPED_CLASSES = register(new PermittedOptionProvider.Argument<Boolean>(
            "forbidmethodscopedclasses", defaultFalseBooleanDecoder,
            "Don't allow method scoped classes.   Note - this will NOT be used as a fallback, it must be specified.\nIt will produce odd code."));
    public static final PermittedOptionProvider.Argument<Boolean> FORBID_ANONYMOUS_CLASSES = register(new PermittedOptionProvider.Argument<Boolean>(
            "forbidanonymousclasses", defaultFalseBooleanDecoder,
            "Don't allow anonymous classes.   Note - this will NOT be used as a fallback, it must be specified.\nIt will produce odd code."));
    public static final PermittedOptionProvider.Argument<Boolean> SKIP_BATCH_INNER_CLASSES = register(new PermittedOptionProvider.Argument<Boolean>(
            "skipbatchinnerclasses", defaultTrueBooleanDecoder,
            "When processing many files, skip inner classes, as they will be processed as part of outer classes anyway.  If false, you will see inner classes as separate entities also."));
    public static final PermittedOptionProvider.Argument<Boolean> HIDE_UTF8 = register(new PermittedOptionProvider.Argument<Boolean>(
            "hideutf", defaultTrueBooleanDecoder,
            "Hide UTF8 characters - quote them instead of showing the raw characters"));
    public static final PermittedOptionProvider.Argument<Boolean> HIDE_LONGSTRINGS = register(new PermittedOptionProvider.Argument<Boolean>(
            "hidelongstrings", defaultFalseBooleanDecoder,
            "Hide very long strings - useful if obfuscators have placed fake code in strings"));
    public static final PermittedOptionProvider.Argument<Boolean> REMOVE_BOILERPLATE = register(new PermittedOptionProvider.Argument<Boolean>(
            "removeboilerplate", defaultTrueBooleanDecoder,
            "Remove boilderplate functions - constructor boilerplate, lambda deserialisation etc."));
    public static final PermittedOptionProvider.Argument<Boolean> REMOVE_INNER_CLASS_SYNTHETICS = register(new PermittedOptionProvider.Argument<Boolean>(
            "removeinnerclasssynthetics", defaultTrueBooleanDecoder,
            "Remove (where possible) implicit outer class references in inner classes"));
    public static final PermittedOptionProvider.Argument<Boolean> RELINK_CONSTANT_STRINGS = register(new PermittedOptionProvider.Argument<Boolean>(
            "relinkconststring", defaultTrueBooleanDecoder,
            "Relink constant strings - if there is a local reference to a string which matches a static final, use the static final."));
    public static final PermittedOptionProvider.Argument<Boolean> LIFT_CONSTRUCTOR_INIT = register(new PermittedOptionProvider.Argument<Boolean>(
            "liftconstructorinit", defaultTrueBooleanDecoder,
            "Lift initialisation code common to all constructors into member initialisation"));
    public static final PermittedOptionProvider.Argument<Boolean> REMOVE_DEAD_METHODS = register(new PermittedOptionProvider.Argument<Boolean>(
            "removedeadmethods", defaultTrueBooleanDecoder,
            "Remove pointless methods - default constructor etc."));
    public static final PermittedOptionProvider.Argument<Boolean> REMOVE_BAD_GENERICS = register(new PermittedOptionProvider.Argument<Boolean>(
            "removebadgenerics", defaultTrueBooleanDecoder,
            "Hide generics where we've obviously got it wrong, and fallback to non-generic"));
    public static final PermittedOptionProvider.Argument<Boolean> SUGAR_ASSERTS = register(new PermittedOptionProvider.Argument<Boolean>(
            "sugarasserts", defaultTrueBooleanDecoder,
            "Re-sugar assert calls"));
    public static final PermittedOptionProvider.Argument<Boolean> SUGAR_BOXING = register(new PermittedOptionProvider.Argument<Boolean>(
            "sugarboxing", defaultTrueBooleanDecoder,
            "Where possible, remove pointless boxing wrappers"));
    public static final PermittedOptionProvider.Argument<Boolean> SHOW_CFR_VERSION = register(new PermittedOptionProvider.Argument<Boolean>(
            "showversion", defaultTrueBooleanDecoder,
            "Show used CFR version in header (handy to turn off when regression testing)"));
    public static final PermittedOptionProvider.Argument<Boolean> DECODE_FINALLY = register(new PermittedOptionProvider.Argument<Boolean>(
            "decodefinally", defaultTrueBooleanDecoder,
            "Re-sugar finally statements"));
    public static final PermittedOptionProvider.Argument<Boolean> TIDY_MONITORS = register(new PermittedOptionProvider.Argument<Boolean>(
            "tidymonitors", defaultTrueBooleanDecoder,
            "Remove support code for monitors - e.g. catch blocks just to exit a monitor"));
    public static final PermittedOptionProvider.Argument<Boolean> COMMENT_MONITORS = register(new PermittedOptionProvider.Argument<Boolean>(
            "commentmonitors", defaultFalseBooleanDecoder,
            "Replace monitors with comments - useful if we're completely confused"));
    public static final PermittedOptionProvider.Argument<Boolean> LENIENT = register(new PermittedOptionProvider.Argument<Boolean>(
            "lenient", defaultFalseBooleanDecoder,
            "Be a bit more lenient in situations where we'd normally throw an exception"));
    public static final PermittedOptionProvider.Argument<Boolean> DUMP_CLASS_PATH = register(new PermittedOptionProvider.Argument<Boolean>(
            "dumpclasspath", defaultFalseBooleanDecoder,
            "Dump class path for debugging purposes"));
    public static final PermittedOptionProvider.Argument<Boolean> DECOMPILER_COMMENTS = register(new PermittedOptionProvider.Argument<Boolean>(
            "comments", defaultTrueBooleanDecoder,
            "Output comments describing decompiler status, fallback flags etc."));
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_TOPSORT = register(new PermittedOptionProvider.Argument<Troolean>(
            "forcetopsort", defaultNeitherTrooleanDecoder,
            "Force basic block sorting.  Usually not necessary for code emitted directly from javac, but required in the case of obfuscation (or dex2jar!).  Will be enabled in recovery."));
    public static final PermittedOptionProvider.Argument<ClassFileVersion> FORCE_CLASSFILEVER = register(new PermittedOptionProvider.Argument<ClassFileVersion>(
            "forceclassfilever", defaultNullClassFileVersionDecoder,
            "Force the version of the classfile (and hence java) that classfiles are decompiled as.  Normally detected from class files.  --help forceclassfilever for details."));
    public static final PermittedOptionProvider.Argument<Troolean> FOR_LOOP_CAPTURE = register(new PermittedOptionProvider.Argument<Troolean>(
            "forloopaggcapture", defaultNeitherTrooleanDecoder,
            "Allow for loops to aggressively roll mutations into update section, even if they don't appear to be involved with the predicate"));
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_TOPSORT_EXTRA = register(new PermittedOptionProvider.Argument<Troolean>(
            "forcetopsortaggress", defaultNeitherTrooleanDecoder,
            "Force extra aggressive topsort options"));
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_TOPSORT_NOPULL = register(new PermittedOptionProvider.Argument<Troolean>(
            "forcetopsortnopull", defaultNeitherTrooleanDecoder,
            "Force topsort not to pull try blocks"));
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_COND_PROPAGATE = register(new PermittedOptionProvider.Argument<Troolean>(
            "forcecondpropagate", defaultNeitherTrooleanDecoder,
            "Pull results of deterministic jumps back through some constant assignments"));
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_RETURNING_IFS = register(new PermittedOptionProvider.Argument<Troolean>(
            "forcereturningifs", defaultNeitherTrooleanDecoder,
            "Move return up to jump site"));
    public static final PermittedOptionProvider.Argument<Boolean> IGNORE_EXCEPTIONS_ALWAYS = register(new PermittedOptionProvider.Argument<Boolean>(
            "ignoreexceptionsalways", defaultFalseBooleanDecoder,
            "Drop exception information (WARNING : changes semantics, dangerous!)"));
    public static final PermittedOptionProvider.Argument<Boolean> ANTI_OBF = register(new PermittedOptionProvider.Argument<Boolean>(
            "antiobf", defaultFalseBooleanDecoder,
            "Undo various obfuscations"));
    public static final PermittedOptionProvider.Argument<Boolean> CONTROL_FLOW_OBF = register(new PermittedOptionProvider.Argument<Boolean>(
            "obfcontrol", new DefaultChainBooleanDecoder(ANTI_OBF, false),
            "Undo control flow obfuscation"));
    public static final PermittedOptionProvider.Argument<Boolean> ATTRIBUTE_OBF = register(new PermittedOptionProvider.Argument<Boolean>(
            "obfattr", new DefaultChainBooleanDecoder(ANTI_OBF, false),
            "Undo attribute obfuscation"));
    public static final PermittedOptionProvider.Argument<Boolean> HIDE_BRIDGE_METHODS = register(new PermittedOptionProvider.Argument<Boolean>(
            "hidebridgemethods", new DefaultChainBooleanDecoder(ATTRIBUTE_OBF, true),
            "Hide bridge methods"));
    public static final PermittedOptionProvider.Argument<Boolean> IGNORE_EXCEPTIONS = register(new PermittedOptionProvider.Argument<Boolean>(
            "ignoreexceptions", defaultFalseBooleanDecoder,
            "Drop exception information if completely stuck (WARNING : changes semantics, dangerous!)"));
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_PRUNE_EXCEPTIONS = register(new PermittedOptionProvider.Argument<Troolean>(
            "forceexceptionprune", defaultNeitherTrooleanDecoder,
            "Remove nested exception handlers if they don't change semantics"));
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_AGGRESSIVE_EXCEPTION_AGG = register(new PermittedOptionProvider.Argument<Troolean>(
            "aexagg", defaultNeitherTrooleanDecoder,
            "Try to extend and merge exceptions more aggressively"));
    public static final PermittedOptionProvider.Argument<Troolean> FORCE_AGGRESSIVE_EXCEPTION_AGG2 = register(new PermittedOptionProvider.Argument<Troolean>(
            "aexagg2", defaultNeitherTrooleanDecoder,
            "Try to extend and merge exceptions more aggressively (may change semantics)"));
    public static final PermittedOptionProvider.Argument<Troolean> RECOVER_TYPECLASHES = register(new PermittedOptionProvider.Argument<Troolean>(
            "recovertypeclash", defaultNeitherTrooleanDecoder,
            "Split lifetimes where analysis caused type clash"));
    public static final PermittedOptionProvider.Argument<Troolean> USE_RECOVERED_ITERATOR_TYPE_HINTS = register(new PermittedOptionProvider.Argument<Troolean>(
            "recovertypehints", defaultNeitherTrooleanDecoder,
            "Recover type hints for iterators from first pass"));
    public static final PermittedOptionProvider.Argument<String> OUTPUT_DIR = register(new PermittedOptionProvider.Argument<String>(
            "outputdir", defaultNullStringDecoder,
            "Decompile to files in [directory] (= options 'outputpath' + 'clobber') (historic compatibility)"));
    public static final PermittedOptionProvider.Argument<String> OUTPUT_PATH = register(new PermittedOptionProvider.Argument<String>(
            "outputpath", defaultNullStringDecoder,
            "Decompile to files in [directory]"));
    public static final PermittedOptionProvider.Argument<Troolean> CLOBBER_FILES = register(new PermittedOptionProvider.Argument<Troolean>(
            "clobber", defaultNeitherTrooleanDecoder,
            "Overwrite files when using option 'outputpath'"));
    public static final PermittedOptionProvider.Argument<Boolean> SILENT = register(new PermittedOptionProvider.Argument<Boolean>(
            "silent", defaultFalseBooleanDecoder,
            "Don't display state while decompiling"));
    public static final PermittedOptionProvider.Argument<Boolean> RECOVER = register(new PermittedOptionProvider.Argument<Boolean>(
            "recover", defaultTrueBooleanDecoder,
            "Allow more and more aggressive options to be set if decompilation fails"));
    public static final PermittedOptionProvider.Argument<Boolean> ECLIPSE = register(new PermittedOptionProvider.Argument<Boolean>(
            "eclipse", defaultTrueBooleanDecoder,
            "Enable transformations to handle Eclipse code better"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> OVERRIDES = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "override", new VersionSpecificDefaulter(ClassFileVersion.JAVA_6, true),
            "Generate @Override annotations (if method is seen to implement interface method, or override a base class method)"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> SHOW_INFERRABLE = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "showinferrable", new VersionSpecificDefaulter(ClassFileVersion.JAVA_7, false),
            "Decorate methods with explicit types if not implied by arguments"));
    public static final PermittedOptionProvider.Argument<String> HELP = register(new PermittedOptionProvider.Argument<String>(
            "help", defaultNullStringDecoder,
            "Show help for a given parameter"));
    public static final PermittedOptionProvider.Argument<Boolean> ALLOW_CORRECTING = register(new PermittedOptionProvider.Argument<Boolean>(
            "allowcorrecting", defaultTrueBooleanDecoder,
            "Allow transformations which correct errors, potentially at the cost of altering emitted code behaviour.  An example would be removing impossible (in java!) exception handling - if this has any effect, a warning will be emitted."));
    public static final PermittedOptionProvider.Argument<Boolean> LABELLED_BLOCKS = register(new PermittedOptionProvider.Argument<Boolean>(
            "labelledblocks", defaultTrueBooleanDecoder,
            "Allow code to be emitted which uses labelled blocks, (handling odd forward gotos)"));
    public static final PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion> JAVA_4_CLASS_OBJECTS = register(new PermittedOptionProvider.ArgumentParam<Boolean, ClassFileVersion>(
            "j14classobj", new VersionSpecificDefaulter(ClassFileVersion.JAVA_5, false),
            "Reverse java 1.4 class object construction"));
    public static final PermittedOptionProvider.Argument<Boolean> HIDE_LANG_IMPORTS = register(new PermittedOptionProvider.Argument<Boolean>(
            "hidelangimports", defaultTrueBooleanDecoder,
            "Hide imports from java.lang."));
    public static final PermittedOptionProvider.Argument<Integer> FORCE_PASS = register(new PermittedOptionProvider.Argument<Integer>(
            "recpass", default0intDecoder,
            "Decompile specifically with recovery options from pass #X. (really only useful for debugging)", true));
    public static final PermittedOptionProvider.Argument<AnalysisType> ANALYSE_AS = register(new PermittedOptionProvider.Argument<AnalysisType>(
            "analyseas", new DefaultNullEnumDecoder<AnalysisType>(AnalysisType.class),
            "Force file to be analysed as 'jar' or 'class'"));
    public static final PermittedOptionProvider.Argument<String> JAR_FILTER = register(new PermittedOptionProvider.Argument<String>(
            "jarfilter", defaultNullStringDecoder,
            "Substring regex - analyse only classes where the fqn matches this pattern. (when analysing jar)"));
    private static final PermittedOptionProvider.Argument<Boolean> RENAME_MEMBERS = register(new PermittedOptionProvider.Argument<Boolean>(
            "rename", defaultFalseBooleanDecoder,
            "Synonym for 'renamedupmembers' + 'renameillegalidents' + 'renameenumidents'"));
    public static final PermittedOptionProvider.Argument<Boolean> RENAME_DUP_MEMBERS = register(new PermittedOptionProvider.Argument<Boolean>(
            "renamedupmembers", new DefaultChainBooleanDecoder(RENAME_MEMBERS, false),
            "Rename ambiguous/duplicate fields.  Note - this WILL break reflection based access, so is not automatically enabled."));
    public static final PermittedOptionProvider.Argument<Integer> RENAME_SMALL_MEMBERS = register(new PermittedOptionProvider.Argument<Integer>(
            "renamesmallmembers", new DefaultingIntDecoder(0),
            "Rename small members.  Note - this WILL break reflection based access, so is not automatically enabled."));
    public static final PermittedOptionProvider.Argument<Boolean> RENAME_ILLEGAL_IDENTS = register(new PermittedOptionProvider.Argument<Boolean>(
            "renameillegalidents", new DefaultChainBooleanDecoder(RENAME_MEMBERS, false),
            "Rename identifiers which are not valid java identifiers.  Note - this WILL break reflection based access, so is not automatically enabled."));
    public static final PermittedOptionProvider.Argument<Boolean> RENAME_ENUM_MEMBERS = register(new PermittedOptionProvider.Argument<Boolean>(
            "renameenumidents", new DefaultChainBooleanDecoder(RENAME_MEMBERS, false),
            "Rename ENUM identifiers which do not match their 'expected' string names.  Note - this WILL break reflection based access, so is not automatically enabled."));
    public static final PermittedOptionProvider.Argument<Troolean> REMOVE_DEAD_CONDITIONALS = register(new PermittedOptionProvider.Argument<Troolean>(
            "removedeadconditionals", defaultNeitherTrooleanDecoder,
            "Remove code that can't be executed."));
    public static final PermittedOptionProvider.Argument<Troolean> AGGRESSIVE_DO_EXTENSION = register(new PermittedOptionProvider.Argument<Troolean>(
            "aggressivedoextension", defaultNeitherTrooleanDecoder,
            "Fold impossible jumps into do loops with 'first' test"));
    public static final PermittedOptionProvider.Argument<Troolean> AGGRESSIVE_DUFF = register(new PermittedOptionProvider.Argument<Troolean>(
            "aggressiveduff", defaultNeitherTrooleanDecoder,
            "Fold duff device style switches with additional control."));
    public static final PermittedOptionProvider.Argument<Integer> AGGRESSIVE_DO_COPY = register(new PermittedOptionProvider.Argument<Integer>(
            "aggressivedocopy", new DefaultingIntDecoder(0),
            "Clone code from impossible jumps into loops with 'first' test"));
    public static final PermittedOptionProvider.Argument<Integer> AGGRESSIVE_SIZE_REDUCTION_THRESHOLD = register(new PermittedOptionProvider.Argument<Integer>(
            "aggressivesizethreshold", new DefaultingIntDecoder(15000),
            "Opcode count at which to trigger aggressive reductions"));
    public static final PermittedOptionProvider.Argument<Boolean> STATIC_INIT_RETURN = register(new PermittedOptionProvider.Argument<Boolean>(
            "staticinitreturn", defaultTrueBooleanDecoder,
            "Try to remove return from static init"));
    public static final PermittedOptionProvider.Argument<Boolean> USE_NAME_TABLE = register(new PermittedOptionProvider.Argument<Boolean>(
            "usenametable", defaultTrueBooleanDecoder,
            "Use local variable name table if present"));
    public static final PermittedOptionProvider.Argument<String> METHODNAME = register(new PermittedOptionProvider.Argument<String>(
            "methodname", defaultNullStringDecoder,
            "Name of method to analyse"));
    public static final PermittedOptionProvider.Argument<String> EXTRA_CLASS_PATH = register(new PermittedOptionProvider.Argument<String>(
            "extraclasspath", defaultNullStringDecoder,
            "additional class path - classes in this classpath will be used if needed."));
    public static final PermittedOptionProvider.Argument<Boolean> PULL_CODE_CASE = register(new PermittedOptionProvider.Argument<Boolean>(
            "pullcodecase", defaultFalseBooleanDecoder,
            "Pull code into case statements agressively"));
    public static final PermittedOptionProvider.Argument<Boolean> ELIDE_SCALA = register(new PermittedOptionProvider.Argument<Boolean>(
            "elidescala", defaultFalseBooleanDecoder,
            "Elide things which aren't helpful in scala output (serialVersionUID, @ScalaSignature)"));
    public static final PermittedOptionProvider.Argument<Boolean> USE_SIGNATURES = register(new PermittedOptionProvider.Argument<Boolean>(
            "usesignatures", defaultTrueBooleanDecoder,
            "Use signatures in addition to descriptors (when they are not obviously incorrect)"));
    public static final PermittedOptionProvider.Argument<Boolean> CASE_INSENSITIVE_FS_RENAME = register(new PermittedOptionProvider.Argument<Boolean>(
            "caseinsensitivefs", new DefaultingBooleanDecoder(OsInfo.OS().isCaseInsensitive()),
            "Cope with case insensitive file systems by renaming colliding classes"));
    public static final PermittedOptionProvider.Argument<Boolean> LOMEM = register(new PermittedOptionProvider.Argument<Boolean>(
            "lomem", defaultFalseBooleanDecoder,
            "Be more agressive about uncaching in order to reduce memory footprint"));
    public static final PermittedOptionProvider.Argument<String> IMPORT_FILTER = register(new PermittedOptionProvider.Argument<String>(
            "importfilter", defaultNullStringDecoder,
            "Substring regex - import classes only when fqn matches this pattern. (VNegate with !, eg !lang)"));
    public static final PermittedOptionProvider.Argument<String> OBFUSCATION_PATH = register(new PermittedOptionProvider.Argument<String>(
            "obfuscationpath", defaultNullStringDecoder,
            "Path to obfuscation symbol remapping file"));
    public static final PermittedOptionProvider.Argument<Boolean> TRACK_BYTECODE_LOC = register(new PermittedOptionProvider.Argument<Boolean>(
            "trackbytecodeloc", defaultFalseBooleanDecoder,
            "Propagate bytecode location info."));


    public OptionsImpl(Map<String, String> opts) {
        this.opts = new HashMap<String, String>(opts);
    }

    @Override
    public <T> T getOption(PermittedOptionProvider.ArgumentParam<T, Void> option) {
        return option.getFn().invoke(opts.get(option.getName()), null, this);
    }

    @Override
    public <T, A> T getOption(PermittedOptionProvider.ArgumentParam<T, A> option, A arg) {
        return option.getFn().invoke(opts.get(option.getName()), arg, this);
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
            return all;
        }

        @Override
        public Options create(Map<String, String> opts) {
            return new OptionsImpl(opts);
        }
    }

}
