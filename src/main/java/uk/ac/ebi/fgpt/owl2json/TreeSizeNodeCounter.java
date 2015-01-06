package uk.ac.ebi.fgpt.owl2json;

/**
 * An {@link uk.ac.ebi.fgpt.owl2json.OntologyHierarchyNodeCounter} that considers the size of the tree under a given
 * node to attribute size.  If the passed term is a leaf node, the size will be 1.  In all other cases, the size of a
 * node will be the sum total of each of it's direct child nodes.  For example, if a term has 3 children, each of which
 * are leaf nodes, the size of that term will be 3.  If THAT term itself has a single parent and that parent had no
 * other children, it's size would also be 3.
 *
 * @author Tony Burdett
 * @date 18/08/14
 */
public class TreeSizeNodeCounter implements OntologyHierarchyNodeCounter {
    @Override public int count(OntologyHierarchyNode node) {
        if (node.getChildren().isEmpty()) {
            return 1;
        }
        else {
            int totalChildSize = 0;
            for (OntologyHierarchyNode childNode : node.getChildren()) {
                totalChildSize += childNode.getSize();
            }
            return totalChildSize;
        }
    }
}
