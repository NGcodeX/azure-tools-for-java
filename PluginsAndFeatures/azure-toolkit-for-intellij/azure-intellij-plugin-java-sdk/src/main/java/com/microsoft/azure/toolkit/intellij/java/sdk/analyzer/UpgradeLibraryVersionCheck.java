// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.analyzer;

import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.RuleConfig;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.DependencyVersionFileFetcher;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.RuleConfigLoader;
import com.microsoft.azure.toolkit.intellij.java.sdk.utils.VersionUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.NotNull;

import static com.microsoft.azure.toolkit.intellij.java.sdk.analyzer.UpgradeLibraryVersionCheck.LibraryVersionVisitor.getRecommendedVersionMap;

/**
 * Inspection class to check the version of the libraries in the pom.xml file against the recommended version.
 */
public class UpgradeLibraryVersionCheck extends AbstractLibraryVersionCheck {
    private static final Logger LOG = Logger.getLogger(UpgradeLibraryVersionCheck.class.getName());

    @NotNull
    @Override
    public PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
        return new LibraryVersionVisitor(holder);
    }

    @Override
    protected void checkAndFlagVersion(String fullName, String currentVersion, ProblemsHolder holder, PsiElement versionTag) {
        String recommendedVersion = VersionUtils.getRecommendedVersion(fullName, getRecommendedVersionMap());
        if (recommendedVersion == null) {
            return;
        }

        String currentMinorVersion = VersionUtils.extractMinorVersion(currentVersion);
        if (currentMinorVersion == null || currentMinorVersion.equals(recommendedVersion)) {
            return;
        }

        holder.registerProblem(versionTag, getFormattedMessage(fullName, recommendedVersion, LibraryVersionVisitor.RULE_CONFIG));
    }

    class LibraryVersionVisitor extends PsiElementVisitor {

        private final ProblemsHolder holder;
        private static final RuleConfig RULE_CONFIG;
        private static final boolean SKIP_WHOLE_RULE;

        // Cache for recommended versions
        private static Map<String, String> VERSION_MAP_CACHE;

        static {
            RULE_CONFIG = RuleConfigLoader.getInstance().getRuleConfig("UpgradeLibraryVersionCheck");
            SKIP_WHOLE_RULE = RULE_CONFIG.skipRuleCheck();
        }

        LibraryVersionVisitor(ProblemsHolder holder) {
            this.holder = holder;
        }

        @Override
        public void visitFile(@NotNull PsiFile file) {
            super.visitFile(file);

            if (SKIP_WHOLE_RULE || !(file instanceof XmlFile) || !RuleConfig.POM_XML.equals(file.getName())) {
                return;
            }

            try {
                UpgradeLibraryVersionCheck.this.checkPomXml((XmlFile) file, holder);
            } catch (IOException e) {
                LOG.log(Level.ALL, "Error while parsing pom.xml", e);
            }
        }

        static Map<String, String> getRecommendedVersionMap() {
            Map<String, String> cachedMap = VERSION_MAP_CACHE == null ? null : VERSION_MAP_CACHE;
            if (cachedMap != null) {
                return cachedMap;
            }

            synchronized (VersionUtils.class) {
                cachedMap = VERSION_MAP_CACHE == null ? null : VERSION_MAP_CACHE;
                if (cachedMap == null) {
                    String latestVersion = DependencyVersionFileFetcher.getLatestVersion(RULE_CONFIG.AZURE_MAVEN_METADATA_URL);
                    if (latestVersion != null) {
                        String baseUrl = RULE_CONFIG.AZURE_MAVEN_METADATA_URL.replace("/maven-metadata.xml", "");
                        String pomUrl = String.format("%s/%s/azure-sdk-bom-%s.pom", baseUrl, latestVersion, latestVersion);
                        cachedMap = DependencyVersionFileFetcher.parsePomFile(pomUrl);
                        VERSION_MAP_CACHE = new HashMap<>(cachedMap);
                    }
                }
            }
            return cachedMap;
        }
    }
}