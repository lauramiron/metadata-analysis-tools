package org.metadatacenter.biosample.analyzer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;
import java.util.*;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Rafael Gonçalves <br>
 * Center for Biomedical Informatics Research <br>
 * Stanford University
 */
@Immutable
public final class GenericValidator extends RecordValidator {
  @Nonnull private static final Logger logger = LoggerFactory.getLogger(GenericValidator.class.getName());
  @Nonnull private final TermValidator termValidator;

  public GenericValidator(@Nonnull TermValidator termValidator) {
    this.termValidator = checkNotNull(termValidator);
  }

  public RecordValidationReport validateBioSampleRecord(@Nonnull Record biosample) {
    List<AttributeGroupValidationReport> attributeGroupValidationReports = new ArrayList<>();
    Map<String,Attribute> map = biosample.getAttributes();
    // validate record against known attribute types
    for(AttributeType attrType : BioSampleAttributes.getAttributeTypes()) {
      List<AttributeValidationReport> reports = new ArrayList<>();
      for (AttributeSchema schema : BioSampleAttributes.getAttributesOfType(attrType)) {
        String attrName = schema.getName();
        Attribute attribute = map.get(attrName);
        AttributeValidationReport report;
        if(attribute != null) {
          report = validateAttribute(attribute, schema);
        } else {
          report = Utils.getMissingAttributeReport(attrName);
        }
        reports.add(report);
      }
      attributeGroupValidationReports.add(new AttributeGroupValidationReport(attrType.name().toLowerCase(), reports));
    }
    return new RecordValidationReport(biosample, attributeGroupValidationReports);
  }

  public AttributeValidationReport validateAttribute(Attribute attribute, AttributeSchema schema) {
    AttributeType type = schema.getType();
    AttributeValidationReport report;
    if(type.equals(AttributeType.BOOLEAN)) {
      report = validateBooleanAttribute(attribute);
    }
    else if(type.equals(AttributeType.INTEGER)) {
      report = validateIntegerAttribute(attribute);
    }
    else if(type.equals(AttributeType.VALUE_SET)) {
      report = validateValueSetAttribute(attribute, schema);
    }
    else if(type.equals(AttributeType.TERM)) {
      report = validateTermAttribute(attribute, schema);
    }
    else if(type.equals(AttributeType.ONTOLOGY_TERM)) {
      report = validateOntologyTermAttribute(attribute, true,
          schema.getValues().toArray(new String[schema.getValues().size()]));
    } else {
      report = Utils.getMissingAttributeReport(attribute.getName());
      logger.error("Missing functionality to handle attributes of type: " + type);
    }
    return report;
  }

  @Nonnull
  private AttributeValidationReport validateTermAttribute(@Nonnull Attribute attribute, @Nonnull AttributeSchema schema) {
    AttributeValidationReport report;
    if(schema.getValues().contains("GEOLOC")) {
      report = validateGeographicLocation(attribute);
    } else {
      report = validateOntologyTermAttribute(attribute, true);
    }
    return report;
  }

  @Nonnull
  private AttributeValidationReport validateValueSetAttribute(@Nonnull Attribute attribute, @Nonnull AttributeSchema schema) {
    String value = attribute.getValue();
    boolean isFilledIn = isFilledIn(value);
    boolean isValidFormat = false;
    String match = null;
    if(isFilledIn) {
      List<String> values = schema.getValues();
      for(String v : values) {
        if(value.equalsIgnoreCase(v)) {
          isValidFormat = true;
          match = v;
          break;
        }
      }
    }
    return new AttributeValidationReport(attribute, isFilledIn, isValidFormat, Optional.ofNullable(match));
  }

  @Nonnull
  private AttributeValidationReport validateOntologyTermAttribute(@Nonnull Attribute attribute, boolean exactMatch,
                                                                  @Nonnull String... ontologies) {
    String value = attribute.getValue();
    boolean isFilledIn = isFilledIn(value);
    boolean isValidFormat = false;
    String match = null;
    if(isFilledIn) {
      TermValidationReport report;
      if (ontologies.length > 0) {
        report = termValidator.validateTerm(normalize(value), exactMatch, ontologies);
      } else {
        report = termValidator.validateTerm(normalize(value), exactMatch);
      }
      isValidFormat = report.isResolvableOntologyClass();
      match = report.getMatchValue();
    }
    return new AttributeValidationReport(attribute, isFilledIn, isValidFormat, Optional.ofNullable(match));
  }

  @Nonnull
  private AttributeValidationReport validateBooleanAttribute(@Nonnull Attribute attribute) {
    String value = attribute.getValue().trim();
    boolean isFilledIn = isFilledIn(value);
    boolean isValidFormat = false;
    if(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
      isValidFormat = true;
    }
    return new AttributeValidationReport(attribute, isFilledIn, isValidFormat, Optional.empty());
  }

  @Nonnull
  private AttributeValidationReport validateIntegerAttribute(@Nonnull Attribute attribute) {
    String value = attribute.getValue().trim();
    boolean isFilledIn = isFilledIn(value);
    boolean isValidFormat = false;
    if(isFilledIn) {
      try {
        Integer.parseInt(value);
        isValidFormat = true;
      } catch (NumberFormatException e) {
        isValidFormat = false;
      }
    }
    return new AttributeValidationReport(attribute, isFilledIn, isValidFormat, Optional.empty());
  }

  /**
   * Check whether all the provided attributes are filled in properly
   */
  @Override
  public boolean isValid(@Nonnull RecordValidationReport report) {
    boolean isValid = true;
    for(AttributeGroupValidationReport group : report.getAttributeGroupValidationReports()) {
      for (AttributeValidationReport r : group.getValidationReports()) {
        if (!r.isValid()) {
          isValid = false;
          break;
        }
      }
    }
    return isValid;
  }

  @Nonnull
  private String normalize(@Nonnull String str) {
    String result = str.replaceAll("\\[", "");
    result = result.replaceAll("]", "");
    if (result.contains(":")) {
      result = result.substring(result.indexOf(":")+1, result.length());
    }
    return result;
  }
}
