# Building CFR

Dead easy!

Just ensure you have <a href="https://maven.apache.org/">maven</a> installed.

Then `mvn compile` in the root will get you what you need.

Note: if you encounter a `maven-compiler-plugin...: Compilation failure` error while trying to compile the project then your `JAVA_HOME` environment variable is probably pointing to a JDK version that doesn't support `6` for the `source` or `target` compile options.
Fix this by pointing `JAVA_HOME` to a JDK version that still supports compiling to java 1.6 such as JDK 10 (successfully tested). Also note, the version of java on your `path` may need to be greater than 1.6 if you are using `maven` version `>=3.3.1` which requires java 1.7. The best solution is to use JDK 8, 9, or 10 for both your `path` and `JAVA_HOME`

The main class is `org.benf.cfr.reader.Main`, so once you've built, you can test it out (from `target/classes`)

`java org.benf.cfr.reader.Main java.lang.Object`

to get CFR to decompile `java.lang.Object`.
