package fr.hardcoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.Response.Status.OK;
import static java.nio.charset.StandardCharsets.UTF_8;

@ApplicationScoped
public class BlueskyService {
    private static final Logger LOG = LoggerFactory.getLogger(BlueskyService.class);
    private static final String BLUESKY_API_URL = "https://bsky.social/xrpc/com.atproto.repo.createRecord";
    private static final String BLUESKY_AUTH_URL = "https://bsky.social/xrpc/com.atproto.server.createSession";
    private static final Pattern OPENJDK_LINK_PATTERN = Pattern.compile("openjdk\\.org/jeps/(\\d+)");

    @ConfigProperty(name = "bluesky.handle")
    String handle;

    @ConfigProperty(name = "bluesky.app-password")
    String appPassword;

    private final Client client;
    private final AtomicReference<String> authToken = new AtomicReference<>();
    private final AtomicReference<Instant> tokenExpiry = new AtomicReference<>();

    public BlueskyService() {
        this.client = ClientBuilder.newClient();
    }

    public void postUpdate(String text) {
        LOG.debug("Posting {}", text);
        try {
            String token = getAuthToken();
            String payload = createPostRequest(text);
            try (Response response = this.client.target(BLUESKY_API_URL)
                    .request(APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .post(Entity.json(payload))) {
                if (response.getStatus() != OK.getStatusCode()) {
                    LOG.error("Failed to post to Bluesky: {} from payload {}", response.readEntity(String.class), payload);
                }
            }
        } catch (Exception e) {
            LOG.error("Error posting to Bluesky", e);
        }
    }

    private String getAuthToken() {
        // Check if we have a valid token
        Instant now = Instant.now();
        if (this.authToken.get() != null && this.tokenExpiry.get() != null && now.isBefore(this.tokenExpiry.get())) {
            return authToken.get();
        }
        // Create authentication request
        String authRequest = String.format("""
                {
                    "identifier": "%s",
                    "password": "%s"
                }""", this.handle, this.appPassword);
        try (Response response = this.client.target(BLUESKY_AUTH_URL)
                .request(APPLICATION_JSON)
                .post(Entity.json(authRequest))) {
            if (response.getStatus() == OK.getStatusCode()) {
                String responseBody = response.readEntity(String.class);
                // Extract accessJwt from response
                String accessJwt = extractAccessJwt(responseBody);
                if (accessJwt != null) {
                    // Store token and set expiry to 24 hours from now
                    this.authToken.set(accessJwt);
                    this.tokenExpiry.set(now.plusSeconds(24 * 60 * 60));
                    return accessJwt;
                }
            }
            LOG.error("Failed to authenticate with Bluesky: {}", response.readEntity(String.class));
            throw new RuntimeException("Failed to authenticate with Bluesky");
        } catch (Exception e) {
            LOG.error("Error during Bluesky authentication", e);
            throw new RuntimeException("Error during Bluesky authentication", e);
        }
    }

    private String extractAccessJwt(String responseBody) {
        // Simple JSON parsing to extract accessJwt
        // In a production environment, you should use proper JSON parsing
        int startIndex = responseBody.indexOf("\"accessJwt\":\"") + 13;
        if (startIndex > 13) {
            int endIndex = responseBody.indexOf("\"", startIndex);
            if (endIndex > startIndex) {
                return responseBody.substring(startIndex, endIndex);
            }
        }
        return null;
    }

    private String createPostRequest(String text) {
        String formattedText = text.replaceAll("\n", "\\\\n");
        String facetsJson = findFacets(text);
        return String.format("""
                {
                    "repo": "%s",
                    "collection": "app.bsky.feed.post",
                    "record": {
                        "$type": "app.bsky.feed.post",
                        "text": "%s",
                        "createdAt": "%s",
                        "langs": ["en-US"]%s
                    }
                }""", this.handle, formattedText, Instant.now().toString(), facetsJson);
    }

    private String findFacets(String text) {
        // Find OpenJDK links and create facets
        List<Facet> facets = findOpenJdkLinks(text);

        // Create facets JSON if any links were found
        String facetsJson = "";
        if (!facets.isEmpty()) {
            StringBuilder facetsBuilder = new StringBuilder();
            facetsBuilder.append(",\n        \"facets\": [");

            for (int i = 0; i < facets.size(); i++) {
                Facet facet = facets.get(i);
                facetsBuilder.append(String.format("""
                        {
                            "index": {
                                "byteStart": %d,
                                "byteEnd": %d
                            },
                            "features": [
                                {
                                    "$type": "app.bsky.richtext.facet#link",
                                    "uri": "%s"
                                }
                            ]
                        }""", facet.byteStart, facet.byteEnd, facet.uri));

                if (i < facets.size() - 1) {
                    facetsBuilder.append(",");
                }
            }

            facetsBuilder.append("]");
            facetsJson = facetsBuilder.toString();
        }
        return facetsJson;
    }

    private List<Facet> findOpenJdkLinks(String text) {
        List<Facet> facets = new ArrayList<>();
        // Pattern to match OpenJDK JEP links like "openjdk.org/jeps/123"
        Matcher matcher = OPENJDK_LINK_PATTERN.matcher(text);
        while (matcher.find()) {
            int charStart = matcher.start();
            int charEnd = matcher.end();
            String jepNumber = matcher.group(1);
            // Create a facet for this link
            Facet facet = new Facet();
            facet.byteStart = getUtf8BytePosition(text, charStart);
            facet.byteEnd = getUtf8BytePosition(text, charEnd);
            facet.uri = "https://openjdk.org/jeps/" + jepNumber;
            facets.add(facet);
            LOG.debug("Found OpenJDK link: {} at positions {}-{} (char positions {}-{})", facet.uri, facet.byteStart, facet.byteEnd, charStart, charEnd);
        }
        return facets;
    }

    /**
     * Converts a character position to a UTF-8 byte position.
     *
     * @param text         The text to analyze
     * @param charPosition The character position to convert
     * @return The corresponding UTF-8 byte position
     */
    private int getUtf8BytePosition(String text, int charPosition) {
        if (charPosition <= 0) {
            return 0;
        }
        // Get the substring up to the character position and calculate its UTF-8 byte length
        String substring = text.substring(0, charPosition);
        return substring.getBytes(UTF_8).length;
    }

    // Inner class to represent a facet
    private static class Facet {
        int byteStart;
        int byteEnd;
        String uri;
    }
}
