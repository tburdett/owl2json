package uk.ac.ebi.fgpt.owl2json;

import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerConfiguration;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.ReasonerProgressMonitor;
import org.semanticweb.owlapi.reasoner.SimpleConfiguration;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;

/**
 * Loads an ontology using the OWLAPI and a HermiT reasoner to classify the ontology.  This allows for richer typing
 * information on each class to be provided
 *
 * @author Tony Burdett
 * @date 03/06/13
 */
public class ReasonedOntologyLoader extends AbstractOntologyLoader {
    protected void loadOntology() throws OWLOntologyCreationException {
        getLog().debug("Loading ontology...");
        OWLOntology ontology = getManager().loadOntology(IRI.create(getOntologyURI()));
        IRI ontologyIRI = ontology.getOntologyID().getOntologyIRI();
        setOntologyIRI(ontologyIRI);
        getLog().debug("Successfully loaded ontology " + ontologyIRI);

        getLog().debug("Trying to create a reasoner over ontology '" + getOntologyURI() + "'");
        OWLReasonerFactory factory = new Reasoner.ReasonerFactory();
        ReasonerProgressMonitor progressMonitor = new LoggingReasonerProgressMonitor(getLog());
        OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor);
        OWLReasoner reasoner = factory.createReasoner(ontology, config);

        getLog().debug("Precomputing inferences...");
        reasoner.precomputeInferences();

        getLog().debug("Checking ontology consistency...");
        reasoner.isConsistent();

        getLog().debug("Checking for unsatisfiable classes...");
        if (reasoner.getUnsatisfiableClasses().getEntitiesMinusBottom().size() > 0) {
            throw new OWLOntologyCreationException(
                    "Once classified, unsatisfiable classes were detected in '" + ontologyIRI + "'");
        }
        else {
            getLog().debug("Reasoning complete! ");
        }

        OWLClass obsoleteClass = getFactory().getOWLClass(
                IRI.create("http://www.geneontology.org/formats/oboInOwl#ObsoleteClass"));

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
        for (OWLClass ontologyClass : allClasses) {
            IRI clsIri = ontologyClass.getIRI();
            getLog().trace("Processing " + clsIri + "...");

            // check if this is a subclass of obsolete class
            if (ontologyClass.equals(obsoleteClass) ||
                    reasoner.getSuperClasses(ontologyClass, false).getFlattened().contains(obsoleteClass)) {
                getLog().trace("Class " + ontologyClass + " is obsolete, skipping");
                continue;
            }

            // get label annotations
            getLog().trace("Collecting labels...");
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
                    getLog().trace("Label of '" + ontologyClass + ": " + label);
                    addClassLabel(clsIri, label);
                    labelledClassCount++;
                    labelCount++;
                }
            }

            // get types
            getLog().trace("Collecting types...");
            Set<String> ontologyTypeLabelSet = new HashSet<>();
            Set<OWLClass> parents = reasoner.getSuperClasses(ontologyClass, false).getFlattened();
            for (OWLClass parentClass : parents) {
                getLog().trace("Next parent of " + label + ": " + parentClass);
                Set<String> typeVals = getStringLiteralAnnotationValues(ontology, parentClass, rdfsLabel);
                ontologyTypeLabelSet.addAll(typeVals);
            }
            addClassTypes(clsIri, ontologyTypeLabelSet);

            // get all synonym annotations
            getLog().trace("Collecting synonyms...");
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
            getLog().trace("Collecting children...");
            Set<IRI> childIriSet = new HashSet<>();
            Set<OWLClass> children = reasoner.getSubClasses(ontologyClass, true).getFlattened();
            for (OWLClass childClass : children) {
                getLog().trace("Next child of " + label + ": " + childClass);
                childIriSet.add(childClass.getIRI());
            }
            addChildren(clsIri, childIriSet);
        }

        getLog().debug("Successfully loaded " + labelCount + " labels on " + labelledClassCount + " classes, and " +
                               synonymCount + " synonyms on " + synonymedClassCount + " classes, " +
                               "from " + ontologyIRI.toString() + "!");
    }

    private class LoggingReasonerProgressMonitor implements ReasonerProgressMonitor {
        private final Logger log;
        private int lastPercent = 0;

        public LoggingReasonerProgressMonitor(Logger log) {
            this.log = log;
        }

        protected Logger getLog() {
            return log;
        }

        @Override public void reasonerTaskStarted(String s) {
            getLog().debug(s);
        }

        @Override public void reasonerTaskStopped() {
            getLog().debug("100% done!");
            lastPercent = 0;
        }

        @Override public void reasonerTaskProgressChanged(int value, int max) {
            if (max > 0) {
                int percent = value * 100 / max;
                if (lastPercent != percent) {
                    if (percent % 25 == 0) {
                        getLog().debug("" + percent + "% done...");
                    }
                    lastPercent = percent;
                }
            }
        }

        @Override public void reasonerTaskBusy() {

        }
    }
}
