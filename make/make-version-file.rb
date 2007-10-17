#!/usr/bin/ruby -w

require 'pathname'

#
# Mercurial
#

def getMercurialVersion(directory)
  return `hg parents | grep changeset:`
end

def extractMercurialVersionNumber(versionString)
  if versionString.match(/^changeset:\s*(\d+):/)
    return $1.to_i()
  end
  return 0
end

#
# Subversion
#

def getSubversionVersion(directory)
  command = "svnversion #{directory}"
  # svnversion is insanely slow on Cygwin using a samba share.
  if `uname` =~ /CYGWIN/ && directory.match(/^\/cygdrive\/([a-z])\/(.*)/)
    drive = $1.upcase()
    pathWithinMappedDrive = $2
    user = `whoami`.chomp()
    # "net use" tells us what machine to ssh to. martind saw one line:
    # OK           F:        \\duezer\martind          Microsoft Windows Network
    # elliotth saw two lines:
    # OK           U:        \\bertha.us.dev.bluearc.com\elliotth
    #                                                  Microsoft Windows Network
    if `net use`.match(/^OK\s+#{drive}:\s+\\\\(\S+)\\#{user}\b/)
      fileHost = $1
      # We assume that the drive is mapped to the user's home directory.
      command = "ssh #{fileHost} svnversion /home/#{user}/#{pathWithinMappedDrive}"
      $stderr.puts(command) # In case ssh(1) prompts for a password.
    end
  end
  return `#{command}`.chomp()
end

def extractSubversionVersionNumber(versionString)
  # The second number, if there are two, is the more up-to-date.
  # (In Subversion's model, commit doesn't necessarily require update.)
  if versionString.match(/^(?:\d+:)?(\d+)/)
    return $1.to_i()
  end
  # In a directory not under Subversion control, svnversion(1) says "exported".
  # This happens if you're using Bazaar, say, or nothing, or if you're building from a source tarball.
  # Returning 0 as the version number lets us build without warnings.
  return 0
end

# --------------------------------------------------------------------------------------------------------

def getWorkingCopyVersion(directory)
  if (Pathname.new(directory) + ".hg").exist?()
    return extractMercurialVersionNumber(getMercurialVersion(directory))
  elsif (Pathname.new(directory) + ".svn").exist?()
    return extractSubversionVersionNumber(getSubversionVersion(directory))
  else
    # An end-user building from source, maybe?
    return 0
  end
end

def makeVersionString(projectRootDirectory, salmaHayekRootDirectory)
  return getWorkingCopyVersion(projectRootDirectory)
end

# Despite its name, run as a script, this generates the contents for "build-revision.txt".
if __FILE__ == $0
  if ARGV.length() != 2
    $stderr.puts("usage: #{File.basename($0)} <project-root-dir>")
    exit(1)
  end
  projectRootDirectory = ARGV.shift()
  projectVersion = getWorkingCopyVersion(projectRootDirectory)
  
  require "time.rb"
  puts(Time.now().iso8601())
  puts(projectVersion)
end
