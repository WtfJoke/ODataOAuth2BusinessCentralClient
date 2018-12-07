/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 ******************************************************************************/
package org.apache.olingo.samples.client;

import org.apache.olingo.client.api.ODataClient;
import org.apache.olingo.client.api.communication.request.cud.ODataDeleteRequest;
import org.apache.olingo.client.api.communication.request.cud.ODataEntityCreateRequest;
import org.apache.olingo.client.api.communication.request.cud.ODataEntityUpdateRequest;
import org.apache.olingo.client.api.communication.request.cud.UpdateType;
import org.apache.olingo.client.api.communication.request.retrieve.EdmMetadataRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntityRequest;
import org.apache.olingo.client.api.communication.request.retrieve.ODataEntitySetIteratorRequest;
import org.apache.olingo.client.api.communication.response.ODataDeleteResponse;
import org.apache.olingo.client.api.communication.response.ODataEntityCreateResponse;
import org.apache.olingo.client.api.communication.response.ODataEntityUpdateResponse;
import org.apache.olingo.client.api.communication.response.ODataRetrieveResponse;
import org.apache.olingo.client.api.domain.*;
import org.apache.olingo.client.api.serialization.ODataDeserializerException;
import org.apache.olingo.client.core.ODataClientFactory;
import org.apache.olingo.commons.api.edm.*;
import org.apache.olingo.commons.api.format.ContentType;
import org.apache.olingo.samples.client.core.http.Adal4JOAuth2HttpClientFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;


/**
 *
 */
public class OlingoSampleApp {
    private ODataClient client;
    private String defaultCompany = "companies(d6a06f5d-0d04-473a-9edb-b79a792d84aa)";

    public OlingoSampleApp() {
        client = ODataClientFactory.getClient();
        Properties properties = loadProperties();
        client.getConfiguration()
                .setHttpClientFactory(new Adal4JOAuth2HttpClientFactory(properties.getProperty("authority"), properties.getProperty("clientId"), properties.getProperty("clientSecret"), properties.getProperty("redirectURI"), properties.getProperty("resourceURL")));
    }

    public static void main(String[] params) throws Exception {
        OlingoSampleApp app = new OlingoSampleApp();
        app.perform("https://api.businesscentral.dynamics.com/v1.0/api/beta");
    }

    private Properties loadProperties() {
        Properties properties = new Properties();
        try {
            final InputStream credentialsInputStream = getClass().getClassLoader().getResourceAsStream("credentials.properties");
            if (credentialsInputStream == null) {
                throw new NoSuchFileException("credentials.properties", null, "Please create credentials.properties and/or check your Ressource-Patterns in your IDE and allow *.properties");
            } else {
                properties.load(credentialsInputStream);
            }
        } catch (IOException e) {
            System.err.println("Error while loading credentials.properties: " + e.getMessage());
            e.printStackTrace();
        }
        return properties;
    }

    void perform(String serviceUrl) throws Exception {

        print("\n----- Read Edm ------------------------------");
        Edm edm = readEdm(serviceUrl);
        List<FullQualifiedName> ctFqns = new ArrayList<FullQualifiedName>();
        List<FullQualifiedName> etFqns = new ArrayList<FullQualifiedName>();
        List<String> actions = new ArrayList<>();

        for (EdmSchema schema : edm.getSchemas()) {
            for (EdmComplexType complexType : schema.getComplexTypes()) {
                ctFqns.add(complexType.getFullQualifiedName());
            }
            for (EdmEntityType entityType : schema.getEntityTypes()) {
                etFqns.add(entityType.getFullQualifiedName());
            }

            actions = schema.getActions().stream()
                    .map(action -> String.join(" ", action.getName(), String.valueOf(action.isBound()), "with", action.getBindingParameterTypeFqn().toString()))
                    .collect(Collectors.toList());
        }
        print("Found ComplexTypes", ctFqns);
        print("Found EntityTypes", etFqns);
        print("Found Actions", actions);

        print("\n----- Inspect each property and its type of the first entity: " + etFqns.get(0) + "----");
        EdmEntityType etype = edm.getEntityType(etFqns.get(0));
        for (String propertyName : etype.getPropertyNames()) {
            EdmProperty property = etype.getStructuralProperty(propertyName);
            FullQualifiedName typeName = property.getType().getFullQualifiedName();
            print("property '" + propertyName + "' " + typeName);
        }

        print("\n----- Read Entities ------------------------------");
        ClientEntitySetIterator<ClientEntitySet, ClientEntity> iterator =
                readEntities(edm, serviceUrl, "items");

        while (iterator.hasNext()) {
            ClientEntity ce = iterator.next();
            print("Entry:\n" + prettyPrint(ce.getProperties(), 0));
        }

        print("\n----- Read Entry ------------------------------");
        final UUID id = UUID.fromString("d84e0a58-f49d-4b38-a567-038baa924c49");
        ClientEntity entry = readEntityWithKey(edm, serviceUrl, "items", id);
        print("Single Entry:\n" + prettyPrint(entry.getProperties(), 0));

        //
//    print("\n----- Read Entity with $expand  ------------------------------");
//    entry = readEntityWithKeyExpand(edm, serviceUrl, "items", id, "baseUnitOfMeasure");
//    print("Single Entry with expanded baseUnitOfMeasure relation:\n" + prettyPrint(entry.getProperties(), 0));

        //
        print("\n----- Read Entities with $filter  ------------------------------");
        iterator = readEntitiesWithFilter(edm, serviceUrl, "items", "displayName eq 'ATHENS Schubladenelement'");
        while (iterator.hasNext()) {
            ClientEntity ce = iterator.next();
            print("Entry:\n" + prettyPrint(ce.getProperties(), 0));
        }

        // skip everything as odata4 sample/server only supporting retrieval
//    print("\n----- Create Entry ------------------------------");
//    ClientEntity ce = loadEntity("/mymanufacturer.json");
//    entry = createEntity(edm, serviceUrl, "Manufacturers", ce);

//    print("\n----- Update Entry ------------------------------");
//    ce = loadEntity("/mymanufacturer2.json");
//    int sc = updateEntity(edm, serviceUrl, "Manufacturers", 123, ce);
//    print("Updated successfully: " + sc);
//    entry = readEntityWithKey(edm, serviceUrl, "Manufacturers", 123);
//    print("Updated Entry successfully: " + prettyPrint(entry.getProperties(), 0));
//
//    //
//    print("\n----- Delete Entry ------------------------------");
//    sc = deleteEntity(serviceUrl, "Manufacturers", 123);
//    print("Deletion of Entry was successfully: " + sc);
//
//    try {
//      print("\n----- Verify Delete Entry ------------------------------");
//      readEntityWithKey(edm, serviceUrl, "Manufacturers", 123);
//    } catch(Exception e) {
//      print(e.getMessage());
//    }
    }

    private static void print(String content) {
        System.out.println(content);
    }

    private static void print(String content, List<?> list) {
        System.out.println(content);
        for (Object o : list) {
            System.out.println("    " + o);
        }
        System.out.println();
    }

    private static String prettyPrint(Map<String, Object> properties, int level) {
        StringBuilder b = new StringBuilder();
        Set<Entry<String, Object>> entries = properties.entrySet();

        for (Entry<String, Object> entry : entries) {
            intend(b, level);
            b.append(entry.getKey()).append(": ");
            Object value = entry.getValue();
            if (value instanceof Map) {
                value = prettyPrint((Map<String, Object>) value, level + 1);
            } else if (value instanceof Calendar) {
                Calendar cal = (Calendar) value;
                value = SimpleDateFormat.getInstance().format(cal.getTime());
            }
            b.append(value).append("\n");
        }
        // remove last line break
        b.deleteCharAt(b.length() - 1);
        return b.toString();
    }

    private static String prettyPrint(Collection<ClientProperty> properties, int level) {
        StringBuilder b = new StringBuilder();

        for (ClientProperty entry : properties) {
            intend(b, level);
            ClientValue value = entry.getValue();
            if (value.isCollection()) {
                ClientCollectionValue cclvalue = value.asCollection();
                b.append(prettyPrint(cclvalue.asJavaCollection(), level + 1));
            } else if (value.isComplex()) {
                ClientComplexValue cpxvalue = value.asComplex();
                b.append(prettyPrint(cpxvalue.asJavaMap(), level + 1));
            } else if (value.isEnum()) {
                ClientEnumValue cnmvalue = value.asEnum();
                b.append(entry.getName()).append(": ");
                b.append(cnmvalue.getValue()).append("\n");
            } else if (value.isPrimitive()) {
                b.append(entry.getName()).append(": ");
                b.append(entry.getValue()).append("\n");
            }
        }
        return b.toString();
    }

    private static void intend(StringBuilder builder, int intendLevel) {
        for (int i = 0; i < intendLevel; i++) {
            builder.append("  ");
        }
    }

    public Edm readEdm(String serviceUrl) throws IOException {
        EdmMetadataRequest request = client.getRetrieveRequestFactory().getMetadataRequest(serviceUrl);
        ODataRetrieveResponse<Edm> response = request.execute();
        return response.getBody();
    }

    public ClientEntitySetIterator<ClientEntitySet, ClientEntity> readEntities(Edm edm, String serviceUri,
                                                                               String entitySetName) {
        URI absoluteUri = client.newURIBuilder(serviceUri).appendEntitySetSegment(defaultCompany).appendEntitySetSegment(entitySetName).build();
        return readEntities(edm, absoluteUri);
    }

    public ClientEntitySetIterator<ClientEntitySet, ClientEntity> readEntitiesWithFilter(Edm edm, String serviceUri,
                                                                                         String entitySetName, String filterName) {
        URI absoluteUri = client.newURIBuilder(serviceUri).appendEntitySetSegment(defaultCompany).appendEntitySetSegment(entitySetName).filter(filterName).build();
        return readEntities(edm, absoluteUri);
    }

    private ClientEntitySetIterator<ClientEntitySet, ClientEntity> readEntities(Edm edm, URI absoluteUri) {
        System.out.println("URI = " + absoluteUri);
        ODataEntitySetIteratorRequest<ClientEntitySet, ClientEntity> request =
                client.getRetrieveRequestFactory().getEntitySetIteratorRequest(absoluteUri);
        // odata4 sample/server limitation not handling metadata=full
        request.setAccept("application/json;odata.metadata=minimal");
        ODataRetrieveResponse<ClientEntitySetIterator<ClientEntitySet, ClientEntity>> response = request.execute();

        return response.getBody();
    }

    public ClientEntity readEntityWithKey(Edm edm, String serviceUri, String entitySetName, Object keyValue) {
        URI absoluteUri = client.newURIBuilder(serviceUri).appendEntitySetSegment(defaultCompany).appendEntitySetSegment(entitySetName)
                .appendKeySegment(keyValue).build();
        return readEntity(edm, absoluteUri);
    }

    public ClientEntity readEntityWithKeyExpand(Edm edm, String serviceUri, String entitySetName, Object keyValue,
                                                String expandRelationName) {
        URI absoluteUri = client.newURIBuilder(serviceUri).appendEntitySetSegment(defaultCompany).appendEntitySetSegment(entitySetName).appendKeySegment(keyValue)
                .expand(expandRelationName).build();
        return readEntity(edm, absoluteUri);
    }

    private ClientEntity readEntity(Edm edm, URI absoluteUri) {
        ODataEntityRequest<ClientEntity> request = client.getRetrieveRequestFactory().getEntityRequest(absoluteUri);
        // odata4 sample/server limitation not handling metadata=full
        request.setAccept("application/json;odata.metadata=minimal");
        ODataRetrieveResponse<ClientEntity> response = request.execute();

        return response.getBody();
    }

    private ClientEntity loadEntity(String path) throws ODataDeserializerException {
        InputStream input = getClass().getResourceAsStream(path);
        return client.getBinder().getODataEntity(client.getDeserializer(ContentType.APPLICATION_JSON).toEntity(input));
    }

    public ClientEntity createEntity(Edm edm, String serviceUri, String entitySetName, ClientEntity ce) {
        URI absoluteUri = client.newURIBuilder(serviceUri).appendEntitySetSegment(entitySetName).build();
        return createEntity(edm, absoluteUri, ce);
    }

    private ClientEntity createEntity(Edm edm, URI absoluteUri, ClientEntity ce) {
        ODataEntityCreateRequest<ClientEntity> request = client.getCUDRequestFactory()
                .getEntityCreateRequest(absoluteUri, ce);
        // odata4 sample/server limitation not handling metadata=full
        request.setAccept("application/json;odata.metadata=minimal");
        ODataEntityCreateResponse<ClientEntity> response = request.execute();

        return response.getBody();
    }

    public int updateEntity(Edm edm, String serviceUri, String entityName, Object keyValue, ClientEntity ce) {
        URI absoluteUri = client.newURIBuilder(serviceUri).appendEntitySetSegment(entityName)
                .appendKeySegment(keyValue).build();
        ODataEntityUpdateRequest<ClientEntity> request =
                client.getCUDRequestFactory().getEntityUpdateRequest(absoluteUri, UpdateType.PATCH, ce);
        // odata4 sample/server limitation not handling metadata=full
        request.setAccept("application/json;odata.metadata=minimal");
        ODataEntityUpdateResponse<ClientEntity> response = request.execute();
        return response.getStatusCode();
    }

    public int deleteEntity(String serviceUri, String entityName, Object keyValue) throws IOException {
        URI absoluteUri = client.newURIBuilder(serviceUri).appendEntitySetSegment(entityName)
                .appendKeySegment(keyValue).build();
        ODataDeleteRequest request = client.getCUDRequestFactory().getDeleteRequest(absoluteUri);
        // odata4 sample/server limitation not handling metadata=full
        request.setAccept("application/json;odata.metadata=minimal");
        ODataDeleteResponse response = request.execute();
        return response.getStatusCode();
    }
}
