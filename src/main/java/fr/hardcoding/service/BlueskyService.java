package fr.hardcoding.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.client.Client;
import jakarta.ws.rs.client.ClientBuilder;
import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
public class BlueskyService {
    private static final Logger LOG = LoggerFactory.getLogger(BlueskyService.class);
    private static final String BLUESKY_API_URL = "https://bsky.social/xrpc/com.atproto.repo.createRecord";
    private static final String BLUESKY_AUTH_URL = "https://bsky.social/xrpc/com.atproto.server.createSession";
    
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
        try {
            String token = getAuthToken();
            
            Response response = client.target(BLUESKY_API_URL)
                    .request(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .post(Entity.json(createPostRequest(text)));
            
            if (response.getStatus() != Response.Status.OK.getStatusCode()) {
                LOG.error("Failed to post to Bluesky: {}", response.readEntity(String.class));
            }
        } catch (Exception e) {
            LOG.error("Error posting to Bluesky", e);
        }
    }
    
    private String getAuthToken() {
        // Check if we have a valid token
        Instant now = Instant.now();
        if (authToken.get() != null && tokenExpiry.get() != null && now.isBefore(tokenExpiry.get())) {
            return authToken.get();
        }
        
        // Create authentication request
        String authRequest = String.format("""
                {
                    "identifier": "%s",
                    "password": "%s"
                }""", handle, appPassword);
        
        try {
            Response response = client.target(BLUESKY_AUTH_URL)
                    .request(MediaType.APPLICATION_JSON)
                    .post(Entity.json(authRequest));
            
            if (response.getStatus() == Response.Status.OK.getStatusCode()) {
                String responseBody = response.readEntity(String.class);
                // Extract accessJwt from response
                String accessJwt = extractAccessJwt(responseBody);
                if (accessJwt != null) {
                    // Store token and set expiry to 24 hours from now
                    authToken.set(accessJwt);
                    tokenExpiry.set(now.plusSeconds(24 * 60 * 60));
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
        return String.format("""
                {
                    "repo": "%s",
                    "collection": "app.bsky.feed.post",
                    "record": {
                        "text": "%s",
                        "createdAt": "%s"
                    }
                }""", handle, text, Instant.now().toString());
    }
} 