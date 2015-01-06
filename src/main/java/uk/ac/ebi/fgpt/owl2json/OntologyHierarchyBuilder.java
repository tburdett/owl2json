package uk.ac.ebi.fgpt.owl2json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.semanticweb.owlapi.model.IRI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Javadocs go here!
 *
 * @author Tony Burdett
 * @date 01/07/13
 */
public class OntologyHierarchyBuilder {
    private static Logger log = LoggerFactory.getLogger(OntologyHierarchyBuilder.class);

    public static OntologyHierarchyNode generateHierarchy(OntologyLoader loader) {
        return generateHierarchy(loader, -1);
    }

    public static OntologyHierarchyNode generateOnePercentHierarachy(OntologyLoader loader) {
        int onePctSize = (int) (loader.getOntologyClassLabels().keySet().size() * 0.01);
        int minSize = (onePctSize > 500 ? 500 : onePctSize);
        return generateHierarchy(loader, -1, minSize);
    }

    public static OntologyHierarchyNode generateHierarchy(OntologyLoader loader, int maxDepth) {
        return generateHierarchy(loader, maxDepth, -1);
    }

    public static OntologyHierarchyNode generateHierarchy(OntologyLoader loader, int maxDepth, int minSize) {
        return generateHierarchy(loader, new TreeSizeNodeCounter(), maxDepth, minSize);
    }

    public static OntologyHierarchyNode generateHierarchy(OntologyLoader loader,
                                                          OntologyHierarchyNodeCounter counter,
                                                          int maxDepth,
                                                          int minSize) {
        // track nodes which might be roots - remove from this set as we walk the tree
        Set<IRI> possibleRoots = new HashSet<>();
        possibleRoots.addAll(loader.getOntologyClassChildren().keySet());

        // map converted nodes
        Map<IRI, OntologyHierarchyNode> hierarchyMap = new HashMap<>();

        for (IRI iri : loader.getOntologyClassChildren().keySet()) {
            buildNode(iri, loader, possibleRoots, hierarchyMap);
        }

        // once we've finished, get the root nodes
        Set<OntologyHierarchyNode> roots = new HashSet<>();
        for (IRI rootNodeIri : possibleRoots) {
            roots.add(hierarchyMap.get(rootNodeIri));
        }

        // get the root node
        OntologyHierarchyNode rootNode;

        // is there a single top level node?
        if (roots.size() == 1) {
            // return the single root node
            rootNode = roots.iterator().next();
        }
        else {
            // if there are several roots, create a single top level node for the ontology for convenience
            rootNode = new SimpleOntologyHierarchyNode(loader.getOntologyIRI().toURI(),
                                                       loader.getOntologyIRI().toString(),
                                                       roots);
        }

        // attribute counts to each node in the tree
        walkTreeAndCount(counter, rootNode);

        // prune the tree to maxDepth
        pruneHierarchy(rootNode, 0, maxDepth);

        groupHierarchy(rootNode, minSize);
        return rootNode;
    }

    public static String convertOntologyHierarchyToJson(OntologyHierarchyNode ontologyHierarchyNode) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_EMPTY);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_DEFAULT);
            return mapper.writeValueAsString(ontologyHierarchyNode);
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to serialize ontology hierarchy to JSON", e);
        }
    }

    private static OntologyHierarchyNode buildNode(IRI nodeIRI,
                                                   OntologyLoader loader,
                                                   Set<IRI> possibleRoots,
                                                   Map<IRI, OntologyHierarchyNode> hierarchyMap) {
        if (hierarchyMap.containsKey(nodeIRI)) {
            return hierarchyMap.get(nodeIRI);
        }

        Set<OntologyHierarchyNode> children = new HashSet<>();
        if (loader.getOntologyClassChildren().containsKey(nodeIRI)) {
            for (IRI childIRI : loader.getOntologyClassChildren().get(nodeIRI)) {
                if (childIRI.equals(nodeIRI)) {
                    continue;
                }

                log.trace("Next child of " + nodeIRI + ": " + childIRI);
                // childIRI is a child of nodeIRI and therefore by definition isn't a root
                if (possibleRoots.contains(childIRI)) {
                    possibleRoots.remove(childIRI);
                }

                // build the node for this child
                OntologyHierarchyNode child = buildNode(childIRI, loader, possibleRoots, hierarchyMap);
                if (child != null) {
                    // guard against owl:nothing, owl:thing or other unexpected cases
                    children.add(child);
                }
            }

            // build this node
            String nodeLabel = loader.getOntologyClassLabels().get(nodeIRI);
            log.trace("Generating hierarchy node for " + nodeIRI + " (" + nodeLabel + ")");
            OntologyHierarchyNode node =
                    new SimpleOntologyHierarchyNode(nodeIRI.toURI(), nodeLabel, children);

            // add it to our hierarchy map
            hierarchyMap.put(nodeIRI, node);

            // and return
            return node;
        }
        else {
            return null;
        }
    }

    private static void pruneHierarchy(OntologyHierarchyNode currentNode, int currentDepth, int maxDepth) {
        if (maxDepth == -1 || currentDepth < maxDepth) {
            for (OntologyHierarchyNode childNode : currentNode.getChildren()) {
                pruneHierarchy(childNode, currentDepth + 1, maxDepth);
            }
        }
        else {
            // this node is deeper than maxDepth, so remove children and set size of this node as total instead
            log.debug("Pruning tree under " + currentNode.getName() + ": this has a depth of " + currentDepth);
            currentNode.getChildren().clear();
            log.debug(currentNode.getName() + " now has " + currentNode.getChildren().size() + " children and size " +
                              currentNode.getSize());
        }
    }

    private static void groupHierarchy(OntologyHierarchyNode currentNode, int minSize) {
        Set<OntologyHierarchyNode> childrenToRemove = new HashSet<>();
        // recurse to children first, start at leaf nodes
        for (OntologyHierarchyNode childNode : currentNode.getChildren()) {
            groupHierarchy(childNode, minSize);

            // is the size of the subtree for this child less than minSize?
            if (childNode.getSize() < minSize) {
                childrenToRemove.add(childNode);
            }
        }

        if (!childrenToRemove.isEmpty()) {
            int removalSize = 0;
            for (OntologyHierarchyNode childToRemove : childrenToRemove) {
                // remove those that are too small and group into "other"
                removalSize += childToRemove.getSize();
                currentNode.getChildren().remove(childToRemove);
            }

            if (removalSize > 0) {
                // create a new "other ..." node and set the size to equal the total sizes of all removed nodes
                OntologyHierarchyNode otherNode = new SimpleOntologyHierarchyNode("Other " + currentNode.getName());
                otherNode.setSize(removalSize);
                currentNode.getChildren().add(otherNode);
            }
        }
    }

    private static void walkTreeAndCount(OntologyHierarchyNodeCounter counter, OntologyHierarchyNode node) {
        // recurse first, so we know we count from the leaf nodes up
        for (OntologyHierarchyNode childNode : node.getChildren()) {
            walkTreeAndCount(counter, childNode);
        }
        node.setSize(counter.count(node));
    }
}
