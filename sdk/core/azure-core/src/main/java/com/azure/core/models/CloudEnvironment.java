// Copyright (c) Microsoft Corporation. All rights reserved.
// Licensed under the MIT License.

package com.azure.core.models;

import com.azure.core.util.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Describes the cloud an application targets, as declared by the process environment, so that a
 * cloud can be selected by configuration rather than in application source.
 *
 * <p>{@code AZURE_CLOUD} is the activation switch. When it names a built-in cloud, that cloud is
 * described and the other variables are ignored. Only the value {@code custom} causes
 * {@code AZURE_AUTHORITY_HOST}, {@code AZURE_RESOURCE_MANAGER_ENDPOINT} and
 * {@code AZURE_RESOURCE_MANAGER_AUDIENCE} to be read, and then all three are required.</p>
 *
 * <p>Configuration set in code always takes precedence: a client that specifies its own authority
 * host or Azure environment is unaffected by these variables.</p>
 */
public final class CloudEnvironment {
    /**
     * The base URL of the cloud's Azure Resource Manager. Required when {@code AZURE_CLOUD} is
     * {@code custom}.
     */
    public static final String PROPERTY_AZURE_RESOURCE_MANAGER_ENDPOINT = "AZURE_RESOURCE_MANAGER_ENDPOINT";

    /**
     * The audience requested for Azure Resource Manager access tokens. Required when
     * {@code AZURE_CLOUD} is {@code custom}.
     */
    public static final String PROPERTY_AZURE_RESOURCE_MANAGER_AUDIENCE = "AZURE_RESOURCE_MANAGER_AUDIENCE";

    /** Canonical name of the Azure Public cloud. */
    public static final String AZURE_CLOUD_NAME = "AzureCloud";

    /** Canonical name of the Azure US Government cloud. */
    public static final String AZURE_US_GOVERNMENT_NAME = "AzureUSGovernment";

    /** Canonical name of the Azure China cloud. */
    public static final String AZURE_CHINA_NAME = "AzureChinaCloud";

    /** Name identifying a custom cloud. */
    public static final String CUSTOM_NAME = "custom";

    private static final String AZURE_PUBLIC_AUTHORITY = "https://login.microsoftonline.com/";
    private static final String AZURE_US_GOVERNMENT_AUTHORITY = "https://login.microsoftonline.us/";
    private static final String AZURE_CHINA_AUTHORITY = "https://login.chinacloudapi.cn/";

    private final String name;
    private final String authorityHost;
    private final String resourceManagerEndpoint;
    private final String resourceManagerAudience;

    private CloudEnvironment(String name, String authorityHost, String resourceManagerEndpoint,
        String resourceManagerAudience) {
        this.name = name;
        this.authorityHost = authorityHost;
        this.resourceManagerEndpoint = resourceManagerEndpoint;
        this.resourceManagerAudience = resourceManagerAudience;
    }

    /**
     * Resolves the cloud described by the global configuration.
     *
     * @return the resolved environment, or {@code null} when {@code AZURE_CLOUD} is not set.
     * @throws IllegalArgumentException when {@code AZURE_CLOUD} names an unrecognized cloud, or is
     * {@code custom} and the required endpoint variables are missing or malformed.
     */
    public static CloudEnvironment fromEnvironment() {
        return fromConfiguration(Configuration.getGlobalConfiguration());
    }

    /**
     * Resolves the cloud described by the supplied configuration.
     *
     * @param configuration the configuration to read; must not be {@code null}.
     * @return the resolved environment, or {@code null} when {@code AZURE_CLOUD} is not set.
     * @throws IllegalArgumentException when {@code AZURE_CLOUD} names an unrecognized cloud, or is
     * {@code custom} and the required endpoint variables are missing or malformed.
     */
    public static CloudEnvironment fromConfiguration(Configuration configuration) {
        Objects.requireNonNull(configuration, "'configuration' cannot be null.");

        String name = trimToNull(configuration.get(Configuration.PROPERTY_AZURE_CLOUD));
        if (name == null) {
            return null;
        }

        // Three vocabularies are in play for this variable and all are accepted.
        //
        //  - "AzureCloud" and friends are the canonical cross-SDK spelling, matching `az cloud list`.
        //  - "AZURE_PUBLIC_CLOUD" and friends are the values com.azure.core.models.AzureCloud and
        //    Python's azure.core.AzureClouds already ship. Those DIVERGE from the canonical
        //    cross-SDK spelling; the divergence predates this resolver and is preserved here only
        //    for compatibility.
        //  - "AZURE", "AZURE_CHINA" and "AZURE_US_GOVERNMENT" are Spring Cloud Azure's CloudType
        //    values. Spring already maps AZURE_CLOUD onto its profile.cloud-type property, so
        //    rejecting them would break Spring applications that set this variable today.
        switch (name.toLowerCase(Locale.ROOT)) {
            case "azurecloud":
            case "public":
            case "azure_public_cloud":
            case "azure":
                return new CloudEnvironment(AZURE_CLOUD_NAME, AZURE_PUBLIC_AUTHORITY, null, null);

            case "azureusgovernment":
            case "usgovernment":
            case "azure_us_government":
                return new CloudEnvironment(AZURE_US_GOVERNMENT_NAME, AZURE_US_GOVERNMENT_AUTHORITY, null, null);

            case "azurechinacloud":
            case "china":
            case "azure_china_cloud":
            case "azure_china":
                return new CloudEnvironment(AZURE_CHINA_NAME, AZURE_CHINA_AUTHORITY, null, null);

            case "custom":
                return custom(configuration);

            case "other":
                // Spring Cloud Azure's CloudType.OTHER means "I configure endpoints myself", not
                // "read the endpoint variables". Treating it as a custom cloud would demand
                // variables such an application has never set and fail its clients at
                // construction, so it is treated as though AZURE_CLOUD were unset.
                return null;

            case "azure_germany":
                throw new IllegalArgumentException(Configuration.PROPERTY_AZURE_CLOUD
                    + " names Microsoft Cloud Germany, which closed on October 29th, 2021.");

            default:
                throw new IllegalArgumentException(Configuration.PROPERTY_AZURE_CLOUD + " has unrecognized value \""
                    + name + "\". Valid values are " + AZURE_CLOUD_NAME + ", " + AZURE_US_GOVERNMENT_NAME + ", "
                    + AZURE_CHINA_NAME + " and " + CUSTOM_NAME + ".");
        }
    }

    private static CloudEnvironment custom(Configuration configuration) {
        String authority = trimToNull(configuration.get(Configuration.PROPERTY_AZURE_AUTHORITY_HOST));
        String endpoint = trimToNull(configuration.get(PROPERTY_AZURE_RESOURCE_MANAGER_ENDPOINT));
        String audience = trimToNull(configuration.get(PROPERTY_AZURE_RESOURCE_MANAGER_AUDIENCE));

        List<String> missing = new ArrayList<>(3);
        if (authority == null) {
            missing.add(Configuration.PROPERTY_AZURE_AUTHORITY_HOST);
        }
        if (endpoint == null) {
            missing.add(PROPERTY_AZURE_RESOURCE_MANAGER_ENDPOINT);
        }
        if (audience == null) {
            missing.add(PROPERTY_AZURE_RESOURCE_MANAGER_AUDIENCE);
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(Configuration.PROPERTY_AZURE_CLOUD + " is \"" + CUSTOM_NAME + "\" but "
                + String.join(", ", missing) + (missing.size() == 1 ? " is" : " are") + " not set. A custom cloud"
                + " requires all of " + Configuration.PROPERTY_AZURE_AUTHORITY_HOST + ", "
                + PROPERTY_AZURE_RESOURCE_MANAGER_ENDPOINT + " and " + PROPERTY_AZURE_RESOURCE_MANAGER_AUDIENCE + ".");
        }

        validateHttps(Configuration.PROPERTY_AZURE_AUTHORITY_HOST, authority);
        validateHttps(PROPERTY_AZURE_RESOURCE_MANAGER_ENDPOINT, endpoint);
        validateHttps(PROPERTY_AZURE_RESOURCE_MANAGER_AUDIENCE, audience);

        return new CloudEnvironment(CUSTOM_NAME, authority, endpoint, audience);
    }

    private static void validateHttps(String variable, String value) {
        if (!value.toLowerCase(Locale.ROOT).startsWith("https://")) {
            throw new IllegalArgumentException(variable + " must use https. Got \"" + value + "\".");
        }
        if (value.length() <= "https://".length()) {
            throw new IllegalArgumentException(variable + " must specify a host. Got \"" + value + "\".");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Gets the canonical name of the cloud.
     *
     * @return one of {@code AzureCloud}, {@code AzureUSGovernment}, {@code AzureChinaCloud} or {@code custom}.
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the base URL of the cloud's Microsoft Entra authority.
     *
     * @return the authority host.
     */
    public String getAuthorityHost() {
        return authorityHost;
    }

    /**
     * Gets the base URL of the cloud's Azure Resource Manager.
     *
     * @return the endpoint, or {@code null} for a built-in cloud whose endpoint is already known.
     */
    public String getResourceManagerEndpoint() {
        return resourceManagerEndpoint;
    }

    /**
     * Gets the audience requested for Azure Resource Manager access tokens.
     *
     * @return the audience, or {@code null} for a built-in cloud whose audience is already known.
     */
    public String getResourceManagerAudience() {
        return resourceManagerAudience;
    }

    /**
     * Whether this environment describes a custom cloud rather than a built-in one.
     *
     * @return true when the cloud is custom.
     */
    public boolean isCustom() {
        return resourceManagerEndpoint != null;
    }
}
