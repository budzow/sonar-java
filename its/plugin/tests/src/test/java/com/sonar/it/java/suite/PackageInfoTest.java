/*
 * SonarQube Java
 * Copyright (C) 2013-2025 SonarSource SA
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
package com.sonar.it.java.suite;

import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.junit4.OrchestratorRule;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.ClassRule;
import org.junit.Test;
import org.sonarqube.ws.Issues.Issue;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class PackageInfoTest {

  @ClassRule
  public static OrchestratorRule orchestrator = JavaTestSuite.ORCHESTRATOR;

  @Test
  public void should_detect_package_info_issues() {
    String projectKey = "org.sonarsource.it.projects:package-info";
    MavenBuild build = TestUtils.createMavenBuild().setPom(TestUtils.projectPom("package-info"))
      .setCleanPackageSonarGoals()
      .setProperty("sonar.sources", "src/main/java,src/main/other-src")
      .setProperty("sonar.scm.disabled", "true");
    TestUtils.provisionProject(orchestrator, projectKey, "package-info", "java", "package-info");

    orchestrator.executeBuild(build);

    List<Issue> issues = TestUtils.issuesForComponent(orchestrator, projectKey);
    List<String> packageInfoRuleKeys = asList("java:S1228", "java:S4032");

    assertThat(issues).hasSize(3);
    assertThat(issues.stream().map(Issue::getRule)).allMatch(packageInfoRuleKeys::contains);
    assertThat(issues.stream().map(Issue::getLine)).allMatch(line -> line == 0);

    Pattern packagePattern = Pattern.compile("'org\\.package[12]'");
    List<Issue> s1228Issues = issues.stream().filter(issue -> issue.getRule().equals("java:S1228")).toList();
    assertThat(s1228Issues).hasSize(2);
    assertThat(s1228Issues).extracting(Issue::getMessage).allMatch(msg -> packagePattern.matcher(msg).find());

    List<Issue> s4032Issues = issues.stream().filter(issue -> issue.getRule().equals("java:S4032")).toList();
    assertThat(s4032Issues).hasSize(1);
    assertThat(s4032Issues.get(0).getMessage()).isEqualTo("Remove this package.");
    assertThat(s4032Issues.get(0).getComponent()).isEqualTo(projectKey + ":src/main/other-src/org/package4/package-info.java");

    List<Issue> issuesOnTestPackage = TestUtils.issuesForComponent(orchestrator, projectKey + ":src/test/java/package1");
    assertThat(issuesOnTestPackage).isEmpty();
  }

}
