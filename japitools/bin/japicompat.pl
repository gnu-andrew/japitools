#!/usr/bin/perl
###############################################################################
# japicompat - Test Java APIs for binary backwards compatibility.
# Copyright (C) 2000  Stuart Ballard <sballard@wuffies.net>
# 
# This program is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
# 
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
# 
# You should have received a copy of the GNU General Public License
# along with this program; if not, write to the Free Software
# Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
# 
# A link to the GNU General Public License is available at the
# japitools homepage, http://stuart.wuffies.net/japi/
###############################################################################

# Recent changes:
# - 2000/06/26: Added progress indicators. Turn them off with -q.
# - 2000/06/23: Made compiletime-constants actually be understood, rather than
#   just treated as part of the type. Added Meaningless, Arbitrary Percentage
#   scores.
# - 2000/06/18: Added support for SerialVersionUIDs, fixed a mis-counting bug
#   caused by the elimination of duplicate errors ("missing" errors were being
#   counted even if they were dups). Added getopt support.
# - 2000/06/16: Finished the dup-removal code (Kaffe now shows 140 errors
#   instead of 429).

# Parse cmdline and give a usage message.
use Getopt::Std;
getopts("svq");
my $sun_only = 1 if $opt_s;
my $svuid_errors = 1 if $opt_v;
my $dot = "." unless $opt_q;
my ($origfile, $newfile) = @ARGV;
if (!defined $newfile) {
  print "Usage: japicompat.pl [-svq] <original api> <api to check>\n";
  exit 1;
}

# Read a japi file into a huge enormous frikkin' hash.
# The format is $japi->{$pkg}->{$class}->{$member}->...
# Things related to the class itself are stored in member->{""}, to avoid
# clashes with fields of the same name.
sub read_japi($) {
  my ($filename) = @_;
  my $japi = {};
  open IN, $filename or die "Could not open $filename";
  print STDERR "Loading $filename" if $dot;
  while (<IN>) {
    chomp;
  
    # Parse and interpret the entry.
    my ($item, $access, $abstract, $static, $final, $type) = split / /, $_, 6;
    my ($class, $member) = split /#/, $item, 2;
    my @comps = split /\./, $class;
    my $class = pop @comps;
    $pkg = join ".", @comps;
    my $isa;
    if ($member eq "") {
      $isa = $type =~ /class/ ? "class" : "interface";
    } elsif ($member =~ /^\(.*\)$/) {
      $isa = "constructor";
    } elsif ($member =~ /\(.*\)$/) {
      $isa = "method";
    } else {
      $isa = "field";
    }

    # Store information about the entry in $japi->{$pkg}->{$class}->{$member}.
    print STDERR $dot unless defined $japi->{$pkg}->{$class};
    $japi->{$pkg}->{$class}->{$member}->{isa} = $isa;
    $japi->{$pkg}->{$class}->{$member}->{class} = $class;
    $japi->{$pkg}->{$class}->{$member}->{member} = $member;
    $japi->{$pkg}->{$class}->{$member}->{access} = $access;
    $japi->{$pkg}->{$class}->{$member}->{abstract} = $abstract;
    $japi->{$pkg}->{$class}->{$member}->{static} = $static;
    $japi->{$pkg}->{$class}->{$member}->{final} = $final;

    # Classes and interfaces have superclasses and implemented interfaces tacked
    # on to the "type" field. We store this information in the $japi hash also.
    if ($member eq "") {

      # Get the interfaces data, which is separated by '*'s from the classname.
      my @ifaces = split(/\*/, $type);
      $type = shift @ifaces;
      foreach my $iface (@ifaces) {
        $japi->{$pkg}->{$class}->{$member}->{ifaces}->{$iface} = 1;
      }

      # Get the class's superclasses, which are separated by ':'s.
      my @supers = split(/:/, $type);
      $type = shift @supers;
      my $ct = 0;
      foreach my $super (@supers) {
        $japi->{$pkg}->{$class}->{$member}->{superset}->{$super} = 1;
        $japi->{$pkg}->{$class}->{$member}->{supers}->[$ct++] = $super;
      }

      my $svuid;
      ($type, $svuid) = split(/#/, $type, 2);
      $japi->{$pkg}->{$class}->{$member}->{svuid} = $svuid;

    # Methods and constructors have exceptions that can be thrown separated by
    # '*'s from the typename. These also need to get stored in the $japi hash.
    } elsif ($member =~ /\(.*\)$/) {
      my @excps = split(/\*/, $type);
      $type = shift @excps;
      foreach my $excp (@excps) {
        $japi->{$pkg}->{$class}->{$member}->{excps}->{$excp} = 1;
      }
    # Fields can have their value separated by a : from the typename, if they
    # are constant.
    } else {
      my $val;
      ($type, $val) = split(/:/, $type, 2);
      if (defined $val) {
        $japi->{$pkg}->{$class}->{$member}->{constant} = $val;
      }
    }

    # Store what's left of the type after parsing off all of those parts.
    $japi->{$pkg}->{$class}->{$member}->{type} = $type;
  }
  close IN;
  print STDERR "\n" if $dot;

  # Return the hash we have created.
  $japi;
}

# Print out an error for a given class. Errors on fields and methods that
# also exist identically in the superclass or an interface are skipped. This
# function also keeps track of the problem counts.
sub report_error($$$$$$) {
  my ($old, $new, $pkg, $class, $member, $error) = @_;
  my $clitem = $old->{$pkg}->{$class};
  my $mitem = $clitem->{$member};
  my $nitem = $new->{$pkg}->{$class}->{$member};
  my $dup = 0;

  # Scan superclass and all superinterfaces for the same error...
  if ($mitem->{isa} eq "method" || $mitem->{isa} eq "field") {
    if (defined $clitem->{""}->{supers}) {
      foreach my $super (@{$clitem->{""}->{supers}}) {
        my @comps = split /\./, $super;
        my $sclass = pop @comps;
        my $spkg = join ".", @comps;
        if (defined $old->{$spkg}->{$sclass}) {
  
          # This hideous hash location is where we store errors
          if ($old->{$spkg}->{$sclass}->{""}->{errors}->{$member}->{$error}) {
            $dup = 1;
          }
          last;
        }
      }
    }
    foreach my $super (keys %{$clitem->{""}->{ifaces}}) {
      my @comps = split /\./, $super;
      my $sclass = pop @comps;
      my $spkg = join ".", @comps;
      # This hideous hash location is where we store errors
      if ($old->{$spkg}->{$sclass}->{""}->{errors}->{$member}->{$error}) {
        $dup = 1;
      }
    }

    # Store the error for the check above to find on subclasses.
    $clitem->{""}->{errors}->{$member}->{$error} = 1;

    return 0 if $dup;
  }

  # If it wasn't a duplicate, report and output it...
  print "$mitem->{isa} $pkg.$class#$member $error\n";
  $errs++; $errhere++;
  1;
}

# Run all the relevant compatibility checks for a class in the "old" api.
# Classes already processed are skipped, making it possible for every class
# to ask all its superclasses to process themselves before its own processing
# is done.
sub process_class($$$$) {
  my ($old, $new, $pkg, $class) = @_;
  my $clitem = $old->{$pkg}->{$class};

  # process_class will never get called on a class that isn't defined.
  die "Class not found: $pkg.$class" unless defined $clitem;

  # Avoid potential infinite recursion by storing a special value for classes
  # we are in the *middle* of processing. Skip processing if we have already
  # processed the class.
  die "Infinite recursion averted" if $clitem->{""}->{processed} eq "x";
  unless ($clitem->{""}->{processed}) {
    die "File format error: missing class entry for $pkg.$class"
      unless $clitem->{""};
    $clitem->{""}->{processed} = "x";

    # Make sure each superclass is processed before we get processed, so that
    # we can detect errors that are just inherited from the superclass.
    if (defined $clitem->{""}->{supers}) {
      foreach my $super (@{$clitem->{""}->{supers}}) {
        my @comps = split /\./, $super;
        my $sclass = pop @comps;
        my $spkg = join ".", @comps;
        if (defined $old->{$spkg}->{$sclass}) {
          process_class($old, $new, $spkg, $sclass);
          last;
        }
      }
    }
    # Make sure each superinterface is processed before we get processed, so
    # that we can detect errors that are just inherited from the superinterface.
    foreach my $super (keys %{$clitem->{""}->{ifaces}}) {
      my @comps = split /\./, $super;
      my $sclass = pop @comps;
      my $spkg = join ".", @comps;
      if (defined $old->{$spkg}->{$sclass}) {
        process_class($old, $new, $spkg, $sclass);
      }
    }

    # Make sure the class itself is present in the new japi.
    # We use the {isa} entry to distinguish between a class and an interface.
    unless (defined $new->{$pkg}->{$class}) {
      print "$clitem->{''}->{isa} $pkg.$class not defined in $newfile\n";
      $errs++; $missing{$clitem->{""}->{isa}}++;
      $total += keys %$clitem; $misstotal += keys %$clitem;
    } else {

      # Check for the case where an abstract method has been added to a class
      # (which also catches methods being added to interfaces).
      # This is not in the JLS so we only do it if not sun_only.
      my $nclitem = $new->{$pkg}->{$class};
      unless ($sun_only) {
        foreach my $member (keys %$nclitem) {
          my $nitem = $nclitem->{$member};
          if ($nitem->{isa} eq "method" && $nitem->{abstract} eq "abstract" &&
              !defined $clitem->{$member}) {
            $clitem->{$member} = $nitem;
            report_error($old, $new, $pkg, $class, $member, 
                         "abstract method added to $clitem->{''}->{isa} in $newfile");
            delete $clitem->{$member};
          }
        }
      }

      # Now we perform the basic checks on all the members of the class... this
      # includes the "" entry, which we don't treat specially here.
      foreach my $member (sort keys %$clitem) {

        # Store some friendly shortcuts to things we will use a lot.
        my $mitem = $clitem->{$member};
        my $nitem = $nclitem->{$member};

        # errhere is global because it gets modified by report_error().
        $errhere = 0;
        my $isa = $mitem->{isa};
        my $item = "$pkg.$class#$member";

        # Check that the item is actually defined in $new.
        unless (defined($nitem)) {
          $missing{$isa} += report_error($old, $new, $pkg, $class, $member,
                                         "not defined in $newfile");
          $misstotal++; $total++;
          next;
        }

        # Check that access to the item hasn't been reduced.
        if ($mitem->{access} eq "public" && $nitem->{access} ne "public") {
          report_error($old, $new, $pkg, $class, $member,
                       "is public in $origfile but protected in $newfile");
        }

        # Check that the item hasn't changed from concrete to abstract.
        if ($mitem->{abstract} eq "concrete" && $nitem->{abstract} ne "concrete") {
          report_error($old, $new, $pkg, $class, $member,
                       "is concrete in $origfile but abstract in $newfile");
        }

        # Check that the staticness of the item hasn't changed
        if ($mitem->{static} ne $nitem->{static}) {
          report_error($old, $new, $pkg, $class, $member,
                       "is $mitem->{static} in $origfile but $nitem->{static} in $newfile");
        }

        # Check that the item hasn't gone from nonfinal to final, except
        # for static methods.
        if ($mitem->{final} eq "nonfinal" && $nitem->{final} ne "nonfinal" &&
            ($mitem->{isa} ne "method" || $mitem->{static} ne "static")) {
          report_error($old, $new, $pkg, $class, $member,
                       "is nonfinal in $origfile but final in $newfile");
        }

        # For classes and interfaces, check that nothing has been removed from
        # the set of super-interfaces or superclasses.
        if ($member eq "") {
          foreach my $iface (keys %{$mitem->{ifaces}}) {
            unless ($nitem->{ifaces}->{$iface}) {
              report_error($old, $new, $pkg, $class, $member,
                           "implements $iface in $origfile but not in $newfile");
            }
          }
          my $super = $mitem->{supers}->[0];
          if (defined $super and !$nitem->{superset}->{$super}) {
            report_error($old, $new, $pkg, $class, $member,
                         "'s superclass $super in $origfile is not its superclass in $newfile");
          }

          # Also check the SerialVersionUID if that is turned on.
          if ($svuid_errors && defined $mitem->{svuid}) {
            if (!defined $nitem->{svuid}) {
              report_error($old, $new, $pkg, $class, $member,
                           "has SerialVersionUID $mitem->{svuid} in $origfile but no SVUID in $newfile");
            } elsif ($nitem->{svuid} ne $mitem->{svuid}) {
              report_error($old, $new, $pkg, $class, $member,
                           "has SerialVersionUID $mitem->{svuid} in $origfile but $nitem->{svuid} in $newfile");
            }
          }

        # For methods and constructors, check that the set of thrown exceptions
        # is the same. The JLS does not specify this so only do it if not
        # sun_only.
        } elsif ($member =~ /\(.*\)/ && !$sun_only) {
          foreach my $excp (keys %{$mitem->{excps}}) {
            unless ($nitem->{excps}->{$excp}) {
              report_error($old, $new, $pkg, $class, $member,
                           "throws $excp in $origfile but not in $newfile");
            }
          }
          foreach my $excp (keys %{$nitem->{excps}}) {
            unless ($mitem->{excps}->{$excp}) {
              report_error($old, $new, $pkg, $class, $member,
                           "throws $excp in $newfile but not in $origfile");
            }
          }

        # For fields, check the constant value if there is one.
        } else {
          if (defined $mitem->{constant}) {
            if (!defined $nitem->{constant}) {
              report_error($old, $new, $pkg, $class, $member,
                           "is constant $mitem->{constant} in $origfile but not in $newfile");
            } elsif ($nitem->{constant} ne $mitem->{constant}) {
              report_error($old, $new, $pkg, $class, $member,
                           "is constant $mitem->{constant} in $origfile but constant $nitem->{constant} in $newfile");
            }
          }
        }

        # Check that the item's type has remained the same.
        if ($mitem->{type} ne $nitem->{type}) {
          unless ($mitem->{isa} eq "field" && $mitem->{type} !~ /:/ &&
                  $nitem->{type} =~ /^\Q$mitem->{type}\E:/) {
            report_error($old, $new, $pkg, $class, $member,
                         "'s type in $origfile [$mitem->{type}] does not match $newfile [$nitem->{type}]");
          }
        }

        # Update the summary results.
        if ($errhere) {
          $bad{$isa}++;
          $badtotal++;
        } else {
          $good{$isa}++;
          $goodtotal++;
        }
        $total++;
      }
    }

    # Store that this class has now been processed.
    $clitem->{""}->{processed} = 1;
  }
}

# Read in the old and new APIs.
$new = read_japi($newfile);
$orig = read_japi($origfile);
$errs=0; %missing=(); %bad=(); %good=();
$total=0; $misstotal=0; $goodtotal=0; $badtotal=0;

# Loop through all the packages in the original API and process them.
foreach my $pkg (keys %$orig) {
  print STDERR "Comparing $pkg" if $dot;

  # If a whole package is missing, don't waste time generating missing class
  # errors for every single class.
  unless ($new->{$pkg}) {
    print "package $pkg defined in $origfile but not in $newfile\n";
    $missing{package}++;
    foreach my $class (keys %{$new->{pkg}}) {
      $total += keys %{$new->{$pkg}->{$class}};
      $misstotal += keys %{$new->{$pkg}->{$class}};
    }
  } else {
    $good{package}++;

    # Loop through the classes in the original package and process them.
    foreach my $class (keys %{$orig->{$pkg}}) {
      process_class($orig, $new, $pkg, $class);
      print STDERR $dot;
    }
  }
  print STDERR "\n" if $dot;
}

# Print summary information.
foreach my $x (qw(package class interface field constructor method)) {
  foreach my $ct (\%missing, \%bad, \%good) {
    $ct->{$x} = "none" unless $ct->{$x};
  }
}
$mpct = $1 if (0.005 + 100*$misstotal/$total) =~ /^([0-9]+(?:\.[0-9]{1,2})?)/;
$bpct = $1 if (0.005 + 100*$badtotal/$total) =~ /^([0-9]+(?:\.[0-9]{1,2})?)/;
$gpct = $1 if (0.005 + 100*$goodtotal/$total) =~ /^([0-9]+(?:\.[0-9]{1,2})?)/;
print <<EOF
Packages: $missing{package} missing, $good{package} good.
Classes: $missing{class} missing, $bad{class} bad, $good{class} good.
Interfaces: $missing{interface} missing, $bad{interface} bad, $good{interface} good.
Fields: $missing{field} missing, $bad{field} bad, $good{field} good.
Constructors: $missing{constructor} missing, $bad{constructor} bad, $good{constructor} good.
Methods: $missing{method} missing, $bad{method} bad, $good{method} good.
Total Errors: $errs.
Meaningless API coverage score: $mpct% missing, $bpct% bad, $gpct% good.
EOF
