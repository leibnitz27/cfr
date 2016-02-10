package org.benf.cfr.reader;

import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.bytecode.analysis.types.JavaTypeInstance;
import org.benf.cfr.reader.entities.ClassFile;
import org.benf.cfr.reader.entities.Method;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.state.TypeUsageCollector;
import org.benf.cfr.reader.state.TypeUsageInformation;
import org.benf.cfr.reader.util.MapFactory;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.*;

import java.util.Map;

public class PluginRunner {
    private final DCCommonState dcCommonState;
    private final IllegalIdentifierDump illegalIdentifierDump = new IllegalIdentifierDump.Nop();
    private final ClassFileSource classFileSource;

    /*
     *
     */
    public PluginRunner() {
        this(MapFactory.<String, String>newMap(), null);
    }

    public PluginRunner(Map<String, String> options) {
        this(options, null);
    }

    public PluginRunner(Map<String, String> options, ClassFileSource classFileSource) {
        this.dcCommonState = initDCState(options, classFileSource);
        this.classFileSource = classFileSource;
    }

    public Options getOptions() {
        return this.dcCommonState.getOptions();
    }

    public void addJarPaths(String[] jarPaths) {
        for (String jarPath : jarPaths) {
            addJarPath(jarPath);
        }
    }

    public void addJarPath(String jarPath) {
        try {
            dcCommonState.explicitlyLoadJar(jarPath);
        } catch (Exception e) {
        }
    }

    class StringStreamDumper extends StreamDumper {
        private final StringBuilder stringBuilder;

        public StringStreamDumper(StringBuilder sb, TypeUsageInformation typeUsageInformation, Options options) {
            super(typeUsageInformation, options, illegalIdentifierDump);
            this.stringBuilder = sb;
        }

        @Override
        protected void write(String s) {
            stringBuilder.append(s);
        }

        @Override
        public void close() {
        }

        @Override
        public void addSummaryError(Method method, String s) {
        }
    }

    private class PluginDumperFactory implements DumperFactory {

        private final StringBuilder outBuffer;

        public PluginDumperFactory(StringBuilder out) {
            this.outBuffer = out;
        }

        public Dumper getNewTopLevelDumper(Options options, JavaTypeInstance classType, SummaryDumper summaryDumper, TypeUsageInformation typeUsageInformation, IllegalIdentifierDump illegalIdentifierDump) {
            return new StringStreamDumper(outBuffer, typeUsageInformation, options);
        }

        /*
         * A summary dumper will receive errors.  Generally, it's only of value when dumping jars to file.
         */
        public SummaryDumper getSummaryDumper(Options options) {
            if (!options.optionIsSet(OptionsImpl.OUTPUT_DIR)) return new NopSummaryDumper();

            return new FileSummaryDumper(options.getOption(OptionsImpl.OUTPUT_DIR));
        }
    }

    public String getDecompilationFor(String classFilePath) {
        try {
            StringBuilder output = new StringBuilder();
            DumperFactory dumperFactory = new PluginDumperFactory(output);
            Main.doClass(dcCommonState, classFilePath, dumperFactory);
            return output.toString();
        } catch (Exception e) {
            return e.toString();
        }
    }

    private static DCCommonState initDCState(Map<String, String> optionsMap, ClassFileSource classFileSource) {
        OptionsImpl options = new OptionsImpl(null, null, optionsMap);
        if (classFileSource == null) classFileSource = new ClassFileSourceImpl(options);
        DCCommonState dcCommonState = new DCCommonState(options, classFileSource);
        return dcCommonState;
    }

}
