package uk.ac.ebi.fgpt.owl2json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * An abstract node counter that provides support for looking up counts from an external resource (via the {@link
 * #lookupCounts} initializer method). This class will lazy initialize the counts on first request, and cache in memory.
 * Implementations should pass required resources to their constructor and implement the lookupCounts() method
 *
 * @author Tony Burdett
 * @date 27/02/17
 */
public abstract class AbstractLookupNodeCounter implements OntologyHierarchyNodeCounter {
    private final Map<URI, Integer> counts;
    private boolean init = false;

    private final Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    public AbstractLookupNodeCounter() {
        counts = new HashMap<>();
    }

    private void init() {
        if (init) {
            getLog().warn("Ontology hierarchy counts have been loaded and cached, they will not be reloaded");
        }
        else {
            try {
                lookupCounts();
                init = true;
                getLog().info("Successfully acquired counts for " + counts.keySet().size() + " URIs");
            }
            catch (IOException e) {
                throw new RuntimeException("Unable to create a NodeCounter - communication with counts resource failed", e);
            }
        }
    }

    protected void setCount(URI uri, int count) {
        counts.put(uri, count);
    }

    @Override public int count(OntologyHierarchyNode node) {
        if (!init) {
            init();
        }

        int size;
        if (counts.containsKey(node.getURI())) {
            size = counts.get(node.getURI());
        }
        else {
            size = 0;
        }

        // total this and all child terms
        int totalChildSize = 0;
        for (OntologyHierarchyNode childNode : node.getChildren()) {
            totalChildSize += childNode.getSize();
        }
        return size + totalChildSize;
    }

    abstract void lookupCounts() throws IOException;
}
