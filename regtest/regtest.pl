#!/usr/bin/perl
use File::Slurp;

my $base = "";
if (scalar(@ARGV) > 2) { $base = $ARGV[1]; }
my @files = `ls -1 expected`;
chomp @files;

foreach my $file (@files) {
    print "[$file]";
    my $path = "../out/production/cfr/org/benf/cfr/tests/$file.class";
    my $cmd = "java -classpath ../out/production/cfr org.benf.cfr.reader.Main $path";
    my $actualtext = `$cmd`;
    my $expected = read_file("expected/$file");
    if ($actualtext ne $expected) {
      print "[$cmd]\n";
      print " ** FAIL **";
    } 
    print "\n";
}	
