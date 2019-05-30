package org.benf.cfr.reader;

import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.ClassFileSource;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.apiunreleased.ClassFileSource2;
import org.benf.cfr.reader.state.ClassFileSourceImpl;
import org.benf.cfr.reader.state.ClassFileSourceWrapper;
import org.benf.cfr.reader.state.DCCommonState;
import org.benf.cfr.reader.util.AnalysisType;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;
import org.benf.cfr.reader.util.output.DumperFactory;
import org.benf.cfr.reader.util.output.InternalDumperFactoryImpl;
import org.benf.cfr.reader.util.output.SinkDumperFactory;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class CfrDriverImpl implements CfrDriver {
    private final Options options;
    private final ClassFileSource2 classFileSource;
    private final OutputSinkFactory outputSinkFactory;

    public CfrDriverImpl(ClassFileSource source, OutputSinkFactory outputSinkFactory, Options options) {
        if (options == null) {
            options = new OptionsImpl(new HashMap<String, String>());
        }
        if (source == null) {
            source = new ClassFileSourceImpl(options);
        }
        this.outputSinkFactory = outputSinkFactory;
        this.options = options;
        this.classFileSource = source instanceof ClassFileSource2 ? (ClassFileSource2)source : new ClassFileSourceWrapper(source);
    }

    @Override
    public void analyse(List<String> toAnalyse) {
        /*
         * There's an interesting question here - do we want to skip inner classes, if we've been given a wildcard?
         * (or a wildcard expanded by the operating system).
         *
         * Assume yes.
         */
        boolean skipInnerClass = toAnalyse.size() > 1 && options.getOption(OptionsImpl.SKIP_BATCH_INNER_CLASSES);

        Collections.sort(toAnalyse);
        for (String path : toAnalyse) {
            // TODO : We shouldn't have to discard state here.  But we do, because
            // it causes test fails.  (used class name table retains useful symbols).
            classFileSource.informAnalysisRelativePathDetail(null, null);
            // Note - both of these need to be reset, as they have caches.
            DCCommonState dcCommonState = new DCCommonState(options, classFileSource);
            DumperFactory dumperFactory = outputSinkFactory != null ?
                    new SinkDumperFactory(outputSinkFactory, options) :
                    new InternalDumperFactoryImpl(options);

            AnalysisType type = options.getOption(OptionsImpl.ANALYSE_AS);
            if (type == null || type == AnalysisType.DETECT) {
                type = dcCommonState.detectClsJar(path);
            }

            if (type == AnalysisType.JAR || type == AnalysisType.WAR) {
                Driver.doJar(dcCommonState, path, dumperFactory);
            } else if (type == AnalysisType.CLASS) {
                Driver.doClass(dcCommonState, path, skipInnerClass, dumperFactory);
            }
        }
    }
}
