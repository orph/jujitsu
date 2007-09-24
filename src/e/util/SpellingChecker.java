package e.util;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Uses an ispell(1)-compatible back end to check spelling.
 */
public class SpellingChecker {
    private static final SpellingChecker instance = new SpellingChecker();
    private static final boolean DEBUGGING = false;
    
    private static final Stopwatch stopwatch = Stopwatch.get("SpellingChecker");
    
    private static HashSet<String> knownGood = new HashSet<String>();
    private static HashSet<String> knownBad = new HashSet<String>();
    
    private Process ispell;
    private PrintWriter out;
    private BufferedReader in;
    
    /** Returns the single instance of SpellingChecker. */
    public static synchronized SpellingChecker getSharedSpellingCheckerInstance() {
        return instance;
    }
    
    /** Establishes the connection to ispell, if possible. */
    private SpellingChecker() {
        // On Mac OS, we want to use the system's spelling checker, so try our NSSpell utility (which gives Apple's code an ispell-like interface) first.
        // Our start-up scripts ensure it's on our path if we're running on a Mac.
        // Otherwise try aspell(1) -- also used by gedit(1) -- first, and fall back to good old ispell(1).
        String[] backEnds = { "NSSpell", "aspell", "ispell" };
        for (String backEnd : backEnds) {
            if (FileUtilities.findOnPath(backEnd) != null) {
                if (connectTo(new String[] { backEnd, "-a" })) {
                    return;
                }
            }
        }
        Log.warn("SpellingChecker: failed to find any back end. Please install aspell(1) or ispell(1).");
    }
    
    /** Attempts to connect to the given command-line spelling checker, which must be compatible with ispell's -a mode. */
    private boolean connectTo(String[] execArguments) {
        try {
            ispell = Runtime.getRuntime().exec(execArguments);
            in = new BufferedReader(new InputStreamReader(ispell.getInputStream()));
            out = new PrintWriter(ispell.getOutputStream());
            
            String greeting = in.readLine();
            if (greeting.startsWith("@(#) International Ispell ")) {
                Log.warn("SpellingChecker: connected to " + execArguments[0] + " okay: " + greeting + ".");
            } else {
                throw new IOException("Garbled ispell response: " + greeting);
            }
            out.println("!"); // Set terse mode.
            out.flush();
            return true;
        } catch (IOException ex) {
            Log.warn("SpellingChecker: couldn't start " + execArguments[0] + " (" + ex.getMessage() + "), though it was on the path.");
            ispell = null;
            in = null;
            out = null;
            return false;
        }
    }
    
    /**
     * Tests whether the given word is misspelled.
     * If ispell is unavailable, no words are considered misspelled.
     * We only ask ispell about any given word at most once: the
     * knownGood and knownBad HashSets are used to save on
     * expensive inter-process communication.
     */
    public synchronized boolean isMisspelledWord(String word) {
        if (ispell == null) {
            debug("ispell == null");
            return false;
        }
        
        word = word.toLowerCase();
        
        // Check the known-good words first, because good words should be more common.
        if (knownGood.contains(word)) {
            return false;
        }
        // Then check the known-bad words.
        if (knownBad.contains(word)) {
            return true;
        }
        // Then give in and ask ispell.
        boolean misspelled = isMisspelledWordAccordingToIspell(word, null);
        
        // Ensure that this word makes its way into one set or the other.
        // We copy the word into a new string to avoid accidental retention
        // of character arrays representing documents in their entirety.
        (misspelled ? knownBad : knownGood).add(new String(word));
        return misspelled;
    }
    
    public synchronized String[] getSuggestionsFor(String misspelledWord) {
        ArrayList<String> suggestions = new ArrayList<String>();
        boolean isMisspelled = isMisspelledWordAccordingToIspell(misspelledWord, suggestions);
        if (isMisspelled == false) {
            return new String[0];
        }
        return suggestions.toArray(new String[suggestions.size()]);
    }
    
    /**
     * Moves the word from the known bad set to the known good set,
     * and inserts it into the user's personal ispell dictionary.
     */
    public synchronized void acceptSpelling(String word) {
        if (isMisspelledWord(word) == false) {
            return;
        }
        
        // knownBad and knownGood only contain lowercase words.
        String setWord = word.toLowerCase();
        knownBad.remove(setWord);
        knownGood.add(setWord);
        
        // Send the word to ispell to insert into the personal dictionary.
        // FIXME: we pass it through with its original case, but if it's not all lowercase, ispell(1) takes that to mean that it should only accept that capitalization. This may not be the right choice.
        out.println("*" + word);
        out.flush();
    }
    
    public static synchronized void dumpKnownBadWordsTo(PrintStream out) {
        // Get a sorted list of the known bad words.
        ArrayList<String> words = new ArrayList<String>();
        for (String word : knownBad) {
            words.add(word);
        }
        Collections.sort(words);
        
        // Dump them.
        out.println("SpellingChecker's set of known-bad words:");
        for (int i = 0; i < words.size(); i++) {
            out.println(words.get(i));
        }
        out.println("=" + words.size());
    }
    
    private boolean isMisspelledWordAccordingToIspell(String word, Collection<String> returnSuggestions) {
        Stopwatch.Timer timer = stopwatch.start();
        try {
            // Send the word to ispell for checking.
            String request = "^" + word;
            debug(request);
            out.println(request);
            out.flush();
            
            // ispell's response will be one of:
            // 1. a blank line (meaning "correctly spelled"),
            // 2. lines beginning with [&?#] containing suggested corrections, followed by a blank line.
            String response = in.readLine();
            
            // A blank line means "correctly spelled".
            if (response.length() == 0) {
                debug("\"" + word + "\" response length == 0");
                return false;
            }
            
            // &: near-miss
            // ?: guess
            // #: no suggestions
            boolean misspelled = true;
            while (response.length() > 0 && "&?#+-".indexOf(response.charAt(0)) != -1) {
                debug(" " + response);
                
                if (response.charAt(0) == '&' && isCorrectIgnoringCase(word, response)) {
                    misspelled = false;
                }
                
                if (returnSuggestions != null) {
                    fillCollectionWithSuggestions(response, returnSuggestions);
                }
                
                response = in.readLine();
            }
            
            if (response.length() != 0) {
                Log.warn("SpellingChecker: garbled response: \"" + response + "\"");
            }
            
            return misspelled;
        } catch (IOException ex) {
            // What do we know, other than we failed to get an answer?
            // Should we stop talking to ispell?
            Log.warn("SpellingChecker: I/O error.", ex);
            return false;
        } finally {
            timer.stop();
        }
    }
    
    /**
     * Tests whether a spelling would be correct if we didn't care about case.
     * In code, case is often dependent on naming conventions rather than
     * linguistics. So 'british' might be a reasonable identifier, and we might
     * say 'isAscii' instead of 'isASCII'.
     */
    private boolean isCorrectIgnoringCase(String word, String response) {
        /*
         * From the ispell(1) man page:
         * 
         * If the word is not in the dictionary, but there are near  misses,  then
         * the  line  contains  an '&', a space, the misspelled word, a space, the
         * number of near misses, the number of characters between  the  beginning
         * of  the line and the beginning of the misspelled word, a colon, another
         * space, and a list of the near misses separated by  commas  and  spaces.
         * Following  the  near  misses  (and identified only by the count of near
         * misses), if the word could be formed by adding (illegal) affixes  to  a
         * known root, is a list of suggested derivations, again separated by com-
         * mas and spaces.
         */
        Pattern pattern = Pattern.compile("^& .* \\d+ \\d+: ([^,]+)");
        Matcher matcher = pattern.matcher(response);
        if (matcher.find() == false) {
            return false; // We don't understand what ispell's said, so let's not assume anything.
        }
        List<String> suggestions = new ArrayList<String>();
        fillCollectionWithSuggestions(response, suggestions);
        for (String suggestion : suggestions) {
            if (suggestion.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }
    
    private String[] extractSuggestions(String response) {
        // Does this response actually have any suggestions?
        if ("&?".indexOf(response.charAt(0)) == -1) {
            return new String[0];
        }
        
        return response.replaceFirst("^[&\\?] .* \\d+ \\d+: ", "").split(", ");
    }
    
    private void fillCollectionWithSuggestions(String response, Collection<String> returnSuggestions) {
        String[] suggestions = extractSuggestions(response);
        for (String suggestion : suggestions) {
            returnSuggestions.add(suggestion);
        }
    }
    
    private void debug(String message) {
        if (DEBUGGING) {
            Log.warn("SpellingChecker: " + message);
        }
    }
}
