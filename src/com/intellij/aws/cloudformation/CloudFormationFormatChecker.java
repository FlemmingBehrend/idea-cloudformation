package com.intellij.aws.cloudformation;

import com.intellij.aws.cloudformation.metadata.CloudFormationResourceType;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.javascript.psi.JSExpression;
import com.intellij.lang.javascript.psi.JSLiteralExpression;
import com.intellij.lang.javascript.psi.JSObjectLiteralExpression;
import com.intellij.lang.javascript.psi.JSProperty;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class CloudFormationFormatChecker {
  private static Pattern AlphanumericStringPattern = Pattern.compile("[a-zA-Z0-9]+");

  private List<ProblemDescriptor> myProblems = new ArrayList<ProblemDescriptor>();
  private InspectionManager myInspectionManager;
  private final boolean myOnTheFly;

  public CloudFormationFormatChecker(InspectionManager manager, boolean isOnTheFly) {
    myInspectionManager = manager;
    myOnTheFly = isOnTheFly;
  }

  public List<ProblemDescriptor> getProblems() {
    return myProblems;
  }

  private void addProblem(@NotNull PsiElement element, @NotNull String description) {
    myProblems.add(myInspectionManager.createProblemDescriptor(
      element,
      description,
      myOnTheFly,
      LocalQuickFix.EMPTY_ARRAY,
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING));
  }

  private void addProblemOnNameElement(@NotNull JSProperty property, @NotNull String description) {
    addProblem(
      property.getFirstChild() != null ? property.getFirstChild() : property,
      description);
  }

  private void root(JSObjectLiteralExpression root) {
    for (JSProperty property : root.getProperties()) {
      final String name = property.getName();
      final JSExpression value = property.getValue();

      if (name == null || value == null) {
        continue;
      }

      if (CloudFormationSections.FormatVersion.equals(name)) {
        formatVersion(value);
      } else if (CloudFormationSections.Description.equals(name)) {
        description(value);
      } else if (CloudFormationSections.Parameters.equals(name)) {
        // TODO
      } else if (CloudFormationSections.Resources.equals(name)) {
        resources(value);
      } else if (CloudFormationSections.Conditions.equals(name)) {
        // TODO
      } else if (CloudFormationSections.Outputs.equals(name)) {
        outputs(value);
      } else if (CloudFormationSections.Mappings.equals(name)) {
        // TODO
      } else {
        addProblemOnNameElement(
          property,
          CloudFormationBundle.getString("format.unknown.section", property.getName()));
      }
    }

    if (root.findProperty(CloudFormationSections.Resources) == null) {
      addProblem(root, CloudFormationBundle.getString("format.resources.section.required"));
    }
  }

  private void outputs(JSExpression outputsExpression) {
    final JSObjectLiteralExpression obj = checkAndGetObject(outputsExpression);
    if (obj == null) {
      return;
    }

    for (JSProperty property : obj.getProperties()) {
      final String name = property.getName();
      final JSExpression value = property.getValue();
      if (name == null || value == null) {
        continue;
      }

      checkKeyName(property);
      stringValue(value);
    }

    if (obj.getProperties().length == 0) {
      addProblemOnNameElement(
        (JSProperty)obj.getParent(),
        CloudFormationBundle.getString("format.no.outputs.declared"));
    }

    if (obj.getProperties().length > CloudFormationConstants.MaxOutputs) {
      addProblemOnNameElement(
        (JSProperty)obj.getParent(),
        CloudFormationBundle.getString("format.max.outputs.exceeded", CloudFormationConstants.MaxOutputs));
    }
  }

  private void stringValue(JSExpression expression) {
    final JSLiteralExpression literalExpression = ObjectUtils.tryCast(expression, JSLiteralExpression.class);
    if (literalExpression != null && literalExpression.isQuotedLiteral()) {
      return;
    }
  }

  private void checkKeyName(JSProperty property) {
    if (property == null || property.getName() == null) {
      return;
    }

    if (!AlphanumericStringPattern.matcher(property.getName()).matches()) {
      addProblemOnNameElement(
        property,
        CloudFormationBundle.getString("format.invalid.key.name"));
    }
  }

  private void description(JSExpression value) {
    checkAndGetQuotedStringText(value);
  }

  private void resources(JSExpression value) {
    final JSObjectLiteralExpression obj = checkAndGetObject(value);
    if (obj == null) {
      return;
    }

    for (JSProperty property : obj.getProperties()) {
      final String resourceName = property.getName();
      final JSExpression resourceObj = property.getValue();
      if (resourceName == null || resourceObj == null) {
        continue;
      }

      checkKeyName(property);
      resource(property);
    }
  }

  private void resource(JSProperty resourceProperty) {
    final JSObjectLiteralExpression obj = checkAndGetObject(resourceProperty.getValue());
    if (obj == null) {
      return;
    }

    final JSProperty typeProperty = obj.findProperty(CloudFormationConstants.TypePropertyName);
    if (typeProperty == null) {
      addProblemOnNameElement(resourceProperty, CloudFormationBundle.getString("format.type.property.required"));
      return;
    }

    resourceType(typeProperty);

    final JSProperty propertiesProperty = obj.findProperty(CloudFormationConstants.PropertiesPropertyName);
    if (propertiesProperty != null) {
      resourceProperties(propertiesProperty, typeProperty);
    }

    // TODO Handle case with required properties and no Properties section
  }

  private void resourceProperties(JSProperty propertiesProperty, JSProperty typeProperty) {
    final JSObjectLiteralExpression properties = ObjectUtils.tryCast(propertiesProperty.getValue(), JSObjectLiteralExpression.class);
    if (properties == null) {
      addProblemOnNameElement(propertiesProperty, CloudFormationBundle.getString("format.properties.property.should.properties.list"));
      return;
    }

    final String resourceTypeName = checkAndGetUnquotedStringText(typeProperty.getValue());
    if (resourceTypeName == null) {
      return;
    }

    final CloudFormationResourceType resourceType = CloudFormationMetadataProvider.METADATA.findResourceType(resourceTypeName);
    if (resourceType == null) {
      return;
    }

    for (JSProperty property : properties.getProperties()) {
      final String propertyName = property.getName();
      if (propertyName == null) {
        continue;
      }

      if (resourceType.findProperty(propertyName) == null) {
        // TODO Enable when ready
        // addProblemOnNameElement(property, CloudFormationBundle.getString("format.unknown.resource.type.property", propertyName));
      }
    }

    // TODO Required properties
  }

  private void resourceType(JSProperty typeProperty) {
    final String value = checkAndGetQuotedStringText(typeProperty.getValue());
    if (value == null) {
      return;
    }

    final String unquotedValue = StringUtil.stripQuotesAroundValue(value);

    for (CloudFormationResourceType resourceType : CloudFormationMetadataProvider.METADATA.resourceTypes) {
      if (unquotedValue.equals(resourceType.name)) {
        return;
      }
    }

    addProblem(typeProperty, CloudFormationBundle.getString("format.unknown.type", value));
  }

  private JSObjectLiteralExpression checkAndGetObject(JSExpression expression) {
    final JSObjectLiteralExpression obj = ObjectUtils.tryCast(expression, JSObjectLiteralExpression.class);
    if (obj == null) {
      addProblem(
        expression,
        CloudFormationBundle.getString("format.expected.json.object"));

      return null;
    }

    return obj;
  }


  private void formatVersion(JSExpression value) {
    final String text = checkAndGetQuotedStringText(value);
    if (text == null) {
      return;
    }

    final String version = StringUtil.stripQuotesAroundValue(StringUtil.notNullize(text));
    if (!CloudFormationConstants.SupportedTemplateFormatVersions.contains(version)) {
      final String supportedVersions = StringUtil.join(CloudFormationConstants.SupportedTemplateFormatVersions, ", ");
      myProblems.add(
        myInspectionManager.createProblemDescriptor(
          value,
          CloudFormationBundle.getString("format.unknownVersion", supportedVersions),
          myOnTheFly,
          LocalQuickFix.EMPTY_ARRAY,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      );
    }
  }

  @Nullable
  private String checkAndGetQuotedStringText(JSExpression expression) {
    final JSLiteralExpression literal = ObjectUtils.tryCast(expression, JSLiteralExpression.class);
    if (literal == null || !literal.isQuotedLiteral()) {
      myProblems.add(
        myInspectionManager.createProblemDescriptor(
          expression,
          CloudFormationBundle.getString("format.expected.quoted.string"),
          myOnTheFly,
          LocalQuickFix.EMPTY_ARRAY,
          ProblemHighlightType.GENERIC_ERROR_OR_WARNING)
      );

      return null;
    }

    return literal.getText();
  }

  @Nullable
  private String checkAndGetUnquotedStringText(JSExpression expression) {
    final String quoted = checkAndGetQuotedStringText(expression);
    if (quoted == null) {
      return null;
    }

    return StringUtil.stripQuotesAroundValue(quoted);
  }

  public void file(PsiFile psiFile) {
    assert CloudFormationPsiUtils.isCloudFormationFile(psiFile) : psiFile.getName() + " is not a cfn file";

    final JSObjectLiteralExpression root = CloudFormationPsiUtils.getRootExpression(psiFile);
    if (root == null) {
      return;
    }

    root(root);
  }
}
