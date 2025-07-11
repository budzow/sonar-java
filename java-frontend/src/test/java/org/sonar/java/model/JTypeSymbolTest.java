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
package org.sonar.java.model;

import java.util.Objects;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.junit.jupiter.api.Test;
import org.sonar.java.model.declaration.ClassTreeImpl;
import org.sonar.java.model.declaration.MethodTreeImpl;
import org.sonar.java.model.declaration.VariableTreeImpl;
import org.sonar.plugins.java.api.semantic.Type;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

class JTypeSymbolTest {

  @Test
  void superClass() {
    JavaTree.CompilationUnitTreeImpl cu = test("interface I { } class C implements I { } class C2 extends C { }");
    ITypeBinding javaLangObject = Objects.requireNonNull(cu.sema.resolveType("java.lang.Object"));
    ITypeBinding i = Objects.requireNonNull(((ClassTreeImpl) cu.types().get(0)).typeBinding);
    ITypeBinding c = Objects.requireNonNull(((ClassTreeImpl) cu.types().get(1)).typeBinding);
    ITypeBinding c2 = Objects.requireNonNull(((ClassTreeImpl) cu.types().get(2)).typeBinding);
    assertAll(
      // for java.lang.Object
      () ->
        assertThat(cu.sema.typeSymbol(javaLangObject).superClass())
          .isNull(),
      // for interfaces
      () ->
        assertThat(cu.sema.typeSymbol(i).superClass())
          .isSameAs(cu.sema.type(javaLangObject)),
      // for classes
      () ->
        assertThat(cu.sema.typeSymbol(c).superClass())
          .isSameAs(cu.sema.type(javaLangObject)),
      () ->
        assertThat(cu.sema.typeSymbol(c2).superClass())
          .isSameAs(cu.sema.type(c)),
      // for arrays
      () ->
        assertThat(cu.sema.typeSymbol(javaLangObject.createArrayType(1)).superClass())
          .isSameAs(cu.sema.type(javaLangObject))
    );
  }

  @Test
  void testSuperClassWhenSemanticCanNotResolveObjectType() {
    var cu = test("interface I{}");
    var i = (ClassTreeImpl) cu.types().get(0);

    JSema sematicThatCanNotResolveObjectType = spy(cu.sema);
    when(sematicThatCanNotResolveObjectType.resolveType("java.lang.Object")).thenReturn(null);
    var typeSymbol = new JTypeSymbol(sematicThatCanNotResolveObjectType, i.typeBinding);
    assertThat(typeSymbol.superClass()).isSameAs(Type.UNKNOWN);
  }

  @Test
  void interfaces() {
    JavaTree.CompilationUnitTreeImpl cu = test("interface I { } class C implements I { }");
    ITypeBinding i = Objects.requireNonNull(((ClassTreeImpl) cu.types().get(0)).typeBinding);
    ITypeBinding c = Objects.requireNonNull(((ClassTreeImpl) cu.types().get(1)).typeBinding);
    assertThat(cu.sema.typeSymbol(i).interfaces())
      .isEmpty();
    assertThat(cu.sema.typeSymbol(c).interfaces())
      .containsOnly(cu.sema.type(i));
  }

  @Test
  void memberSymbols() {
    JavaTree.CompilationUnitTreeImpl cu = test("class C { int f; C() {} class N {} }");
    ClassTreeImpl c = (ClassTreeImpl) cu.types().get(0);
    VariableTreeImpl f = (VariableTreeImpl) c.members().get(0);
    MethodTreeImpl m = (MethodTreeImpl) c.members().get(1);
    ClassTreeImpl n = (ClassTreeImpl) c.members().get(2);
    assertThat(cu.sema.typeSymbol(c.typeBinding).memberSymbols())
      .containsOnly(
        cu.sema.variableSymbol(f.variableBinding),
        cu.sema.methodSymbol(m.methodBinding),
        cu.sema.typeSymbol(n.typeBinding)
      );
  }

  @Test
  void superTypesTest(){
    JavaTree.CompilationUnitTreeImpl cu = test("interface I { } class C implements I { } class C2 extends C { }");
    ITypeBinding javaLangObject = Objects.requireNonNull(cu.sema.resolveType("java.lang.Object"));
    assertThat(cu.sema.typeSymbol(javaLangObject).superTypes()).isEmpty();

    ClassTreeImpl interfaceI = (ClassTreeImpl) cu.types().get(0);
    JTypeSymbol interfaceITypeSymbol = cu.sema.typeSymbol(interfaceI.typeBinding);
    assertThat(interfaceITypeSymbol.superTypes()).isEmpty();
    assertThat(interfaceITypeSymbol.superTypes()).isEmpty(); // repeat call to cover cache

    ClassTreeImpl classC = (ClassTreeImpl) cu.types().get(1);
    JTypeSymbol classCTypeSymbol = cu.sema.typeSymbol(classC.typeBinding);
    assertThat(classCTypeSymbol.superTypes()).containsExactly(interfaceITypeSymbol.type(), cu.sema.type(javaLangObject));


    ClassTreeImpl classC2 = (ClassTreeImpl) cu.types().get(2);
    JTypeSymbol classC2TypeSymbol = cu.sema.typeSymbol(classC2.typeBinding);
    assertThat(classC2TypeSymbol.superTypes()).containsExactly(classCTypeSymbol.type() ,interfaceITypeSymbol.type(), cu.sema.type(javaLangObject));

    ITypeBinding brokenTypeBinding = spy(classC2.typeBinding);
    when(brokenTypeBinding.isRecovered()).thenReturn(true);
    JTypeSymbol brokenTypeSymbol = new JTypeSymbol(cu.sema, brokenTypeBinding);
    assertThat(brokenTypeSymbol.superTypes()).isEmpty();
  }

  @Test
  void outermostClassTest() {
    JavaTree.CompilationUnitTreeImpl cu = test("class C { class N {} }");
    ClassTreeImpl outerClass = (ClassTreeImpl) cu.types().get(0);
    ClassTreeImpl innerClass = (ClassTreeImpl) outerClass.members().get(0);
    JTypeSymbol innerClassSymbol = cu.sema.typeSymbol(innerClass.typeBinding);
    JTypeSymbol outerClassSymbol = cu.sema.typeSymbol(outerClass.typeBinding);
    assertThat(innerClassSymbol.outermostClass()).isSameAs(outerClassSymbol);
    assertThat(innerClassSymbol.outermostClass()).isSameAs(outerClassSymbol);
  }

  @Test
  void isAnnotationTest(){
    JTypeSymbol annotationSymbol = getJTypeSymbolFromClassText("@interface A { }", true);
    assertThat(annotationSymbol.isAnnotation()).isFalse();
    annotationSymbol = getJTypeSymbolFromClassText("@interface A { }", false);
    assertThat(annotationSymbol.isAnnotation()).isTrue();
    JTypeSymbol classSymbol = getJTypeSymbolFromClassText("class C { }", false);
    assertThat(classSymbol.isAnnotation()).isFalse();
  }

  @Test
  void isEffectivelyFinalTest(){
    JTypeSymbol classSymbol = getJTypeSymbolFromClassText("class C { }", false);
    assertThat(classSymbol.superSymbol.isEffectivelyFinal()).isFalse();
  }

  private static JTypeSymbol getJTypeSymbolFromClassText(String classText, boolean isUnknown){
    JavaTree.CompilationUnitTreeImpl cu = test(classText);
    ClassTreeImpl c = (ClassTreeImpl) cu.types().get(0);
    if(isUnknown){
      ITypeBinding brokenTypeBinding = spy(c.typeBinding);
      when(brokenTypeBinding.isRecovered()).thenReturn(true);
      return new JTypeSymbol(cu.sema, brokenTypeBinding);
    }
    return new JTypeSymbol(cu.sema, c.typeBinding);
  }

  private static JavaTree.CompilationUnitTreeImpl test(String source) {
    return (JavaTree.CompilationUnitTreeImpl) JParserTestUtils.parse(source);
  }

}
