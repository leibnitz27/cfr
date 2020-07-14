package org.benf.cfr.reader;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.apiunreleased.ClassFileSource2;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.state.ClassFileSourceWrapper;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.AnalysisType;
import org.benf.cfr.reader.util.collections.Functional;
import org.benf.cfr.reader.util.collections.MapFactory;
import org.benf.cfr.reader.util.functors.UnaryFunction;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.*;
import org.benf.cfr.reader.util.output.MethodErrorCollector.SummaryDumperMethodErrorCollector;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@SuppressWarnings({"WeakerAccess", "unused"})
@Deprecated
public class PluginRunner {
    private final DCCommonState dcCommonState;

    public PluginRunner() {
        this(MapFactory.<String, String>newMap(), null);
    }

    public PluginRunner(Map<String, String> options) {
        this(options, null);
    }

    public PluginRunner(Map<String, String> options, ClassFileSource classFileSource) {
        this.dcCommonState = initDCState(options, classFileSource);
    }

    public Options getOptions() {
        return this.dcCommonState.getOptions();
    }

    public List<List<String>> addJarPaths(String[] jarPaths) {
        List<List<String>> res = new ArrayList<List<String>>();
        for (String jarPath : jarPaths) {
            res.add(addJarPath(jarPath));
        }
        return res;
    }

    public List<String> addJarPath(String jarPath) {
        try {
            List<JavaTypeInstance> types = dcCommonState.explicitlyLoadJar(jarPath, AnalysisType.JAR).get(0);
            return Functional.map(types, new UnaryFunction<JavaTypeInstance, String>() {
                @Override
                public String invoke(JavaTypeInstance arg) {
                    return arg.getRawName();
                }
            });
        } catch (Exception e) {
            return new ArrayList<String>();
        }
    }

    private class PluginDumperFactory implements DumperFactory {
        private final IllegalIdentifierDump illegalIdentifierDump = new IllegalIdentifierDump.Nop();

        private final StringBuilder outBuffer;
        private final Options options;

        public PluginDumperFactory(StringBuilder out, Options options) {
            this.outBuffer = out;
            this.options = options;
        }

        public Dumper getNewTopLevelDumper(JavaTypeInstance classType, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation, IllegalIdentifierDump illegalIdentifierDump) {
            return new StringStreamDumper(new SummaryDumperMethodErrorCollector(classType, summaryDumper), outBuffer, typeUsageInformation, options, this.illegalIdentifierDump);
        }

        @Override
        public Dumper wrapLineNoDumper(Dumper dumper) {
            return dumper;
        }

        /*
         * A summary dumper will receive errors.  Generally, it's only of value when dumping jars to file.
         */
        public SummaryDumper getSummaryDumper() {
            if (!options.optionIsSet(OptionsImpl.OUTPUT_DIR)) return new NopSummaryDumper();

            return new FileSummaryDumper(options.getOption(OptionsImpl.OUTPUT_DIR), options, null);
        }

        @Override
        public ProgressDumper getProgressDumper() {
            return ProgressDumperNop.INSTANCE;
        }

        @Override
        public ExceptionDumper getExceptionDumper() {
            return new StdErrExceptionDumper();
        }

        @Override
        public DumperFactory getFactoryWithPrefix(String prefix, int version) {
            return this;
        }
    }

    public String getDecompilationFor(String classFilePath) {
        try {
            StringBuilder output = new StringBuilder();
            DumperFactory dumperFactory = new PluginDumperFactory(output, dcCommonState.getOptions());
            Main.doClass(dcCommonState, classFilePath, false, dumperFactory);
            return output.toString();
        } catch (Exception e) {
            return e.toString();
        }
    }

    private static DCCommonState initDCState(Map<String, String> optionsMap, ClassFileSource classFileSource) {
        OptionsImpl options = new OptionsImpl(optionsMap);
        ClassFileSource2 source = classFileSource == null ?
                new ClassFileSourceImpl(options) :
                new ClassFileSourceWrapper(classFileSource);
        return new DCCommonState(options, source);
    }

}
