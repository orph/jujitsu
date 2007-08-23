#!/usr/bin/ruby -w

require "pathname"

script_path = Pathname.new(__FILE__).realpath().dirname()
$: << script_path.dirname().to_s()

require "patch-to-html-email.rb"

from_address = ENV["LOGNAME"]
if from_address == ""
  from_address = `whoami`.chomp()
end
to_address = from_address
reply_to_address = nil
subject = "patch"
preamble = ""
changes = ARGF.readlines()

sendHtmlEmail(from_address, to_address, reply_to_address, subject, preamble, changes)

exit(0)
