/*
 * SonarQube Java
 * Copyright (C) 2012-2025 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the Sonar Source-Available License Version 1, as published by SonarSource SA.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the Sonar Source-Available License for more details.
 *
 * You should have received a copy of the Sonar Source-Available License
 * along with this program; if not, see https://sonarsource.com/license/ssal/
 */
package org.sonar.java.filters;

import java.util.List;
import java.util.Set;
import org.sonar.java.checks.AtLeastOneConstructorCheck;
import org.sonar.java.checks.MethodOnlyCallsSuperCheck;
import org.sonar.java.checks.OptionalAsParameterCheck;
import org.sonar.java.checks.ServletInstanceFieldCheck;
import org.sonar.java.checks.TooManyParametersCheck;
import org.sonar.java.checks.helpers.ExpressionsHelper;
import org.sonar.java.checks.helpers.SpringUtils;
import org.sonar.java.checks.naming.BadMethodNameCheck;
import org.sonar.plugins.java.api.JavaCheck;
import org.sonar.plugins.java.api.semantic.Symbol;
import org.sonar.plugins.java.api.semantic.SymbolMetadata;
import org.sonar.plugins.java.api.tree.AnnotationTree;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.ExpressionTree;
import org.sonar.plugins.java.api.tree.MethodTree;
import org.sonar.plugins.java.api.tree.ModifiersTree;
import org.sonar.plugins.java.api.tree.Tree;
import org.sonar.plugins.java.api.tree.VariableTree;

public class SpringFilter extends BaseTreeVisitorIssueFilter {

  private static final List<String> S107_METHOD_ANNOTATION_EXCEPTIONS = List.of(
    "org.springframework.web.bind.annotation.RequestMapping",
    "org.springframework.web.bind.annotation.GetMapping",
    "org.springframework.web.bind.annotation.PostMapping",
    "org.springframework.web.bind.annotation.PutMapping",
    "org.springframework.web.bind.annotation.DeleteMapping",
    "org.springframework.web.bind.annotation.PatchMapping",
    SpringUtils.BEAN_ANNOTATION,
    SpringUtils.AUTOWIRED_ANNOTATION);

  private static final List<String> S107_CLASS_ANNOTATION_EXCEPTIONS = List.of(
    SpringUtils.COMPONENT_ANNOTATION,
    SpringUtils.CONFIGURATION_ANNOTATION,
    SpringUtils.SERVICE_ANNOTATION,
    SpringUtils.REPOSITORY_ANNOTATION);

  @Override
  public Set<Class<? extends JavaCheck>> filteredRules() {
    return Set.of(
      /* S100_ */ BadMethodNameCheck.class,
      /* S107_ */ TooManyParametersCheck.class,
      /* S1185 */ MethodOnlyCallsSuperCheck.class,
      /* S1258 */ AtLeastOneConstructorCheck.class,
      /* S2226 */ ServletInstanceFieldCheck.class,
      /* S3553 */ OptionalAsParameterCheck.class);
  }

  @Override
  public void visitClass(ClassTree tree) {
    excludeLinesIfTrue(isTransactional(tree), tree, MethodOnlyCallsSuperCheck.class);
    excludeLinesIfTrue(hasAutowiredField(tree), tree.simpleName(), AtLeastOneConstructorCheck.class);
    super.visitClass(tree);
  }

  @Override
  public void visitVariable(VariableTree tree) {
    excludeLinesIfTrue(isAutowired(tree), tree, ServletInstanceFieldCheck.class);
    super.visitVariable(tree);
  }

  @Override
  public void visitMethod(MethodTree tree) {
    Symbol.MethodSymbol symbol = tree.symbol();
    Tree reportTree = tree.simpleName();
    if (tree.is(Tree.Kind.CONSTRUCTOR)) {
      SymbolMetadata ownerMetadata = symbol.owner().metadata();
      excludeLinesIfTrue(S107_CLASS_ANNOTATION_EXCEPTIONS.stream().anyMatch(ownerMetadata::isAnnotatedWith), reportTree, TooManyParametersCheck.class);
    } else {
      SymbolMetadata methodMetadata = symbol.metadata();
      excludeLinesIfTrue(S107_METHOD_ANNOTATION_EXCEPTIONS.stream().anyMatch(methodMetadata::isAnnotatedWith), reportTree, TooManyParametersCheck.class);
      excludeLinesIfTrue(isRepositoryPropertyExpression(symbol), reportTree, BadMethodNameCheck.class);
      excludeLinesIfTrue(methodMetadata.isAnnotatedWith(SpringUtils.AUTOWIRED_ANNOTATION), reportTree, OptionalAsParameterCheck.class);
    }
    super.visitMethod(tree);
  }

  /**
   * This methods requires semantic information to take a decision and filter out issues.
   * The knowledge of being in a SpringData context can not be inferred from only tokens; it needs to understand the interfaces that the owning class implements.
   *
   * As a consequence, in case of missing semantic (degraded environment), S100 (BadMethodNameCheck) will still raise issues on SpringData methods.
   * These issues will be considered FPs by the user, but can not be eliminated without introducing way too many FN for the rule.
   *
   * @param symbol the symbol of the method under analysis
   * @return true if the method is understood as being a repository property expression, containing an underscore character in the middle of its name. Returns false otherwise.
   */
  private static boolean isRepositoryPropertyExpression(Symbol.MethodSymbol symbol) {
    String name = symbol.name();
    int underscorePosition = name.indexOf('_');
    boolean isSeparatorInMethodName = underscorePosition > 0 && underscorePosition < name.length() - 1;
    return isSeparatorInMethodName && symbol.owner().type().isSubtypeOf(SpringUtils.DATA_REPOSITORY_ANNOTATION);
  }

  private static boolean hasAutowiredField(ClassTree tree) {
    return tree.members().stream()
      .filter(member -> member.is(Tree.Kind.VARIABLE))
      .map(VariableTree.class::cast)
      .anyMatch(SpringFilter::isAutowired);
  }

  private static boolean isAutowired(VariableTree tree) {
    return tree.symbol().metadata().isAnnotatedWith(SpringUtils.AUTOWIRED_ANNOTATION)
      // missing semantic
      || hasUnknownAnnotationWithName(tree.modifiers(), "Autowired");
  }

  private static boolean isTransactional(ClassTree tree) {
    return tree.symbol().metadata().isAnnotatedWith(SpringUtils.TRANSACTIONAL_ANNOTATION)
      // missing semantic
      || hasUnknownAnnotationWithName(tree.modifiers(), "Transactional");
  }

  private static boolean hasUnknownAnnotationWithName(ModifiersTree modifiers, String annotation) {
    // Token based check in case of missing semantic
    List<AnnotationTree> annotations = modifiers.annotations();
    if (annotations.isEmpty()) {
      // avoid creation of streams for methods without annotations (should be a large majority of methods)
      return false;
    }
    return annotations.stream()
      .map(AnnotationTree::annotationType)
      .filter(a -> a.symbolType().isUnknown())
      // unknown annotation potentially matching - checking for tokens
      .filter(tree -> tree.is(Tree.Kind.IDENTIFIER, Tree.Kind.MEMBER_SELECT))
      .map(ExpressionTree.class::cast)
      .map(ExpressionsHelper::concatenate)
      .anyMatch(annotationName -> annotationName.endsWith(annotation));
  }
}
