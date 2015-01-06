package uk.ac.ebi.fgpt.owl2json;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;

/**
 * Driver class for invoking OWL to JSON conversion.  Uses POSIX style arguments.
 *
 * @author Tony Burdett
 * @date 01/07/13
 */
public class OWL2JSONDriver {
    private static File _outputFile;

    private static File _ontologyFile;
    private static URI _ontologyURI;
    private static URI _synonymURI;

    private static int _maxDepth;
    private static int _minSize;

    private static boolean _useReasoning;
    private static boolean _useZooma;
    private static URI _zoomaDatasource;

    public static void main(String[] args) {
        try {
            int statusCode = parseArguments(args);
            if (statusCode == 0) {
                try {
                    OWL2JSONDriver driver = new OWL2JSONDriver();

                    OntologyLoader loader;
                    if (_ontologyFile != null) {
                        loader = driver.createOntologyLoader(_ontologyFile, _ontologyURI, _synonymURI, _useReasoning);
                    }
                    else {
                        loader = driver.createOntologyLoader(_ontologyURI, _synonymURI, _useReasoning);
                    }

                    OntologyHierarchyNodeCounter counter;
                    if (_zoomaDatasource != null) {
                        counter = driver.createOntologyHierarchyNodeCounter(_zoomaDatasource);
                    }
                    else {
                        counter = driver.createOntologyHierarchyNodeCounter(_useZooma);
                    }

                    String jsonString = driver.generateJSON(loader, counter, _maxDepth, _minSize);
                    driver.saveJSON(jsonString, _outputFile);
                }
                catch (Exception e) {
                    e.printStackTrace();
                    System.exit(1);
                }
            }
            else {
                System.exit(statusCode);
            }
        }
        catch (Exception e) {
            System.err.println("OWL2JSON did not complete successfully: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static int parseArguments(String[] args) throws IOException {
        CommandLineParser parser = new GnuParser();
        HelpFormatter help = new HelpFormatter();
        Options options = bindOptions();

        int parseArgs = 0;
        try {
            CommandLine cl = parser.parse(options, args, true);

            // check for mode help option
            if (cl.hasOption("")) {
                // print out mode help
                help.printHelp("owl2json", options, true);
                parseArgs += 1;
            }
            else {
                // check -f required option
                if (cl.hasOption("f")) {
                    // get file argument
                    _outputFile = new File(cl.getOptionValue("f"));
                    File d = _outputFile.getAbsoluteFile().getParentFile();
                    // create if the parent directory if it doesn't exist
                    if (!d.getAbsoluteFile().exists()) {
                        System.out.print("Creating output file directory '" + d.getAbsolutePath() + "'...");
                        if (d.mkdirs()) {
                            System.out.println("ok!");
                        }
                        else {
                            System.out.println("failed.");
                        }
                    }
                }

                // check -o required option
                if (cl.hasOption("o")) {
                    // get ontology uri argument
                    _ontologyURI = URI.create(cl.getOptionValue("o"));
                }

                // check -of option - not required, can be null
                if (cl.hasOption("of")) {
                    // get ontology file argument
                    _ontologyFile = new File(cl.getOptionValue("of"));
                    System.out.println(
                            "Getting ready to convert '" + _ontologyURI + "', loaded from '" + _ontologyFile + "'...");
                }
                else {
                    System.out.println("Getting ready to convert '" + _ontologyURI + "'...");
                }


                // check -s option - not required, defaults to EFO synonym uri
                if (cl.hasOption("y")) {
                    // get synonym uri argument
                    _synonymURI = URI.create(cl.getOptionValue("s"));
                    System.out.println("Synonyms will be identified using annotation property '" + _synonymURI + "'");
                }
                else {
                    _synonymURI = URI.create("http://www.ebi.ac.uk/efo/alternative_term");
                    System.out.println(
                            "Synonyms will be identified using the default annotation property '" + _synonymURI + "'");
                }

                // check maxDepth and minSize options - optional, both default to -1
                if (cl.hasOption("d")) {
                    _maxDepth = Integer.parseInt(cl.getOptionValue("d"));
                    System.out.println("Using maximum tree depth option = " + _maxDepth);
                }
                else {
                    _maxDepth = -1;
                }
                if (cl.hasOption("s")) {
                    _minSize = Integer.parseInt(cl.getOptionValue("s"));
                    System.out.println("Using minimum subtree size option = " + _minSize);
                }
                else {
                    _minSize = -1;
                }

                // check nr flag - optional, used to suppress reasoning, default is to use reasoning
                if (cl.hasOption("nr")) {
                    _useReasoning = false;
                    System.out.println("Using asserted ontology tree hierarchy only (no reasoning)");
                }
                else {
                    _useReasoning = true;
                    System.out.println("Using inferred ontology tree hierarchy");
                }

                // check useZooma flag - optional, defaults to false
                if (cl.hasOption("z")) {
                    _useZooma = true;
                    System.out.print("Using ZOOMA to get data counts");
                    if (cl.getOptionValue("z") != null) {
                        _zoomaDatasource = URI.create(cl.getOptionValue("z"));
                        System.out.print(": datasource = '" + _zoomaDatasource + "'");
                    }
                    System.out.println("");
                }
                else {
                    _useZooma = false;
                }
            }
        }
        catch (ParseException e) {
            System.err.println("Failed to read supplied arguments (" + e.getMessage() + ")");
            help.printHelp("owl2json", options, true);
            parseArgs += 1;
        }
        return parseArgs;
    }

    private static Options bindOptions() {
        Options options = new Options();

        // help
        Option helpOption = new Option("h", "help", false, "Print the help");
        options.addOption(helpOption);

        // add file options
        Option fileOption = new Option(
                "f",
                "file",
                true,
                "Output file - the file where the resulting JSON output should be written.");
        fileOption.setRequired(true);
        options.addOption(fileOption);

        // add ontology options
        Option ontologyURIOption = new Option(
                "o",
                "ontology",
                true,
                "Ontology URI - the URI of the ontology to convert.");
        ontologyURIOption.setRequired(true);
        options.addOption(ontologyURIOption);
        Option ontologyFileOption = new Option(
                "of",
                "ontologyFile",
                true,
                "Ontology File - the path to a local copy of the ontology.  If not supplied, the ontology will be loaded directly from it's URI.");
        ontologyFileOption.setRequired(false);
        options.addOption(ontologyFileOption);
        Option synonymOption = new Option(
                "y",
                "synonym",
                true,
                "Synonym URI - the URI of the annotation property that describes synonyms in the ontology. Defaults to 'http://www.ebi.ac.uk/efo/alternative_term'. Optional.");
        synonymOption.setRequired(false);
        options.addOption(synonymOption);

        // add sizing options
        Option maxDepthOption = new Option("d",
                                           "depth",
                                           true,
                                           "Max depth - the maximum depth of the ontology tree to render as JSON.  Optional.");
        maxDepthOption.setRequired(false);
        options.addOption(maxDepthOption);
        Option minSizeOption = new Option("s",
                                          "size",
                                          true,
                                          "Min size - the minimum size a node in the tree must have in order to be rendered as JSON.  Nodes of less than this size are aggregated.  Optional.");
        minSizeOption.setRequired(false);
        options.addOption(minSizeOption);

        // add additional config options
        Option noReasoningOption = new Option("nr",
                                              "noReasoning",
                                              false,
                                              "No reasoning flag - use to prevent the ontology being classified before converting the inferred hierarchy.");
        noReasoningOption.setRequired(false);
        options.addOption(noReasoningOption);
        @SuppressWarnings("AccessStaticViaInstance")
        Option zoomaOption = OptionBuilder
                .withArgName("URI")
                .withLongOpt("zooma")
                .hasOptionalArg()
                .withDescription(
                        "Use ZOOMA - use to acquire data counts from ZOOMA when evaluating the size of nodes.  You can optionally supply the URI of a datasource from ZOOMA to restrict to")
                .create("z");
        options.addOption(zoomaOption);
        return options;
    }

    public OntologyLoader createOntologyLoader(URI ontologyToLoad,
                                               URI synonymURI,
                                               boolean useReasoning)
            throws Exception {
        return createOntologyLoader(null, ontologyToLoad, synonymURI, useReasoning);
    }

    public OntologyHierarchyNodeCounter createOntologyHierarchyNodeCounter() {
        return createOntologyHierarchyNodeCounter(false);
    }

    public OntologyHierarchyNodeCounter createOntologyHierarchyNodeCounter(boolean useZooma) {
        if (useZooma) {
            return new ZoomaNodeCounter();
        }
        else {
            return new TreeSizeNodeCounter();
        }
    }

    public OntologyHierarchyNodeCounter createOntologyHierarchyNodeCounter(URI zoomaDatasource) {
        return new ZoomaNodeCounter(zoomaDatasource);
    }

    public OntologyLoader createOntologyLoader(File ontologyFile,
                                               URI ontologyToLoad,
                                               URI synonymURI,
                                               boolean useReasoning)
            throws Exception {
        AbstractOntologyLoader loader;
        if (useReasoning) {
            loader = new ReasonedOntologyLoader();
        }
        else {
            loader = new AssertedOntologyLoader();
        }
        loader.setOntologyFile(ontologyFile);
        loader.setOntologyURI(ontologyToLoad);
        loader.setSynonymURI(synonymURI);
        loader.init();
        return loader;
    }

    public String generateJSON(OntologyLoader loader, OntologyHierarchyNodeCounter counter, int maxDepth, int minSize) {
        OntologyHierarchyNode hierarchy =
                OntologyHierarchyBuilder.generateHierarchy(loader, counter, maxDepth, minSize);
        return OntologyHierarchyBuilder.convertOntologyHierarchyToJson(hierarchy);
    }

    public void saveJSON(String jsonString, File outputFile) throws IOException {
        BufferedWriter out = new BufferedWriter(new FileWriter(outputFile));
        out.write(jsonString);
        out.close();
    }
}
