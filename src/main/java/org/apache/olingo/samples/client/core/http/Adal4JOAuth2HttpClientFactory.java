package org.apache.olingo.samples.client.core.http;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.ClientParametersAuthentication;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.microsoft.aad.adal4j.AuthenticationCallback;
import com.microsoft.aad.adal4j.AuthenticationContext;
import com.microsoft.aad.adal4j.AuthenticationResult;
import com.microsoft.aad.adal4j.ClientCredential;
import org.apache.http.HttpException;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.HttpContext;
import org.apache.olingo.client.core.http.AbstractOAuth2HttpClientFactory;
import org.apache.olingo.client.core.http.OAuth2Exception;

import java.awt.*;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Scanner;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Adal4JOAuth2HttpClientFactory extends AbstractOAuth2HttpClientFactory {
    private final String authority;
    private final String clientId;
    private final String redirectURI;
    private final String resourceURI;
    private final String clientSecret;
    private final String authURL;
    private final ClientCredential clientCredentials;
    private String token;
    private AuthenticationContext context;
    private String refreshToken;
    private String authorizationCode;

    public Adal4JOAuth2HttpClientFactory(String authority, final String clientId, final String clientSecret,
                                         final String redirectURI, final String resourceURI) {
        super(null, URI.create(authority + "/oauth2/token?resource=" + resourceURI));
        this.authority = authority;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.clientCredentials = new ClientCredential(clientId, clientSecret);
        this.redirectURI = redirectURI;
        this.resourceURI = resourceURI;
        this.authURL = authority + "/oauth2/authorize?resource=" + resourceURI;
    }

    @Override
    protected boolean isInited() throws OAuth2Exception {
        return token != null;
    }

    @Override
    protected void init() throws OAuth2Exception {
        //   String authority = "https://login.microsoftonline.com/contoso.onmicrosoft.com/";
        ExecutorService service = Executors.newFixedThreadPool(1);

        final ClientParametersAuthentication clientAuthentication = new ClientParametersAuthentication(clientId, clientSecret);

        try {
            this.context = new AuthenticationContext(authority, true, service);
            final AuthorizationCodeFlow authorizationCodeFlow = new AuthorizationCodeFlow(BearerToken.authorizationHeaderAccessMethod(), new NetHttpTransport(), new JacksonFactory(), new GenericUrl(oauth2TokenServiceURI), clientAuthentication, clientId, authURL);
            AuthorizationCodeRequestUrl request = authorizationCodeFlow.newAuthorizationUrl();
            if (redirectURI != null) {
                request.setRedirectUri(redirectURI);
            }
            String accessTokenURL = request.build();
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(new URI(accessTokenURL)); // open oauth request
            }
            System.out.print("Please insert authorization code:");
            final Scanner in = new Scanner(System.in, "UTF-8");
            this.authorizationCode = in.nextLine();
        } catch (IOException | URISyntaxException e) {
            e.printStackTrace();
        }

        fetchAccessToken();
    }

    private void fetchAccessToken() {
        final Future<AuthenticationResult> yay = this.context.acquireTokenByAuthorizationCode(authorizationCode, URI.create(redirectURI), clientCredentials, resourceURI, new AuthenticationCallback() {
            @Override
            public void onSuccess(Object o) {
                System.out.println("Yay,token aquired!");
            }

            @Override
            public void onFailure(Throwable throwable) {
                throwable.printStackTrace();
            }
        });
        final AuthenticationResult authenticationResult;
        try {
            authenticationResult = yay.get();
            this.token = authenticationResult.getAccessToken();
            this.refreshToken = authenticationResult.getRefreshToken();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void accessToken(DefaultHttpClient client) throws OAuth2Exception {
        client.addRequestInterceptor((request, context) -> {
                request.removeHeaders(HttpHeaders.AUTHORIZATION);
                request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            });
    }

    @Override
    protected void refreshToken(DefaultHttpClient defaultHttpClient) throws OAuth2Exception {
        this.context.acquireTokenByRefreshToken(refreshToken, clientId, resourceURI, new AuthenticationCallback() {
            @Override
            public void onSuccess(Object o) {
                System.out.println("Sucessfully issued token by refreshtoken");
            }

            @Override
            public void onFailure(Throwable throwable) {
                throwable.printStackTrace();
            }
        });
    }
}