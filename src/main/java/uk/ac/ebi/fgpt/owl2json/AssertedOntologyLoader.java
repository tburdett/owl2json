package uk.ac.ebi.fgpt.owl2json;

import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

/**
 * Loads an ontology using the OWLAPI, and considers only axioms that are asserted in the loaded ontology when
 * generating class labels and types
 *
 * @author Tony Burdett
 * @author James Malone
 * @date 15/02/12
 */
public class AssertedOntologyLoader extends AbstractOntologyLoader {
    protected void loadOntology() throws OWLOntologyCreationException {
        getLog().debug("Loading ontology...");
        OWLOntology ontology = getManager().loadOntology(IRI.create(getOntologyURI()));
        IRI ontologyIRI = ontology.getOntologyID().getOntologyIRI();
        setOntologyIRI(ontologyIRI);
        getLog().debug("Successfully loaded ontology " + ontologyIRI);
        Set<OWLClass> allClasses = ontology.getClassesInSignature();

        OWLAnnotationProperty rdfsLabel = getFactory().getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
        OWLAnnotationProperty synonym = null;
        if (getSynonymURI() != null) {
            synonym = getFactory().getOWLAnnotationProperty(IRI.create(getSynonymURI()));
        }

        int labelCount = 0;
        int labelledClassCount = 0;
        int synonymCount = 0;
        int synonymedClassCount = 0;
        getLog().debug("Loading labels and synonyms...");
        for (OWLClass ontologyClass : allClasses) {
            IRI clsIri = ontologyClass.getIRI();

            // get label annotations
            Set<String> labels = getStringLiteralAnnotationValues(ontology, ontologyClass, rdfsLabel);
            String label = null;
            if (labels.isEmpty()) {
                getLog().warn("OWLClass " + ontologyClass + " contains no label. " +
                                      "No labels for this class will be loaded.");
            }
            else {
                if (labels.size() > 1) {
                    getLog().warn("OWLClass " + ontologyClass + " contains more than one label " +
                                          "(including '" + labels.iterator().next() + "'). " +
                                          "No labels for this class will be loaded.");
                }
                else {
                    label = labels.iterator().next();
                    addClassLabel(clsIri, label);
                    labelledClassCount++;
                    labelCount++;
                }
            }

            // get types
            Set<String> ontologyTypeLabelSet = new HashSet<>();
            for (OWLClassExpression parentClassExpression : ontologyClass.getSuperClasses(ontology)) {
                if (!parentClassExpression.isAnonymous()) {
                    OWLClass parentClass = parentClassExpression.asOWLClass();
                    getLog().debug("Next parent of " + label + ": " + parentClass);
                    Set<String> typeVals = getStringLiteralAnnotationValues(ontology, parentClass, rdfsLabel);
                    ontologyTypeLabelSet.addAll(typeVals);
                }
                else {
                    getLog().trace("OWLClassExpression " + parentClassExpression + " is an anonymous class. " +
                                           "No synonyms for this class will be loaded.");
                }
            }
            addClassTypes(clsIri, ontologyTypeLabelSet);

            // get all synonym annotations
            if (synonym != null) {
                Set<String> synonymVals = getStringLiteralAnnotationValues(ontology, ontologyClass, synonym);
                if (synonymVals.isEmpty()) {
                    getLog().trace("OWLClass " + ontologyClass + " contains no synonyms. " +
                                           "No synonyms for this class will be loaded.");
                }
                else {
                    addSynonyms(clsIri, synonymVals);
                    synonymCount += synonymVals.size();
                    synonymedClassCount++;
                }
            }

            // get all children
            getLog().debug("Loading children...");
            Set<IRI> childIriSet = new HashSet<>();
            for (OWLClassExpression childClassExpression : ontologyClass.getSubClasses(ontology)) {
                OWLClass childClass = childClassExpression.asOWLClass();
                getLog().debug("Next child of " + label + ": " + childClass);
                childIriSet.add(childClass.getIRI());
            }
            addChildren(clsIri, childIriSet);
        }

        getLog().debug("Successfully loaded " + labelCount + " labels on " + labelledClassCount + " classes, and " +
                               synonymCount + " synonyms on " + synonymedClassCount + " classes, " +
                               "from " + ontologyIRI.toString() + "!");
    }
}
