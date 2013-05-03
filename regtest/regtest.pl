#!/usr/bin/perl
use File::Slurp;

# use -wB to ignore blanks

my $arg = $ARGV[0];
my $base = "";
my @files = `ls -1 expected`;
chomp @files;

foreach my $file (@files) {
    print "[$file]";
    my $path = "../out/production/cfr/org/benf/cfr/tests/$file.class";
    my $cmd = "java -classpath ../out/production/cfr org.benf.cfr.reader.Main $path";
    my $actualtext = `$cmd`;
    my $expected = read_file("expected/$file");
    if ($actualtext ne $expected) {
      if ($arg eq "r") {
        `$cmd > /tmp/cfrtest.tmp`;
        $res = system("diff expected/$file /tmp/cfrtest.tmp");	      
        print "($res)\n";
        print "[$cmd > expected/$file]\n";
        print " ** FAIL ** Accept new? (y?)";
        my $key = <STDIN>;
	chomp $key;        
        if ($key eq "y") {
	  `cp /tmp/cfrtest.tmp expected/$file`;
        }
        print "\n";
      } else {
        print "[$cmd]\n";
        print " ** FAIL **";
      }
    } 
    print "\n";
}	
