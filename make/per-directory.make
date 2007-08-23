# This makefile fragment compiles all the C/C++/Objective-C/Objective-C++ source found
# in $(SOURCE_DIRECTORY) into a single executable or JNI library.

# It is only suitable for inclusion by universal.make.

# Unusually, it is included multiple times so be careful with += etc.
# Do not define any variables here which aren't dependent
# on the particular directory being built.

# ----------------------------------------------------------------------------
# Initialize any directory-specific variables we want to append to here
# ----------------------------------------------------------------------------

LOCAL_LDFLAGS := $(LDFLAGS)
MISSING_PREREQUISITES :=

# ----------------------------------------------------------------------------
# Choose the basename(1) for the target
# ----------------------------------------------------------------------------

BASE_NAME = $(notdir $(SOURCE_DIRECTORY))

# ----------------------------------------------------------------------------
# Find the source.
# ----------------------------------------------------------------------------

# BSD find's -false is a synonym for -not.  I kid you false.
FIND_FALSE = ! -prune
findCode = $(shell find $(SOURCE_DIRECTORY) -type f '(' $(foreach EXTENSION,$(1),-name "*.$(EXTENSION)" -o) $(FIND_FALSE) ')')
SOURCES := $(call findCode,$(SOURCE_EXTENSIONS))
HEADERS := $(call findCode,$(HEADER_EXTENSIONS))

# ----------------------------------------------------------------------------
# Locate the common intermediate files.
# ----------------------------------------------------------------------------

COMPILATION_DIRECTORY = $(patsubst $(PROJECT_ROOT)/%,$(PROJECT_ROOT)/.generated/%/$(TARGET_DIRECTORY),$(SOURCE_DIRECTORY))

$(foreach EXTENSION,$(SOURCE_EXTENSIONS),$(eval $(call defineObjectsPerLanguage,$(EXTENSION))))
OBJECTS = $(strip $(foreach EXTENSION,$(SOURCE_EXTENSIONS),$(OBJECTS.$(EXTENSION))))
SOURCE_LINKS = $(patsubst $(SOURCE_DIRECTORY)/%,$(COMPILATION_DIRECTORY)/%,$(SOURCES))
HEADER_LINKS = $(patsubst $(SOURCE_DIRECTORY)/%,$(COMPILATION_DIRECTORY)/%,$(HEADERS))

# ----------------------------------------------------------------------------
# Locate the executables.
# ----------------------------------------------------------------------------

EXECUTABLES = $(BIN_DIRECTORY)/$(BASE_NAME)$(EXE_SUFFIX)
WINDOWS_SUBSYSTEM_EXECUTABLES.$(TARGET_OS) =
WINDOWS_SUBSYSTEM_EXECUTABLES.Cygwin = $(EXECUTABLES)
WINDOWS_SUBSYSTEM_EXECUTABLES = $(WINDOWS_SUBSYSTEM_EXECUTABLES.$(TARGET_OS))

# ----------------------------------------------------------------------------
# Locate the JNI library and its intermediate files.
# ----------------------------------------------------------------------------

# $(foreach) generates a space-separated list even where the elements either side are empty strings.
# $(strip) removes spurious spaces.
JNI_SOURCE = $(strip $(foreach SOURCE,$(SOURCES),$(if $(wildcard src/$(subst _,/,$(basename $(notdir $(SOURCE)))).java),$(SOURCE))))
JNI_BASE_NAME = $(basename $(notdir $(JNI_SOURCE)))
NEW_JNI_HEADER = $(COMPILATION_DIRECTORY)/new/$(JNI_BASE_NAME).h
JNI_HEADER = $(COMPILATION_DIRECTORY)/$(JNI_BASE_NAME).h
JNI_OBJECT = $(COMPILATION_DIRECTORY)/$(JNI_BASE_NAME).o
JNI_CLASS_NAME = $(subst _,.,$(JNI_BASE_NAME))

BUILDING_JNI = $(JNI_SOURCE)
LOCAL_LDFLAGS += $(if $(BUILDING_JNI),$(JNI_LIBRARY_LDFLAGS))

define JAVAHPP_RULE
$(JAVAHPP) -classpath .generated/classes $(JNI_CLASS_NAME) > $(NEW_JNI_HEADER) && \
{ cmp -s $(NEW_JNI_HEADER) $(JNI_HEADER) || cp $(NEW_JNI_HEADER) $(JNI_HEADER); }
endef

# ----------------------------------------------------------------------------
# Build shared libraries.
# ----------------------------------------------------------------------------

LOCAL_SHARED_LIBRARY_EXTENSION = $(if $(BUILDING_JNI),$(JNI_LIBRARY_EXTENSION),$(SHARED_LIBRARY_EXTENSION))
POTENTIAL_SHARED_LIBRARY = $(LIB_DIRECTORY)/$(patsubst liblib%,lib%,$(SHARED_LIBRARY_PREFIX)$(BASE_NAME)).$(LOCAL_SHARED_LIBRARY_EXTENSION)

BUILDING_SHARED_LIBRARY =
BUILDING_SHARED_LIBRARY += $(filter lib%,$(notdir $(SOURCE_DIRECTORY)))
BUILDING_SHARED_LIBRARY += $(BUILDING_JNI)

LOCAL_LDFLAGS += $(if $(strip $(BUILDING_SHARED_LIBRARY)),$(SHARED_LIBRARY_LDFLAGS))
SHARED_LIBRARY = $(if $(strip $(BUILDING_SHARED_LIBRARY)),$(POTENTIAL_SHARED_LIBRARY))

# ----------------------------------------------------------------------------
# Add Cocoa frameworks if we're building Objective-C/C++.
# ----------------------------------------------------------------------------

PRIVATE_FRAMEWORKS_DIRECTORY = /System/Library/PrivateFrameworks

BUILDING_COCOA = $(filter $(OBJECTIVE_SOURCE_PATTERNS),$(SOURCES))

LOCAL_LDFLAGS += $(if $(BUILDING_COCOA),-framework Cocoa)
LOCAL_LDFLAGS += $(if $(BUILDING_COCOA),-F$(PRIVATE_FRAMEWORKS_DIRECTORY))

headerToFramework = $(PRIVATE_FRAMEWORKS_DIRECTORY)/$(basename $(notdir $(1))).framework
frameworkToLinkerFlag = -framework $(basename $(notdir $(1)))

PRIVATE_FRAMEWORK_HEADERS = $(filter $(SOURCE_DIRECTORY)/PrivateFrameworks/%,$(HEADERS))
PRIVATE_FRAMEWORKS_USED = $(foreach HEADER,$(PRIVATE_FRAMEWORK_HEADERS),$(call headerToFramework,$(HEADER)))
LOCAL_LDFLAGS += $(foreach PRIVATE_FRAMEWORK,$(PRIVATE_FRAMEWORKS_USED),$(call frameworkToLinkerFlag,$(PRIVATE_FRAMEWORK)))

MISSING_PRIVATE_FRAMEWORKS := $(filter-out $(wildcard $(PRIVATE_FRAMEWORKS_USED)),$(PRIVATE_FRAMEWORKS_USED))
MISSING_PREREQUISITES += $(MISSING_PRIVATE_FRAMEWORKS)

# ----------------------------------------------------------------------------
# Decide on the default target.
# ----------------------------------------------------------------------------

DESIRED_TARGETS = $(if $(strip $(SHARED_LIBRARY)),$(SHARED_LIBRARY),$(EXECUTABLES))
DEFAULT_TARGETS = $(if $(strip $(MISSING_PREREQUISITES)),missing-prerequisites.$(BASE_NAME),$(DESIRED_TARGETS))

define MISSING_PREREQUISITES_RULE
  @echo "*** Can't build $(BASE_NAME) because of missing prerequisites:" && \
  $(foreach PREREQUISITE,$(MISSING_PREREQUISITES),echo "  \"$(PREREQUISITE)\"" &&) \
  true
endef

# ----------------------------------------------------------------------------
# Target-specific variables.
# These need to be assigned while the right hand side is valid so need to use :=
# That means they should be after the right hand side is finalized which means
# after other assignments.
# ----------------------------------------------------------------------------

$(WINDOWS_SUBSYSTEM_EXECUTABLES): LOCAL_LDFLAGS += -Wl,--subsystem,windows
$(EXECUTABLES) $(SHARED_LIBRARY): LDFLAGS := $(LOCAL_LDFLAGS)
$(NEW_JNI_HEADER): RULE := $(JAVAHPP_RULE)
missing-prerequisites.$(BASE_NAME): RULE := $(MISSING_PREREQUISITES_RULE)

# ----------------------------------------------------------------------------
# Variables above this point,
# rules below...
# ----------------------------------------------------------------------------

# ----------------------------------------------------------------------------
# Our linked targets.
# ----------------------------------------------------------------------------

$(EXECUTABLES) $(SHARED_LIBRARY): $(OBJECTS)
	@echo Linking $(notdir $@)...
	mkdir -p $(@D) && \
	$(LD) $^ -o $@ $(LDFLAGS)

# ----------------------------------------------------------------------------
# Generate our JNI header.
# ----------------------------------------------------------------------------

ifneq "$(JNI_SOURCE)" ""

$(NEW_JNI_HEADER): .generated/java.build-finished $(JAVAHPP) $(SALMA_HAYEK)/.generated/classes/e/tools/JavaHpp.class
	@echo "Generating JNI header..."
	mkdir -p $(@D) && \
	$(RM) $@ && \
	$(RULE)

$(JNI_OBJECT): $(JNI_HEADER)
build: $(NEW_JNI_HEADER)
$(JNI_HEADER): $(NEW_JNI_HEADER);

endif

# ----------------------------------------------------------------------------
# Rules for compiling Objective C and Objective C++ source.
# ----------------------------------------------------------------------------

$(OBJECTS.m): %.o: %.m
	$(COMPILE.m) $(OUTPUT_OPTION) $<

$(OBJECTS.mm): %.o: %.mm
	$(COMPILE.mm) $(OUTPUT_OPTION) $<

# ----------------------------------------------------------------------------
# What to do if something we need isn't installed but we want to continue building everything else.
# ----------------------------------------------------------------------------

.PHONY: missing-prerequisites.$(BASE_NAME)
missing-prerequisites.$(BASE_NAME):
	$(RULE)

# ----------------------------------------------------------------------------
# Create "the build tree", GNU-style.
# ----------------------------------------------------------------------------

# This way, we can use compilation rules which assume everything's
# in the same directory.
# FIXME: Copies of files which no longer exist must be removed.
$(SOURCE_LINKS) $(HEADER_LINKS): $(COMPILATION_DIRECTORY)/%: $(SOURCE_DIRECTORY)/%
	$(COPY_RULE)

# ----------------------------------------------------------------------------
# Dependencies.
# ----------------------------------------------------------------------------

# Rather than have the compiler track dependencies we
# conservatively assume that if a header files changes, we have to recompile
# everything.
$(OBJECTS): $(HEADER_LINKS) $(HEADERS) $(MAKEFILE_LIST)
$(OBJECTS): $(wildcard $(SALMA_HAYEK)/native/Headers/*)
