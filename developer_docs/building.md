# Building CFR

Dead easy!

Just ensure you have <a href="https://maven.apache.org/">maven</a> installed.

Then `mvn compile` in the root will get you what you need.

The main class is `org.benf.cfr.reader.Main`, so once you've built, you can test it out (from `target/classes`)

`java org.benf.cfr.reader.Main java.lang.Object`

to get CFR to decompile `java.lang.Object`.
