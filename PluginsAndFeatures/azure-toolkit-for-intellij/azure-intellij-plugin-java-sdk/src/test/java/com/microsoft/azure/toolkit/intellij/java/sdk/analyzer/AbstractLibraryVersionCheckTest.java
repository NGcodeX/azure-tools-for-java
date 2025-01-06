// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.lang.StdLanguages;
import com.intellij.openapi.project.Project;
import com.intellij.psi.FileViewProvider;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlTagValue;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/*
 * This test class is used to test the AbstractLibraryVersionCheck class.
 */
public class AbstractLibraryVersionCheckTest {

    @Mock
    private ProblemsHolder mockHolder;

    @Mock
    private XmlFile mockFile;

    @BeforeEach
    public void setup() {
        mockHolder = mock(ProblemsHolder.class);
        mockFile = mock(XmlFile.class);

        IncompatibleDependencyCheck.encounteredVersionGroups = new HashSet<>(List.of("jackson_2.10", "gson_2.10"));
    }

    static Stream<TestCase> testCases() {
        return Stream.of(
            new TestCase("com.azure", "azure-messaging-servicebus", "7.0.0", 1, "7.17", UpgradeLibraryVersionCheck.LibraryVersionVisitor.class),
            new TestCase("com.azure", "azure-messaging-servicebus", "7.17.1", 0, "7.17", UpgradeLibraryVersionCheck.LibraryVersionVisitor.class),
            new TestCase("com.example", "example-lib", "", 0, null, UpgradeLibraryVersionCheck.LibraryVersionVisitor.class),
            new TestCase("com.google.code.gson", "gson", "2.9.0", 1, "2.10", IncompatibleDependencyCheck.IncompatibleDependencyVisitor.class),
            new TestCase("com.fasterxml.jackson.core", "jackson-databind", "2.10.0", 0, "2.10", IncompatibleDependencyCheck.IncompatibleDependencyVisitor.class),
            new TestCase("com.fasterxml.jackson.core", "jackson-databind", "3.0.0", 0, null, IncompatibleDependencyCheck.IncompatibleDependencyVisitor.class)
        );
    }

    @ParameterizedTest
    @MethodSource("testCases")
    void testLibraryVersionChecks(TestCase testCase) {
        PsiElementVisitor visitor = createVisitor(testCase.visitorClass, mockHolder);

        XmlTag versionTag = setupMockXml(testCase.groupID, testCase.artifactID, testCase.version);
        visitor.visitFile(mockFile);

        if (testCase.visitorClass == UpgradeLibraryVersionCheck.LibraryVersionVisitor.class) {
            // Validate the full message if warnings are expected
            if (testCase.expectedInvocations > 0 && testCase.expectedMessage != null) {
                String expectedMessage = String.format(
                    "A newer stable minor version of '%s:%s' is available. We recommend you update to version %s.x.",
                    testCase.groupID, testCase.artifactID, testCase.expectedMessage
                );
                verify(mockHolder).registerProblem(versionTag, expectedMessage);
            }
        } else if (testCase.visitorClass == IncompatibleDependencyCheck.IncompatibleDependencyVisitor.class) {
            if (testCase.expectedInvocations > 0 && testCase.expectedMessage != null) {
                String expectedMessage = String.format(
                    "The version of '%s:%s' is incompatible with other dependencies of the same " +
                        "library defined in the pom.xml. We recommend you update to version %s.x of the same library " +
                        "release group.",
                    testCase.groupID, testCase.artifactID, testCase.expectedMessage
                );
                verify(mockHolder).registerProblem(versionTag, expectedMessage);
            }

        }
    }

    private PsiElementVisitor createVisitor(Class<? extends PsiElementVisitor> visitorClass, ProblemsHolder holder) {
        if (visitorClass == UpgradeLibraryVersionCheck.LibraryVersionVisitor.class) {
            return new UpgradeLibraryVersionCheck().new LibraryVersionVisitor(holder);
        } else if (visitorClass == IncompatibleDependencyCheck.IncompatibleDependencyVisitor.class) {
            return new IncompatibleDependencyCheck().new IncompatibleDependencyVisitor(holder);
        }
        throw new IllegalArgumentException("Unsupported visitor class: " + visitorClass);
    }

    private XmlTag setupMockXml(String groupIDValue, String artifactIDValue, String versionIDValue) {
        Project project = mock(Project.class);
        MavenProjectsManager mavenProjectsManager = mock(MavenProjectsManager.class);
        FileViewProvider viewProvider = mock(FileViewProvider.class);
        XmlTag rootTag = mock(XmlTag.class);

        XmlTag dependenciesTag = mock(XmlTag.class);
        XmlTag[] dependenciesTags = new XmlTag[]{dependenciesTag};

        XmlTag dependencyTag = mock(XmlTag.class);
        XmlTag[] dependencyTags = new XmlTag[]{dependencyTag};

        XmlTag groupIdTag = mock(XmlTag.class);
        XmlTagValue groupIdValue = mock(XmlTagValue.class);
        XmlTag artifactIdTag = mock(XmlTag.class);
        XmlTagValue artifactIdValue = mock(XmlTagValue.class);
        XmlTag versionTag = mock(XmlTag.class);
        XmlTagValue versionValue = mock(XmlTagValue.class);

        when(mockFile.getName()).thenReturn("pom.xml");
        when(mockFile.getProject()).thenReturn(project);
        when(MavenProjectsManager.getInstance(project)).thenReturn(mavenProjectsManager);
        when(mavenProjectsManager.isMavenizedProject()).thenReturn(true);

        when(mockFile.getViewProvider()).thenReturn(viewProvider);
        when(viewProvider.getPsi(StdLanguages.XML)).thenReturn(mockFile);
        when(mockFile.getRootTag()).thenReturn(rootTag);
        when(rootTag.getName()).thenReturn("project");

        when(rootTag.findSubTags("dependencies")).thenReturn(dependenciesTags);
        when(dependenciesTag.findSubTags("dependency")).thenReturn(dependencyTags);

        when(dependencyTag.findFirstSubTag("groupId")).thenReturn(groupIdTag);
        when(dependencyTag.findFirstSubTag("artifactId")).thenReturn(artifactIdTag);
        when(dependencyTag.findFirstSubTag("version")).thenReturn(versionTag);

        when(groupIdTag.getValue()).thenReturn(groupIdValue);
        when(artifactIdTag.getValue()).thenReturn(artifactIdValue);
        when(versionTag.getValue()).thenReturn(versionValue);

        when(groupIdValue.getText()).thenReturn(groupIDValue);
        when(artifactIdValue.getText()).thenReturn(artifactIDValue);
        when(versionValue.getText()).thenReturn(versionIDValue);

        return versionTag;
    }

    private void verifyProblemsRegistered(TestCase testCase, XmlTag versionTag) {
        if (testCase.expectedInvocations > 0) {
            verify(mockHolder, times(testCase.expectedInvocations)).registerProblem(eq(versionTag), contains(testCase.expectedMessage));
        } else {
            verify(mockHolder, never()).registerProblem(eq(versionTag), anyString());
        }
    }

    private static class TestCase {
        String groupID;
        String artifactID;
        String version;
        int expectedInvocations;
        String expectedMessage;
        Class<? extends PsiElementVisitor> visitorClass;

        TestCase(String groupID, String artifactID, String version, int expectedInvocations, String expectedMessage, Class<? extends PsiElementVisitor> visitorClass) {
            this.groupID = groupID;
            this.artifactID = artifactID;
            this.version = version;
            this.expectedInvocations = expectedInvocations;
            this.expectedMessage = expectedMessage;
            this.visitorClass = visitorClass;
        }
    }
}