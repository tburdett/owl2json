package uk.ac.ebi.fgpt.owl2json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;

/**
 * An {@link uk.ac.ebi.fgpt.owl2json.OntologyHierarchyNodeCounter} that performs a ZOOMA lookup to obtain the counts of
 * all data annotations to ontology terms from a given source, and overlays this data on the ontology hierarchy as count
 * information for each node
 *
 * @author Tony Burdett
 * @date 18/08/14
 */
public class ZoomaNodeCounter extends AbstractLookupNodeCounter {
    private static final URI defaultDatasource = URI.create("http://www.genome.gov/gwastudies");

    private static final String queryPrefix =
            "http://www.ebi.ac.uk/fgpt/zooma/v2/api/query?query=PREFIX%20rdf%3A%20%3Chttp%3A%2F%2Fwww.w3.org%2F1999%2F02%2F22-rdf-syntax-ns%23%3E%0D%0APREFIX%20rdfs%3A%20%3Chttp%3A%2F%2Fwww.w3.org%2F2000%2F01%2Frdf-schema%23%3E%0D%0APREFIX%20owl%3A%20%3Chttp%3A%2F%2Fwww.w3.org%2F2002%2F07%2Fowl%23%3E%0D%0APREFIX%20dc%3A%20%3Chttp%3A%2F%2Fpurl.org%2Fdc%2Felements%2F1.1%2F%3E%0D%0APREFIX%20obo%3A%20%3Chttp%3A%2F%2Fpurl.obolibrary.org%2Fobo%2F%3E%0D%0APREFIX%20efo%3A%20%3Chttp%3A%2F%2Fwww.ebi.ac.uk%2Fefo%2F%3E%0D%0APREFIX%20zoomaresource%3A%20%3Chttp%3A%2F%2Frdf.ebi.ac.uk%2Fresource%2Fzooma%2F%3E%0D%0APREFIX%20zoomaterms%3A%20%3Chttp%3A%2F%2Frdf.ebi.ac.uk%2Fterms%2Fzooma%2F%3E%0D%0APREFIX%20oac%3A%20%3Chttp%3A%2F%2Fwww.openannotation.org%2Fns%2F%3E%0D%0A%0D%0ASELECT%20%3Fsemantictag%20(count(DISTINCT%20%3Fannotationid)%20as%20%3Fdatapoints)%20WHERE%20%7B%0D%0A%20%20%3Fannotationid%20rdf%3Atype%20oac%3ADataAnnotation%20%3B%0D%0A%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20%20oac%3AhasBody%20%3Fsemantictag%20.%20%0D%0A%20%20%3Fsemantictag%20rdf%3Atype%20oac%3ASemanticTag%20.%20%0D%0A%20%20%3Fannotationid%20dc%3Asource%20%3Fsource%20.%0D%0A%20%20FILTER%20(%3Fsource%20%3D%20%3C";
    private static final String querySuffix =
            "%3E)%20.%0D%0A%7D%0D%0AGROUP%20BY%20%3Fsemantictag%0D%0AORDER%20BY%20DESC(%3Fdatapoints)%0D%0A&format=JSON&inference=false";

    private final URL zoomaQueryUrl;

    public ZoomaNodeCounter() {
        this(defaultDatasource);
    }

    public ZoomaNodeCounter(URI zoomaDatasource) {
        // setup params
        try {
            getLog().debug("Utilizing ZOOMA datasource '" + zoomaDatasource + "'");
            String escapedDatasource = URLEncoder.encode(zoomaDatasource.toString(), "UTF-8");
            zoomaQueryUrl = URI.create(queryPrefix + escapedDatasource + querySuffix).toURL();
        }
        catch (UnsupportedEncodingException e) {
            throw new IllegalArgumentException(
                    "Cannot escape '" + defaultDatasource.toString() + ": " + e.getMessage(), e);
        }
        catch (MalformedURLException e) {
            throw new IllegalArgumentException(
                    "Cannot convert query into a URL - check your datasource? " +
                            "[" + zoomaDatasource.toString() + "]: " + e.getMessage(), e);
        }
    }

    void lookupCounts() throws IOException {
        // despatch query and parse response using jackson
        getLog().debug("Despatching ZOOMA query: " + zoomaQueryUrl);
        ObjectMapper mapper = new ObjectMapper();
        JsonNode dataNode = mapper.readTree(zoomaQueryUrl);
        getLog().trace("ZOOMA response: " + dataNode.toString());

        // results.binding[] -> each uri/count mapping
        JsonNode bindingsNode = dataNode.get("results").get("bindings");
        int datapointsCount = 0;
        int urisCount = 0;
        for (JsonNode bindingNode : bindingsNode) {
            URI nodeURI = URI.create(bindingNode.get("semantictag").get("value").asText());
            Integer count = bindingNode.get("datapoints").get("value").asInt();
            setCount(nodeURI, count);
            getLog().trace("Got next result: " + nodeURI.toString() + " -> " + count);
            datapointsCount += count;
            if (count > 0) {
                urisCount++;
            }
        }
        getLog().debug("Fetched " + datapointsCount + " datapoints for " + urisCount + " terms " +
                               "from ZOOMA");
    }
}
