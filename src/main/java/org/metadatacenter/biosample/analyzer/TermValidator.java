package org.metadatacenter.biosample.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.io.File;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

// import java.io.IOException;

/**
 * @author Rafael Gonçalves <br>
 *         Center for Biomedical Informatics Research <br>
 *         Stanford University
 */
@Immutable
public final class TermValidator {
  @Nonnull
  private final BioPortalAgent bioPortalAgent;
  @Nonnull
  private final static Pattern p1 = Pattern.compile(" ");
  @Nonnull
  private final static Pattern p2 = Pattern.compile("%");
  @Nonnull
  private final static Pattern p3 = Pattern.compile("\\.");

  public TermValidator(@Nonnull BioPortalAgent bioPortalAgent) {
    this.bioPortalAgent = checkNotNull(bioPortalAgent);
  }

  public TermValidationReport validateTerm(@Nonnull String term, boolean exactMatch, @Nonnull String... ontologies) {
    String searchString = p1.matcher(term).replaceAll("+");
    searchString = p2.matcher(searchString).replaceAll("");
    searchString = p3.matcher(searchString).replaceAll("");

    Optional<JsonNode> searchResult = Optional.empty();
    if (!searchString.trim().isEmpty()) {
      if (ontologies.length > 0) {
        String onts = "";
        for (int i = 0; i < ontologies.length; i++) {
          onts += ontologies[i] + (i == ontologies.length - 1 ? "" : ",");
        }
        searchResult = bioPortalAgent.getResult(searchString, exactMatch, onts);
      } else {
        searchResult = bioPortalAgent.getResult(searchString, exactMatch);
      }
    }

    if (searchResult.isPresent() && searchResult.get().elements().hasNext()) {
      // look at the first result from BioPortal
      JsonNode node = searchResult.get().elements().next();

      String type = node.get("@type").textValue();
      boolean isOWLClass = isOwlClass(type);

      String ontologyType = node.get("ontologyType").textValue();
      boolean isOntology = isOntology(ontologyType);

      String value = node.get("@id").textValue();
      String label = node.get("prefLabel").textValue();

      String ontology = "";
      JsonNode ontologyNode = node.get("ontology");
      if (ontologyNode != null) {
        ontology = ontologyNode.textValue();
      }
      ArrayList<String> cuis = new ArrayList<String>();
      ArrayList<String> tuis = new ArrayList<String>();
      JsonNode cuiNode = node.get("cui");
      if (cuiNode == null) {
        cuiNode = node.get("UMLS_CUI");
      }
      JsonNode tuiNode = node.get("tui");
      if (tuiNode == null) {
        tuiNode = node.get("Semantic_Type");
      }
      if (cuiNode != null) {
        for (JsonNode objNode : cuiNode) {
          cuis.add(objNode.textValue());
        }
      }
      if (tuiNode != null) {
        for (JsonNode objNode : tuiNode) {
          tuis.add(objNode.textValue());
        }
      }
      return new TermValidationReport(value,label,isOntology,isOWLClass,true,ontology,cuis,tuis);
    } else {
      return new TermValidationReport("", "", false, false, false, "", null, null);
    }
  }

  public ArrayList<TermValidationReport> validateTermMulti(@Nonnull String term, boolean exactMatch, @Nonnull String... ontologies) {
    String searchString = p1.matcher(term).replaceAll("+");
    searchString = p2.matcher(searchString).replaceAll("");
    searchString = p3.matcher(searchString).replaceAll("");

    Optional<JsonNode> searchResult = Optional.empty();
    if (!searchString.trim().isEmpty()) {
      if (ontologies.length > 0) {
        String onts = "";
        for (int i = 0; i < ontologies.length; i++) {
          onts += ontologies[i] + (i == ontologies.length - 1 ? "" : ",");
        }
        searchResult = bioPortalAgent.getResult(searchString, exactMatch, onts);
      } else {
        searchResult = bioPortalAgent.getResult(searchString, exactMatch);
      }
    }

    ArrayList<TermValidationReport> results = new ArrayList<TermValidationReport>();
    if (!searchResult.isPresent() || !searchResult.get().elements().hasNext()) {
      results.add(new TermValidationReport("", "", false, false, false, "", null, null));
      return results;
    }
    
    // int numResults = 0;
    Iterator<JsonNode> iter = searchResult.get().elements();
    while (iter.hasNext()) {
      // if (numResults >=6) break;
      // numResults +=1;

      // System.out.println(numResults);

      // look at the first result from BioPortal
      JsonNode node = iter.next();
      // System.out.println(node.asText());

      String type = node.get("@type").textValue();
      boolean isOWLClass = isOwlClass(type);

      String ontologyType = node.get("ontologyType").textValue();
      boolean isOntology = isOntology(ontologyType);

      String value = node.get("@id").textValue();
      String label = node.get("prefLabel").textValue();

      String ontology = "";
      JsonNode ontologyNode = node.get("ontology");
      if (ontologyNode != null) {
        ontology = ontologyNode.textValue();
      }
      ArrayList<String> cuis = new ArrayList<String>();
      ArrayList<String> tuis = new ArrayList<String>();
      JsonNode cuiNode = node.get("cui");
      if (cuiNode == null) {
        cuiNode = node.get("UMLS_CUI");
      }
      JsonNode tuiNode = node.get("tui");
      if (tuiNode == null) {
        tuiNode = node.get("Semantic_Type");
      }
      if (cuiNode != null) {
        for (JsonNode objNode : cuiNode) {
          cuis.add(objNode.textValue());
        }
      }
      if (tuiNode != null) {
        for (JsonNode objNode : tuiNode) {
          tuis.add(objNode.textValue());
        }
      }
      results.add(new TermValidationReport(value,label,isOntology,isOWLClass,true,ontology,cuis,tuis));
    }
    return results;
  }

  private boolean isOntology(@Nonnull String ontologyType) {
    return ontologyType.equalsIgnoreCase("ontology");
  }

  private boolean isOwlClass(@Nonnull String type) {
    return type.equals("http://www.w3.org/2002/07/owl#Class");
  }

  private boolean exists(@Nonnull String str) {
    try {
      URL url = new URL(str);
      HttpURLConnection connection = (HttpURLConnection) url.openConnection();
      connection.setRequestMethod("HEAD"); // avoid downloading response body
      return (connection.getResponseCode() == HttpURLConnection.HTTP_OK);
    } catch (IOException e) {
      return false;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TermValidator)) {
      return false;
    }
    TermValidator that = (TermValidator) o;
    return Objects.equal(bioPortalAgent, that.bioPortalAgent);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(bioPortalAgent);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this).add("bioPortalAgent", bioPortalAgent).toString();
  }

  /* utils */
  public static int getStartIndex(File f) throws IOException {
    if (!f.exists()) {
      System.out.println("Could not find output file to resume, starting from index 0...\n");
      return 0;
    }
    BufferedReader br = new BufferedReader(new FileReader(f));
    String currLine;
    String lastLine = "";
    while ((currLine = br.readLine()) != null) {
      lastLine = currLine;
    }
    br.close();
    int startIdx = Integer.parseInt(lastLine.split("\t")[0]) + 1;
    System.out.println("Resuming " + f.getName() + " from index " + startIdx);
    return startIdx;
  }

  /* Main */
  public static void OutputResult(TermValidationReport report, FileWriter fw, String idx, String term) throws IOException {
    if (fw != null) {
      // return;
      fw.write(idx+"\t"+term+"\t"+report.getMatchValue()+"\t"+report.getMatchLabel()+"\t"+report.getCuis()+"\t"+report.getSemanticTypes()+"\n");
    } else {
      System.out.println(term + " " + report.toString());
    }
  }

  public static void SearchTerm(String term, boolean exactMatch, String bioPortalApiKey, String... ontology) {
    TermValidator validator = new TermValidator(new BioPortalAgent(bioPortalApiKey));
    TermValidationReport report;
    if (ontology == null) {
      report = validator.validateTerm(term, exactMatch);
    } else {
      report = validator.validateTerm(term, exactMatch, ontology);
    }
    System.out.println(report.toString());
  }

  public static void main(String[] args) throws IOException, InterruptedException, ParseException {
    Options options = new Options();
    options.addOption("t", true, "Search a single term");
    options.addOption("o", true, "Specific bioportal ontology to search, default all ontologies");
    options.addOption("if", true, "Path to tab-delimited file with index and term to search on each line");
    options.addOption("of", true, "Path to write results to, if not specific results will be printed to the console");
    options.addOption("em", true, "[true|false] Whether to search using bioportal's 'exact match'. Default true'");
    options.addOption("k",true, "Bioportal api key");
    options.addOption("restart","r",false, "Remove old outfile and start from index 0");
    options.addOption("index",true,"start from index");
    // options.addOption("allresults","ar",false, "Return results in all ontologies, default first result only");
    CommandLineParser parser = new DefaultParser();
    CommandLine cmd = parser.parse(options,args);

    boolean exactMatch = (cmd.hasOption("em")) ? Boolean.parseBoolean(cmd.getOptionValue("em")) : true;
    // boolean allResults = (cmd.hasOption("ar")) ? Boolean.parseBoolean(cmd.getOptionValue("ar")) : false;
    boolean allResults = true;
    String bioPortalApiKey = cmd.getOptionValue("k");
    String [] ontology = (cmd.hasOption("o")) ? cmd.getOptionValue("o").split(",") : null;
    // boolean allReults = (cmd.hasOption("ar")) ? true : false;

    if (cmd.hasOption("t")) {
      SearchTerm(cmd.getOptionValue("t"),exactMatch,bioPortalApiKey,ontology);
      return;
    }

    Path ifname = Paths.get(cmd.getOptionValue("if"));
    Path ofname = null;
    if (cmd.hasOption("of")) {
      ofname = Paths.get(cmd.getOptionValue("of"));
      if (cmd.hasOption("restart")) {
        ofname.toFile().renameTo(ofname.getParent().resolve("dest").toFile());
      }
    }
    int startIdx = 0;
    if (cmd.hasOption("index")) {
      startIdx = Integer.parseInt(cmd.getOptionValue("index"));
      System.out.println("Starting from index "+startIdx);
    } else if (ofname != null) {
      startIdx = getStartIndex(ofname.toFile());
    }

    ArrayList<String> index_list = new ArrayList<String>();
    ArrayList<String> keywords_list = new ArrayList<String>();

    BufferedReader br = new BufferedReader(new FileReader(ifname.toFile()));
    String line;
    while (((line = br.readLine()) != null) && line != ""){
      String cols[] = line.split("\t",0);
      if (cols.length < 3) continue;
      index_list.add(cols[0]);
      keywords_list.add(cols[2]);
    }
    br.close();
    
    TermValidator validator = new TermValidator(new BioPortalAgent(bioPortalApiKey));
    FileWriter fw = (ofname != null) ? new FileWriter(ofname.toFile(),true) : null;
    for (int i=0; i<keywords_list.size(); i++){
      String idx = index_list.get(i);      
      String term = keywords_list.get(i).strip();
      if (Integer.parseInt(idx)<startIdx) continue;
      if (i%1000 == 0) {
        System.out.println(idx+"/"+index_list.get(index_list.size()-1));
        if (fw != null) fw.flush();
      }

      TermValidationReport report;
      ArrayList<TermValidationReport> reports = new ArrayList<TermValidationReport>();
      int num_retries = 0;
      while (true) {
        try {
          if (allResults==true) {
            reports = validator.validateTermMulti(term, exactMatch);
            for (int j=0; j<reports.size(); j++) {
              OutputResult(reports.get(j),fw,idx,term);
            }
            break;
          }

          if (ontology != null) {
            report = validator.validateTerm(term, exactMatch, ontology);
          } else {
            report = validator.validateTerm(term,exactMatch);
          }
          OutputResult(report, fw, idx, term);
          break;

        } catch (Exception e) {
          if (num_retries >= 5) {
            System.out.println("Too many retries, skipping "+term);
            if (fw != null) fw.flush();
            break;
          }
          System.out.println(e);
          System.out.println("Caught system error trying to validate "+term+", retrying in 30 seconds with new agent...");
          num_retries++;
          // Thread.sleep(30000);
          validator = new TermValidator(new BioPortalAgent(bioPortalApiKey));
          continue;
        }
      }
    }
    if (fw != null) fw.close();
  }
}
