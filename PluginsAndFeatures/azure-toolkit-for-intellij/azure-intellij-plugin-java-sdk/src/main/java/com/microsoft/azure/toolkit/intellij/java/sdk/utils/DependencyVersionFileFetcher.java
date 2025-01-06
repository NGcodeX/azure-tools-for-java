// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.microsoft.azure.toolkit.intellij.java.sdk.utils;

import com.azure.json.JsonProviders;
import com.azure.json.JsonReader;
import com.azure.json.JsonToken;
import com.microsoft.azure.toolkit.intellij.java.sdk.models.DependencyVersionsDataCache;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class to fetch files from their corresponding data sources
 * The class fetches these sources and parses them to get the data.
 * The data is used to check the version of the libraries in the pom.xml file against the recommended version.
 */
public class DependencyVersionFileFetcher {

    private static final Logger LOGGER = Logger.getLogger(DependencyVersionFileFetcher.class.getName());

    private static final DependencyVersionsDataCache<Map<String, String>> pomCache = new DependencyVersionsDataCache<>("pomCache.ser");
    private static final DependencyVersionsDataCache<String> versionCache = new DependencyVersionsDataCache<>("versionCache.ser");
    private static final DependencyVersionsDataCache<Map<String, Set<String>>> incompatibleVersionsCache = new DependencyVersionsDataCache<>("incompatibleVersionsCache.ser");

    /**
     * The parsePomFile method fetches the pom.xml file from the URL and parses it to get the dependencies.
     * This method is used to fetch the pom.xml file from the URL and parse it to get the dependencies.
     * It is used by the UpgradeLibraryVersionCheck inspection.
     *
     * @param pomUrl The URL of the pom.xml file to fetch
     * @return A map of the dependencies in the pom.xml file
     */
    public static Map<String, String> parsePomFile(String pomUrl) {
        Map<String, String> artifactVersionMap = pomCache.get(pomUrl);
        if (artifactVersionMap != null) {
            return artifactVersionMap;
        }

        Document pomDoc = fetchXmlDocument(pomUrl);
        NodeList dependencies = pomDoc.getElementsByTagName("dependency");
        artifactVersionMap = new HashMap<>();

        for (int i = 0; i < dependencies.getLength(); i++) {
            NodeList dependency = dependencies.item(i).getChildNodes();
            String groupId = null, artifactId = null, version = null;

            for (int j = 0; j < dependency.getLength(); j++) {
                switch (dependency.item(j).getNodeName()) {
                    case "groupId":
                        groupId = dependency.item(j).getTextContent();
                        break;
                    case "artifactId":
                        artifactId = dependency.item(j).getTextContent();
                        break;
                    case "version":
                        version = dependency.item(j).getTextContent();
                        break;
                }
            }

            if (groupId != null && artifactId != null && version != null) {
                String minorVersion = version.substring(0, version.lastIndexOf("."));
                artifactVersionMap.put(groupId + ":" + artifactId, minorVersion);
            }
        }

        pomCache.put(pomUrl, artifactVersionMap);
        return artifactVersionMap;
    }

    /**
     * The getLatestVersion method fetches the latest Azure Client release versions from Maven Central.
     * This method is used to fetch the latest version of the library from the metadata file hosted on Maven Central.
     * It is used by the UpgradeLibraryVersionCheck inspection.
     *
     * @param metadataUrl The URL of the metadata file to fetch
     * @return The latest version of the library
     */
    public static String getLatestVersion(String metadataUrl) {
        String cachedVersion = versionCache.get(metadataUrl);
        if (cachedVersion != null) {
            return cachedVersion;
        }

        Document metadataDoc = fetchXmlDocument(metadataUrl);
        NodeList versions = metadataDoc.getElementsByTagName("version");
        String latestVersion = versions.item(versions.getLength() - 1).getTextContent();

        versionCache.put(metadataUrl, latestVersion);
        return latestVersion;
    }

    /**
     * The fetchXmlDocument method fetches an XML document from a URL and parses it.
     * This method is used to fetch the pom.xml file from the URL and parse it to get the dependencies.
     * It is used by the UpgradeLibraryVersionCheck inspection.
     *
     * @param urlString The URL of the XML document to fetch
     * @return The parsed XML document
     */
    private static Document fetchXmlDocument(String urlString) {
        try (InputStream inputStream = new URL(urlString).openStream()) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(inputStream);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            LOGGER.log(Level.SEVERE, "Error fetching or parsing XML from URL: " + urlString, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * The loadJsonDataFromUrl method fetches a .json file from GitHub and parses it to get the data.
     * This method is used to fetch the data for the libraries in the pom.xml file. It is used by the IncompatibleDependencyCheck inspection.
     *
     * @param jsonUrl The URL of the .json file to fetch
     * @return A map of the data for the libraries
     */
    public static Map<String, Set<String>> loadJsonDataFromUrl(String jsonUrl) {
        Map<String, Set<String>> jsonData = incompatibleVersionsCache.get(jsonUrl);
        if (jsonData != null) {
            return jsonData;
        }

        try (InputStream inputStream = new URL(jsonUrl).openStream(); JsonReader jsonReader = JsonProviders.createReader(inputStream)) {
            jsonData = parseJson(jsonReader);
            incompatibleVersionsCache.put(jsonUrl, jsonData);
            return jsonData;
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error reading JSON from URL: " + jsonUrl, e);
            return new HashMap<>();
        }
    }

    /**
     * Method to parse JSON data into a nested map structure
     * The method reads the JSON data from the JsonReader and parses it to get the data for the libraries.
     * The data is in a Map<String, Set<String>> format where the key is the group and the value is a set of artifactIds.
     * For example, the data for the Jackson library is in the format
     * "jackson_2.10: [com.fasterxml.jackson.core:jackson-annotations, com.fasterxml.jackson.core:jackson-core, com.fasterxml.jackson.core:jackson-databind]".
     *
     * @param jsonReader The JsonReader object to read the JSON data
     * @return A map of the data for the libraries
     * @throws IOException If an error occurs while reading the JSON data
     */
    private static Map<String, Set<String>> parseJson(JsonReader jsonReader) throws IOException {
        Map<String, Set<String>> versionData = new ConcurrentHashMap<>();
        if (jsonReader.nextToken() == JsonToken.START_OBJECT) {
            while (jsonReader.nextToken() != JsonToken.END_OBJECT) {
                String groupKey = jsonReader.getFieldName();
                Set<String> groupSet = new HashSet<>();

                if (jsonReader.nextToken() == JsonToken.START_ARRAY) {
                    while (jsonReader.nextToken() != JsonToken.END_ARRAY) {
                        if (jsonReader.nextToken() == JsonToken.FIELD_NAME) {
                            groupSet.add(jsonReader.getFieldName());
                        }
                        while (jsonReader.nextToken() != JsonToken.END_OBJECT) {
                            // Skip remaining tokens in the object
                        }
                    }
                }
                versionData.put(groupKey, groupSet);
            }
        }
        return versionData;
    }
}
