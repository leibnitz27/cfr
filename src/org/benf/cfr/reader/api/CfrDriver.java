package org.benf.cfr.reader.api;

import org.benf.cfr.reader.CfrDriverImpl;
import org.benf.cfr.reader.util.getopt.Options;
import org.benf.cfr.reader.util.getopt.OptionsImpl;

import java.util.List;
import java.util.Map;

/**
 * Main driver for CFR API.   Instance of this driver should be constructed by using
 * {@link Builder}.
 */
@SuppressWarnings("unused")
public interface CfrDriver {
    /**
     * Analyse and dump to configured output sink.
     *
     * @param toAnalyse list of class file FQN / path of jar / path of class file
     * @param externalFileSource implementation of ClassFileSource to find class dependencies
     */
    void analyse(List<String> toAnalyse, ClassFileSource externalFileSource);

    /**
     * Analyse and dump to configured output sink.
     *
     * @param toAnalyse list of class file FQN / path of jar / path of class file
     */
    void analyse(List<String> toAnalyse);

    /**
     * Builder for {@link CfrDriver}
     *
     * Note that *all* parameters are optional.  If you build a naked builder, you will get default
     * CFR behaviour.
     */
    class Builder {
        ClassFileSource source = null;
        Options builtOptions = null;
        OutputSinkFactory output = null;
        boolean fallbackToDefaultSource = false;

        /**
         * Overrides where CFR searches for bytecode.
         * See {@link ClassFileSource}.
         *
         * @param source class file source.
         * @return this builder.
         */
        public Builder withClassFileSource(ClassFileSource source) {
            this.source = source;
            return this;
        }

        /**
         * Allows overrides of where CFR searches for bytecode, but will fall back to default
         * behaviour if null is returned.
         * See {@link ClassFileSource}.
         *
         * @param source class file source.
         * @return this builder.
         */
        public Builder withOverrideClassFileSource(ClassFileSource source) {
            this.source = source;
            this.fallbackToDefaultSource = true;
            return this;
        }

        /**
         * Handle how results / output are provided.
         *
         * @param output see {@link OutputSinkFactory}
         * @return this builder.
         */
        public Builder withOutputSink(OutputSinkFactory output) {
            this.output = output;
            return this;
        }

        /**
         * A map, equivalent to the command line options that are passed to CFR.
         * Note.  Strong values on this are not guaranteed, however you should expect
         * that command line options to CFR do not change.
         *
         * eg { "sugarboxing" -&gt; "false" }
         *
         * You may use
         *
         * { OptionsImpl.SUGAR_BOXING.getName() -&gt; "false" }
         *
         * However, this is not guaranteed to remain in place currently, and may lead to compile / runtime
         * errors in subsequent versions.
         *
         * @param options map of options
         * @return this builder.
         */
        public Builder withOptions(Map<String, String> options) {
            this.builtOptions = OptionsImpl.getFactory().create(options);
            return this;
        }

        /**
         * Note - the {@code Options} interface is *not* guaranteed to be stable.
         * @param options previously built options.
         * @return this builder.
         */
        public Builder withBuiltOptions(Options options) {
            this.builtOptions = options;
            return this;
        }

        /**
         * Given provided artifacts, build an instance of {@link CfrDriver}.
         * Note that if artifacts are not provided, you will get default CFR behaviour.
         *
         * @return Constructed instance of {@link CfrDriver}
         */
        public CfrDriver build() {
            return new CfrDriverImpl(source, output, builtOptions, fallbackToDefaultSource);
        }
    }
}
