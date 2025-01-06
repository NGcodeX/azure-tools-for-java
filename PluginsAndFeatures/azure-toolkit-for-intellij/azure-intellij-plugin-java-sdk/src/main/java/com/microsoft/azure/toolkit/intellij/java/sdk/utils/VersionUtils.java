// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.utils;

import java.util.Map;
import java.util.Set;

/**
 * Utility class for pom version related operations.
 */
public class VersionUtils {

    /**
     * Retrieves the recommended version for a given library.
     *
     * @param libraryName The name of the library.
     * @param recommendedVersionMap The map containing the recommended versions.
     *
     * @return The recommended version of the library, or null if not found.
     */
    public static String getRecommendedVersion(String libraryName, Map<String, String> recommendedVersionMap) {
        return recommendedVersionMap == null ? null : recommendedVersionMap.get(libraryName);
    }

    /**
     * Extracts the minor version from a given version string.
     *
     * @param version The version string.
     * @return The minor version in the format "major.minor", or null if the input is invalid.
     */
    public static String extractMinorVersion(String version) {
        if (version == null || !version.matches("\\d+\\.\\d+.*")) {
            return null;
        }
        String[] parts = version.split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : null;
    }

    /**
     * Method to get the version group for the library. The version group is used to get the compatible versions for
     * the library. The version group is determined by the major and minor version of the library. Eg if the major
     * version is 2 and the minor version is 10, the version group is "jackson_2.10".
     *
     * @param fullName The full name of the library eg "com.azure:azure-core"
     * @param currentVersion The current version of the library
     * @param fileContent The content of the version file (as a map)
     *
     * @return The version group for the library
     */
    public static String getGroupVersion(String fullName, String currentVersion, Map<String, Set<String>> fileContent) {
        // Split currentVersion to extract major and minor version
        String[] versionParts = currentVersion.split("\\.");
        String majorVersion = versionParts[0];
        String minorVersion = versionParts.length > 1 ? versionParts[1] : "";
        String versionSuffix = "_" + majorVersion + "." + minorVersion;

        // Search the file content for the version group
        String versionGroup = null;
        for (Map.Entry<String, Set<String>> entry : fileContent.entrySet()) {
            // Check if the set of artifactIds contains the fullName and the corresponding key ends with the versionSuffix
            if (entry.getValue().contains(fullName) && entry.getKey().endsWith(versionSuffix)) {
                versionGroup = entry.getKey();
                break;
            }
        }
        return versionGroup;
    }

    /**
     * Method to check if two versions are incompatible based on their minor version.
     *
     * @param currentVersion The current version of the library
     * @param recommendedVersion The recommended version of the library
     * @return true if the versions are incompatible, false otherwise
     */
    public static boolean isIncompatibleVersion(String currentVersion, String recommendedVersion) {
        String[] currentVersionParts = currentVersion.split("\\.");
        String[] recommendedVersionParts = recommendedVersion.split("\\.");

        boolean isIncompatible = false;
        for (int i = 0; i < Math.min(currentVersionParts.length, recommendedVersionParts.length); i++) {
            if (!currentVersionParts[i].equals(recommendedVersionParts[i])) {
                isIncompatible = true;
                break;
            }
        }
        return isIncompatible;
    }
}
