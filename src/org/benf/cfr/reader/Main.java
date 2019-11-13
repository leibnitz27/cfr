package org.benf.cfr.reader;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.bytecode.analysis.parse.utils.Pair;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.AnalysisType;
import org.benf.cfr.reader.util.getopt.GetOptParser;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.DumperFactory;

import java.util.List;

public class Main {

    @SuppressWarnings({"WeakerAccess", "unused"}) // too many people use it - left for historical reasons.
    public static void doClass(DCCommonState dcCommonState, String path, boolean skipInnerClass, DumperFactory dumperFactory) {
        Driver.doClass(dcCommonState, path, skipInnerClass, dumperFactory);
    }

    @SuppressWarnings({"WeakerAccess", "unused"}) // too many people use it - left for historical reasons.
    public static void doJar(DCCommonState dcCommonState, String path, DumperFactory dumperFactory) {
        Driver.doJar(dcCommonState, path, AnalysisType.JAR, dumperFactory);
    }

    public static void main(String[] args) {
        GetOptParser getOptParser = new GetOptParser();

        Options options = null;
        List<String> files = null;
        try {
            Pair<List<String>, Options> processedArgs = getOptParser.parse(args, OptionsImpl.getFactory());
            files = processedArgs.getFirst();
            options = processedArgs.getSecond();
            if (files.size() == 0) {
                throw new IllegalArgumentException("Insufficient unqualified parameters - provide at least one filename.");
            }
        } catch (Exception e) {
            getOptParser.showHelp(e);
            System.exit(1);
        }

        if (options.optionIsSet(OptionsImpl.HELP) || files.isEmpty()) {
            getOptParser.showOptionHelp(OptionsImpl.getFactory(), options, OptionsImpl.HELP);
            return;
        }

        CfrDriver cfrDriver = new CfrDriver.Builder().withBuiltOptions(options).build();
        cfrDriver.analyse(files);
    }
}
