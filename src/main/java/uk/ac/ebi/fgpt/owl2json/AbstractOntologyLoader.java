package uk.ac.ebi.fgpt.owl2json;

import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.util.SimpleIRIMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * An abstract implementation of an ontology loader.  Implementations should extend this class with the {@link
 * #loadOntology()}.  This class is provided as a general caching mechanism to allow an ontology to be loaded and
 * processed once before being converted into ZOOMA annotations
 *
 * @author Tony Burdett
 * @date 03/06/13
 */
public abstract class AbstractOntologyLoader implements OntologyLoader {
    private URI ontologyURI;
    private File ontologyFile;

    private URI synonymURI;

    private OWLOntologyManager manager;
    private IRI ontologyIRI;
    private OWLDataFactory factory;

    private Map<IRI, String> ontologyLabels;
    private Map<IRI, Set<String>> ontologyTypeLabels;
    private Map<IRI, Set<String>> ontologySynonyms;
    private Map<IRI, Set<IRI>> ontologyChildren;

    private Logger log = LoggerFactory.getLogger(getClass());

    protected Logger getLog() {
        return log;
    }

    /**
     * Returns the URI of the ontology to load
     *
     * @return the URI of the ontology to load
     */
    public URI getOntologyURI() {
        return ontologyURI;
    }

    /**
     * Sets the URI of the ontology to load.  If accompanied with an <code>ontologyFile</code> property, this ontology
     * will be loaded from the location specified by the resource
     *
     * @param ontologyURI the URI of the target ontology to load
     */
    public void setOntologyURI(URI ontologyURI) {
        this.ontologyURI = ontologyURI;
    }

    /**
     * Returns the location from which the ontology (specified by the <code>ontologyURI</code> property) will be loaded
     * from
     *
     * @return a spring Resource representing this ontology
     */
    public File getOntologyFile() {
        return ontologyFile;
    }


    /**
     * Sets the location from which to load the ontology, if required. Setting this property creates a mapper that
     * prompts the OWL API to load the ontology from the supplied location, instead of attempting to resolve to the URL
     * corresponding to the ontology IRI. This property is optional.
     *
     * @param ontologyFile the resource at which EFO can be found, using spring configuration syntax (URLs,
     *                     classpath:...)
     */
    public void setOntologyFile(File ontologyFile) {
        this.ontologyFile = ontologyFile;
    }

    /**
     * Gets the URI used to denote synonym annotations in this ontology.  As there is no convention for this (i.e. no
     * rdfs:synonym), ontologies tend to define their own.
     *
     * @return the synonym annotation URI
     */
    public URI getSynonymURI() {
        return synonymURI;
    }

    /**
     * Sets the URI used to denote synonym annotations in this ontology. The specific property in use in the given
     * ontology should be specified here.
     *
     * @param synonymURI the URI representing synonym annotations
     */
    public void setSynonymURI(URI synonymURI) {
        this.synonymURI = synonymURI;
    }

    public OWLOntologyManager getManager() {
        return manager;
    }

    public OWLDataFactory getFactory() {
        return factory;
    }

    @Override public IRI getOntologyIRI() {
        if (ontologyIRI != null) {
            return ontologyIRI;
        }
        else {
            throw new IllegalStateException(getClass().getSimpleName() + " has not been initialized");
        }
    }

    @Override public Map<IRI, String> getOntologyClassLabels() {
        if (ontologyLabels != null) {
            return ontologyLabels;
        }
        else {
            throw new IllegalStateException(getClass().getSimpleName() + " has not been initialized");
        }
    }

    @Override public Map<IRI, Set<String>> getOntologyClassTypeLabels() {
        if (ontologyTypeLabels != null) {
            return ontologyTypeLabels;
        }
        else {
            throw new IllegalStateException(getClass().getSimpleName() + " has not been initialized");
        }
    }

    @Override public Map<IRI, Set<String>> getOntologyClassSynonyms() {
        if (ontologySynonyms != null) {
            return ontologySynonyms;
        }
        else {
            throw new IllegalStateException(getClass().getSimpleName() + " has not been initialized");
        }
    }

    @Override public Map<IRI, Set<IRI>> getOntologyClassChildren() {
        if (ontologyChildren != null) {
            return ontologyChildren;
        }
        else {
            throw new IllegalStateException(getClass().getSimpleName() + " has not been initialized");
        }
    }

    public void init() throws Exception {
        // init owl fields
        this.manager = OWLManager.createOWLOntologyManager();
        if (getOntologyFile() != null) {
            getLog().info(
                    "Mapping ontology IRI from " + getOntologyURI() + " to " + getOntologyFile().getAbsolutePath());
            this.manager.addIRIMapper(new SimpleIRIMapper(IRI.create(getOntologyURI()),
                                                          IRI.create(getOntologyFile())));
        }
        this.factory = manager.getOWLDataFactory();

        // init cache fields
        this.ontologyLabels = new HashMap<>();
        this.ontologyTypeLabels = new HashMap<>();
        this.ontologySynonyms = new HashMap<>();
        this.ontologyChildren = new HashMap<>();

        // load the ontology
        loadOntology();
    }

    protected Set<String> getStringLiteralAnnotationValues(OWLOntology ontology,
                                                           OWLClass ontologyClass,
                                                           OWLAnnotationProperty annotationProperty) {
        Set<String> vals = new HashSet<>();
        for (OWLAnnotation annotation : ontologyClass.getAnnotations(ontology, annotationProperty)) {
            if (annotation.getValue() instanceof OWLLiteral) {
                OWLLiteral val = (OWLLiteral) annotation.getValue();
                vals.add(val.getLiteral());
            }
        }
        return vals;
    }

    protected void setOntologyIRI(IRI ontologyIRI) {
        this.ontologyIRI = ontologyIRI;
    }

    protected void addClassLabel(IRI clsIri, String label) {
        this.ontologyLabels.put(clsIri, label);
    }

    protected void addClassTypes(IRI clsIri, Set<String> classTypeLabels) {
        this.ontologyTypeLabels.put(clsIri, classTypeLabels);
    }

    protected void addSynonyms(IRI clsIri, Set<String> synonyms) {
        this.ontologySynonyms.put(clsIri, synonyms);
    }

    protected void addChildren(IRI clsIri, Set<IRI> children) {
        this.ontologyChildren.put(clsIri, children);
    }

    /**
     * Extracts and loads into memory all the class labels and corresponding IRIs.  This class makes the assumption that
     * one primary label per class exists. If any classes contain multiple rdfs:labels, these classes are ignored.
     * <p/>
     * Once loaded, this method must set the IRI of the ontology, and should add class labels, class types (however you
     * chose to implement the concept of a "type") and synonyms, where they exist.
     */
    protected abstract void loadOntology() throws OWLOntologyCreationException;
}
