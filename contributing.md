# Contributing to CFR

*Thanks for visiting!*
First, a few apologies to get out of the way.  

* CFR was a personal project from 2011-2019, so there's a fair bit of cruft. 
* It's in Java 6.  This is deliberate.  This won't change.   It even runs on <a href="https://harmony.apache.org/">Apache's JVM6 clone, Harmony</a>.  
* Seriously.   One of the goals of CFR is to run in whatever hostile environment you can throw it at, and Java 6 is still a thing.
* It doesn't have any external dependencies.  I know, right?  Those wheels won't reinvent themselves.   See above, though.

# The badly phrased legal bit

You *absolutely* must not contribute, or attempt to contribute, code to which you do not hold the right to submit.   Don't cut and paste from work, m'kay?

Ok, that's out of the way.

# Great things to contribute

* Improvements to the 'test framework'.  At the moment, it's a fairly Heath Robinson-ian (or Rube Goldberg, pick your poison) contraption, to allow multiple versions of Java to be tested.  I **know** it could be better.....
* Tests.  Because CFR is Java 6 only, the tests are held in a seperate repo. <a href="https://github.com/leibnitz27/cfr_tests">cfr_tests</a>, which will build and compare several hundred exemplars in multiple versions of Java.
* Fixes for your new Tests. ;) (*hint* - if you've got a bugfix to contribute, please also have contributed a test to prove it!)

# Not great things to contribute

* Output code formatting (other than egregious fixes).  CFR can't be all things to all people, and there are lots of great tools out there to apply your own flavour of egyptian braces, non-egyptian braces, positive semi-definite braces etc.
* Java above 6.  See above.  *I know.*  But it's all syntactic sugar anyway.  **cough**.  Anyway, you get to work with $$FUNKY_NEW_LANGUAGE$$ in your day job.
* External dependencies.  *I know.*  See above.  
* Reflection.  No reflection please. 
* No-op changes.  (For example, there are quite a few intermediate variables just before a return.  I find them convenient for debugging).
* Bad Vibes.

# Before contributing

* Please start small, until I get to know you!  If you send me a 20kloc refactor out of the blue, I'm going to struggle.
* Please chat with me about what you're going to do.   You can get hold of me at lee@benf.org , or <a href="https://twitter.com/leeatbenf">@leeAtBenf</a> on Twitter.
* Please ensure that you have tested <a href="https://github.com/leibnitz27/cfr_tests">cfr_tests</a> against at least java 6, 8 and 13, and the hardcoded class files.
* Even if you pass all tests, I'll be verifying against a huge library of class files that I can't share, so passing all the tests is no guarantee you won't have broken things.  But we can work on that!
