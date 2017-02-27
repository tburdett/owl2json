package uk.ac.ebi.fgpt.owl2json;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 27/02/17
 */
public class CsvNodeCounter extends AbstractLookupNodeCounter {
    private final Path csvPath;

    public CsvNodeCounter(Path csvPath) {
        this.csvPath = csvPath;
    }

    @Override void lookupCounts() throws IOException {
        // load csv and parse
        // format should be URI, COUNT
        getLog().info("Loading node counts from " + csvPath.toString() + "...");
        BufferedReader reader = Files.newBufferedReader(csvPath, StandardCharsets.UTF_8);
        String line;
        int lineNumber = 0;
        while ((line = reader.readLine()) != null) {
            lineNumber++;
            if (line.startsWith("URI")) {
                // this is the header line
                continue;
            }

            try {
                String[] tokens = line.split(",");
                URI uri = URI.create(tokens[0]);
                int count = Integer.parseInt(tokens[1]);
                setCount(uri, count);
            }
            catch (NumberFormatException e) {
                getLog().error("Failed to read line " + lineNumber + "': COUNT column could not be parsed", e);
            }
            catch (IllegalArgumentException e) {
                getLog().error("Failed to read line " + lineNumber + "': URI column could not be parsed", e);
            }
        }
        reader.close();
    }
}
