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
package org.sonar.java.checks.spring;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.sonar.check.Rule;
import org.sonar.java.checks.helpers.SpringUtils;
import org.sonar.plugins.java.api.IssuableSubscriptionVisitor;
import org.sonar.plugins.java.api.semantic.MethodMatchers;
import org.sonar.plugins.java.api.semantic.SymbolMetadata;
import org.sonar.plugins.java.api.tree.AnnotationTree;
import org.sonar.plugins.java.api.tree.BaseTreeVisitor;
import org.sonar.plugins.java.api.tree.ClassTree;
import org.sonar.plugins.java.api.tree.MethodInvocationTree;
import org.sonar.plugins.java.api.tree.Tree;

@Rule(key = "S3753")
public class ControllerWithSessionAttributesCheck extends IssuableSubscriptionVisitor {

  @Override
  public List<Tree.Kind> nodesToVisit() {
    return Collections.singletonList(Tree.Kind.CLASS);
  }

  @Override
  public void visitNode(Tree tree) {
    ClassTree classTree = (ClassTree) tree;
    SymbolMetadata classMetadata = classTree.symbol().metadata();
    Optional<AnnotationTree> sessionAttributesAnnotation = classTree.modifiers().annotations()
      .stream()
      .filter(a -> a.annotationType().symbolType().is("org.springframework.web.bind.annotation.SessionAttributes"))
      .findFirst();

    if (sessionAttributesAnnotation.isPresent()
        && classMetadata.isAnnotatedWith(SpringUtils.CONTROLLER_ANNOTATION)) {
      MethodInvocationVisitor methodInvocationVisitor = new MethodInvocationVisitor();
      classTree.accept(methodInvocationVisitor);
      if (!methodInvocationVisitor.setCompleteIsCalled) {
        reportIssue(sessionAttributesAnnotation.get().annotationType(),
          "Add a call to \"setComplete()\" on the SessionStatus object in a \"@RequestMapping\" method.");
      }
    }
  }

  /**
   * We don't actually care if setComplete is called in a @RequestMapping method, as long as it eventually gets called inside the controller.
   */
  private static class MethodInvocationVisitor extends BaseTreeVisitor {
    private static final MethodMatchers SET_COMPLETE = MethodMatchers.create()
      .ofTypes("org.springframework.web.bind.support.SessionStatus").names("setComplete").withAnyParameters().build();

    boolean setCompleteIsCalled;

    @Override
    public void visitMethodInvocation(MethodInvocationTree methodInvocationTree) {
      if (SET_COMPLETE.matches(methodInvocationTree)) {
        setCompleteIsCalled = true;
      }
    }
  }
}
