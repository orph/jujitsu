#!/usr/bin/ruby -w

# Cope with symbolic links to this script.
require "pathname.rb"
salma_hayek = Pathname.new(__FILE__).realpath().dirname().dirname()

require "lib/invoke-java.rb"
Java.runCommandLineTool("e/tools/JavaHpp")
