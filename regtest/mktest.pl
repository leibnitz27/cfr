#!/usr/bin/perl
use File::Slurp;

my $file = $ARGV[0];

    print "[$file]";
    my $path = "../out/production/cfr/org/benf/cfr/tests/$file.class";
    my $cmd = "java -classpath ../out/production/cfr org.benf.cfr.reader.Main $path > expected/$file";
    `$cmd`;
my $expected = read_file("expected/$file");
print $expected;
print "\n----\n$cmd\n";
