# CFR - Another Java Decompiler \o/

This is the public repository for the CFR decompiler, main site hosted at <a href="https://www.benf.org/other/cfr">benf.org/other/cfr</a>

CFR will decompile modern Java features - <a href="https://www.benf.org/other/cfr/java9observations.html">including much of Java <a href="java9stringconcat.html">9</a>, <a href="https://www.benf.org/other/cfr/switch_expressions.html">12</a> &amp; <a href="https://www.benf.org/other/cfr/java14instanceof_pattern">14</a>, but is written entirely in Java 6, so will work anywhere!  (<a href="https://www.benf.org/other/cfr/faq.html">FAQ</a>) - It'll even make a decent go of turning class files from other JVM languages back into java!</p>

To use, simply run the specific version jar, with the class name(s) you want to decompile (either as a path to a class file, or as a fully qualified classname on your classpath).
(`--help` to list arguments).

Alternately, to decompile an entire jar, simply provide the jar path, and if you want to emit files (which you probably do!) add `--outputdir /tmp/putithere`.

# Getting CFR

The main site for CFR is <a href="https://www.benf.org/other/cfr">benf.org/other/cfr</a>, where releases are available with a bunch of rambling musings from the author.

Since 0.145, Binaries are published on github along with release tags.

You can also download CFR from your favourite <a href="https://mvnrepository.com/artifact/org.benf/cfr">maven</a> repo, though releases are published a few days late usually, to allow for release regret.

# Issues

If you have an issue, please **_DO NOT_** include copyright materials.  I will have to delete your issue.

# Building CFR

Dead easy!

Just ensure you have [Maven](https://maven.apache.org/) installed. Then `mvn compile` in the root directory of this project will get you what you need.

Note: If you encounter a `maven-compiler-plugin...: Compilation failure` error while trying to compile the project then your `JAVA_HOME` environment variable is probably pointing to a JDK version that doesn't support `6` for the `source` or `target` compile options.
Fix this by pointing `JAVA_HOME` to a JDK version that still supports compiling to Java 1.6 such as JDK 11. Also note, the version of Java on your `JAVA` may need to be greater than 1.6 if you are using Maven version `>=3.3.1` which requires Java 1.7. The best solution is to use JDK 8, 9, 10 or 11 for both your `PATH` and `JAVA_HOME`.

The main class is `org.benf.cfr.reader.Main`, so once you've built, you can test it out (from `target/classes`)
```
java org.benf.cfr.reader.Main java.lang.Object
```
to get CFR to decompile `java.lang.Object`.


## Decompilation tests

As part of the Maven build automatic decompilation tests are performed, they verify that the current decompiled output of CFR matches the expected previous output. The test data (Java class and JAR files) are part of a separate Git repository; it is therefore necessary to clone this repository with `git clone --recurse-submodules`. The expected output and CFR test configuration is however part of this repository to allow altering it without having to modify the corresponding test data. The test data is in the `decompilation-test/test-data` directory, and the respective expected data is in the `decompilation-test/test-data-expected-output` directory (with a similar directory structure).

The decompilation test is also performed by the GitHub workflow and in case of test failures the unified diff is available in a workflow artifact called "decompilation-test-failures-diff".

The expected output is not the gold standard, it merely describes the currently expected output. There is nothing wrong with adjusting the expected output, if the changes to the decompilation results are reasonable.

When adding new test data the system property `cfr.decompilation-test.create-missing` can be used (with `-D...` from command line) to automatically generate all missing expected test data based on the current CFR output. The tests will still fail then (to indicate that no previous expected data existed), but afterwards the generated data can be reviewed to verify that the CFR output is reasonable, and the tests will then pass.
