package org.metadatacenter.biosample.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.MoreObjects;
import com.google.common.base.Objects;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Optional;
import java.util.regex.Pattern;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Scanner;
import java.util.ArrayList;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.FileReader;
import java.io.BufferedReader;
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

      return new TermValidationReport(value, label, isOntology, isOWLClass, true); // IRIs from BioPortal are resolvable
    } else {
      return new TermValidationReport("", "", false, false, false);
    }
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

  /* Main */
  public static void main(String[] args) throws IOException, InterruptedException {
    // String term = args[0];
    // boolean exactMatch = Boolean.parseBoolean(args[1]);
    String ifname = args[0];
    String ofname = args[1];
    boolean exactMatch = true;
    String bioPortalApiKey = "b0363744-e6d9-4cd5-a7a8-f3a118ee3049";

    ArrayList<String> index_list = new ArrayList<String>();
    ArrayList<String> files_list = new ArrayList<String>();
    ArrayList<String> keywords_list = new ArrayList<String>();

    BufferedReader br = new BufferedReader(new FileReader(ifname));
    String line;
    while ((line = br.readLine()) != null) {
      String cols[] = line.split("\t",0);
      if (cols.length<3) continue; // title line
      String index = cols[0];
      String filename = cols[1];
      String keyword = cols[2];
      index_list.add(index);
      files_list.add(filename);
      keywords_list.add(keyword);
    }
    br.close();
    
    TermValidator validator = new TermValidator(new BioPortalAgent(bioPortalApiKey));
    FileWriter fw = new FileWriter(ofname);
    for (int i=0; i<keywords_list.size(); i++){
      String idx = index_list.get(i);
      String fname = files_list.get(i);
      String term = keywords_list.get(i);

      if (i%1000 == 0) {
        System.out.println(idx+"/"+index_list.get(index_list.size()-1));
        fw.flush();
      }

      TermValidationReport report;
      int num_retries = 0;
      while (true) {
        try {
          report = validator.validateTerm(term, exactMatch, "MESH");
          break;
        } catch (Exception e) {
          if (num_retries >= 10) {
            System.out.println("Too many retries, exiting...");
            fw.flush();
            fw.close();
            return;
          }
          System.out.println("Caught system error trying to validate term, retrying in 30 seconds with new agent...");
          num_retries++;
          Thread.sleep(30000);
          validator = new TermValidator(new BioPortalAgent(bioPortalApiKey));
          continue;
        }
      }
      fw.write(idx+","+fname+","+term+","+report.getMatchValue()+","+report.getMatchLabel()+"\n");
    }
    fw.close();
  }
}
