/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.kogito.index;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.JsonNode;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;
import static java.util.Collections.singletonMap;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public abstract class AbstractProcessDataIndexIT {

    public static final String GROUP = "\"group\"";
    public static final String MANAGERS = "\"managers\"";
    public static final String PROCESS_ID = "\"processId\"";
    public static final String $_SIZE = "\"$.size\"";
    public static final String FIRST_LINE_APPROVAL = "\"firstLineApproval\"";
    public static final String NAME = "\"[0].name\";
    public static final String ACTIVE = "\"ACTIVE\"";
    public static final String GRAPHQL = "\"/graphql\"";
    public static final String ID = "\"[0].id\"";
    public static final String DATA_PROCESS_INSTANCES_SIZE = "\"data.ProcessInstances.size()\"";
    public static final String DATA_PROCESS_INSTANCES_0_ID = "\"data.ProcessInstances[0].id\"";
    public static final String DATA_PROCESS_INSTANCES_0_PROCESS_ID = "\"data.ProcessInstances[0].processId\"";
    public static final String APPROVALS = "\"approvals\"";
    public static final String DATA_PROCESS_INSTANCES_0_STATE = "\"data.ProcessInstances[0].state\"";
    public static final String TASK_ID = "\"taskId\"";
    public static final String MANAGER = "\"manager\"";
    public static final String DATA_USER_TASK_INSTANCES_0_DESCRIPTION = "\"data.UserTaskInstances[0].description\"";
    public static final String DATA_USER_TASK_INSTANCES_0_POTENTIAL_GROUPS_0 = "\"data.UserTaskInstances[0].potentialGroups[0]\"";
    public static final String ERRORS = "\"errors\"";
    public static final String NEW_DESCRIPTION = "\"NewDescription\"";
    public static final String DATA_USER_TASK_INSTANCES_0_PRIORITY = "\"data.UserTaskInstances[0].priority\"";
    public static final String DATA_USER_TASK_INSTANCES_0_COMMENTS_SIZE = "\"data.UserTaskInstances[0].comments.size()\"";
    public static final String DATA_USER_TASK_INSTANCES_0_ATTACHMENTS_SIZE = "\"data.UserTaskInstances[0].attachments.size()\"";
    public static final String ERRORS_0_MESSAGE = "\"errors[0].message\"";
    public static final String PHASES = "\"phases\"";
    public static final String STRING = "\"string\"";
    public static final String QUERY_PROCESS_INSTANCES_WHERE_ID_EQUAL = "\"{ \\\"query\\\" : \\\"{ ProcessInstances (where: { id: {equal: \\\\\\\"\"";
    public static final String QUERY_USER_TASK_INSTANCES_WHERE_PROCESS_INSTANCE_ID_EQUAL = "\"{ \\\"query\\\" : \\\"{ UserTaskInstances (where: { processInstanceId: {equal: \\\\\\\"\"";
    public static final String TASK_ID1 = "\"taskId: \\\\\\\"\"";
    public static final String USER_MANAGER = "\"user: \\\\\\\"manager\\\\\\\", \""

    private static Duration TIMEOUT = Duration.ofSeconds(30);

    static {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }

    RequestSpecification spec;

    ObjectMapper mapper = new ObjectMapper();

    public abstract String getDataIndexURL();

    public boolean validateDomainData() {
        return true;
    }

    public boolean validateGetProcessInstanceSource() {
        return false;
    }

    public RequestSpecification dataIndexSpec() {
        if (spec == null) {
            spec = new RequestSpecBuilder().setBaseUri(getDataIndexURL()).build();
        }
        return spec;
    }

    @Test
    public void testProcessInstanceEvents() throws IOException {
        String pId = given()
                .contentType(ContentType.JSON)
                .body("{\"traveller\" : {\"firstName\" : \"Darth\",\"lastName\" : \"Vader\",\"email\" : \"darth.vader@deathstar.com\",\"nationality\" : \"Tatooine\"}}")
                .when()
                .post("/approvals")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract()
                .path("id");

        String flTaskId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", "admin")
                .queryParam(GROUP, MANAGERS)
                .pathParam(PROCESS_ID, pId)
                .when()
                .get("/approvals/{processId}/tasks")
                .then()
                .statusCode(200)
                .body($_SIZE, is(1))
                .body(NAME, is(FIRST_LINE_APPROVAL))
                .body(ID, notNullValue())
                .extract()
                .path(ID);

        if (validateDomainData()) {
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"{Approvals{ id, traveller { firstName, lastName }, metadata { processInstances { id, state }, userTasks { id, name, state } } } }\" }")
                            .when().post(GRAPHQL)
                            .then().statusCode(200)
                            .body("data.Approvals.size()", is(1))
                            .body("data.Approvals[0].id", is(pId))
                            .body("data.Approvals[0].traveller.firstName", is("Darth"))
                            .body("data.Approvals[0].traveller.lastName", is("Vader"))
                            .body("data.Approvals[0].metadata.processInstances", is(notNullValue()))
                            .body("data.Approvals[0].metadata.processInstances.size()", is(1))
                            .body("data.Approvals[0].metadata.processInstances[0].id", is(pId))
                            .body("data.Approvals[0].metadata.processInstances[0].state", is(ACTIVE))
                            .body("data.Approvals[0].metadata.userTasks", is(notNullValue()))
                            .body("data.Approvals[0].metadata.userTasks.size()", is(1))
                            .body("data.Approvals[0].metadata.userTasks[0].id", is(flTaskId))
                            .body("data.Approvals[0].metadata.userTasks[0].name", is(FIRST_LINE_APPROVAL))
                            .body("data.Approvals[0].metadata.userTasks[0].state", is("Ready")));
        }

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ProcessInstances{ id, processId, state } }\" }")
                        .when().post(GRAPHQL)
                        .then().statusCode(200)
                        .body(DATA_PROCESS_INSTANCES_SIZE, is(1))
                        .body(DATA_PROCESS_INSTANCES_0_ID, is(pId))
                        .body(DATA_PROCESS_INSTANCES_0_PROCESS_ID, is(APPROVALS))
                        .body(DATA_PROCESS_INSTANCES_0_STATE, is(ACTIVE)));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{UserTaskInstances{ id, name, state } }\" }")
                        .when().post(GRAPHQL)
                        .then().statusCode(200)
                        .body("data.UserTaskInstances.size()", is(1))
                        .body("data.UserTaskInstances[0].id", is(flTaskId))
                        .body("data.UserTaskInstances[0].name", is(FIRST_LINE_APPROVAL))
                        .body("data.UserTaskInstances[0].state", is("Ready")));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .when()
                        .queryParam("user", "admin")
                        .queryParam(GROUP, MANAGERS)
                        .pathParam(PROCESS_ID, pId)
                        .pathParam(TASK_ID, flTaskId)
                        .body(singletonMap("approved", true))
                        .post("/approvals/{processId}/firstLineApproval/{taskId}")
                        .then()
                        .statusCode(200)
                        .body(FIRST_LINE_APPROVAL, is(true)));

        String slTaskId = given()
                .contentType(ContentType.JSON)
                .queryParam("user", MANAGER)
                .queryParam(GROUP, MANAGERS)
                .pathParam(PROCESS_ID, pId)
                .when()
                .get("/approvals/{processId}/tasks")
                .then()
                .statusCode(200)
                .body($_SIZE, is(1))
                .body(NAME, is("secondLineApproval"))
                .body(ID, notNullValue())
                .extract()
                .path(ID);

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .when()
                        .queryParam("user", MANAGER)
                        .queryParam(GROUP, MANAGERS)
                        .pathParam(PROCESS_ID, pId)
                        .pathParam(TASK_ID, slTaskId)
                        .body(singletonMap("approved", true))
                        .post("/approvals/{processId}/secondLineApproval/{taskId}")
                        .then()
                        .statusCode(200)
                        .body("secondLineApproval", is(true)));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given()
                        .contentType(ContentType.JSON)
                        .when()
                        .pathParam(PROCESS_ID, pId)
                        .get("/approvals/{processId}")
                        .then()
                        .statusCode(404));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{ProcessInstances{ id, processId, state } }\" }")
                        .when().post(GRAPHQL)
                        .then().statusCode(200)
                        .body(DATA_PROCESS_INSTANCES_SIZE, is(1))
                        .body(DATA_PROCESS_INSTANCES_0_ID, is(pId))
                        .body(DATA_PROCESS_INSTANCES_0_PROCESS_ID, is(APPROVALS))
                        .body(DATA_PROCESS_INSTANCES_0_STATE, is("COMPLETED")));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"{UserTaskInstances{ id, name, state } }\" }")
                        .when().post(GRAPHQL)
                        .then().statusCode(200)
                        .body("data.UserTaskInstances.size()", is(2)));

        if (validateDomainData()) {
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"{Approvals{ id, firstLineApproval, secondLineApproval, metadata { processInstances { id, state }, userTasks { id, name, state } } } }\" }")
                            .when().post(GRAPHQL)
                            .then().statusCode(200)
                            .body("data.Approvals.size()", is(1))
                            .body("data.Approvals[0].id", is(pId))
                            .body("data.Approvals[0].firstLineApproval", is(true))
                            .body("data.Approvals[0].secondLineApproval", is(true))
                            .body("data.Approvals[0].metadata.processInstances", is(notNullValue()))
                            .body("data.Approvals[0].metadata.processInstances.size()", is(1))
                            .body("data.Approvals[0].metadata.processInstances[0].id", is(pId))
                            .body("data.Approvals[0].metadata.processInstances[0].state", is("COMPLETED"))
                            .body("data.Approvals[0].metadata.userTasks", is(notNullValue()))
                            .body("data.Approvals[0].metadata.userTasks.size()", is(2)));
        }
        testProcessGatewayAPI();
    }

    public void testProcessGatewayAPI() throws IOException {
        String pId2 = createTestProcessInstance();
        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> getProcessInstanceById(pId2, ACTIVE));

        if (validateGetProcessInstanceSource()) {
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body(QUERY_PROCESS_INSTANCES_WHERE_ID_EQUAL + pId2 + "\\\"}}) { source } }\"}")
                            .when().post(GRAPHQL)
                            .then()
                            .statusCode(200)
                            .body("data.ProcessInstances[0].source", is(getTestFileContentByFilename("approval.bpmn"))));
        }

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body(QUERY_PROCESS_INSTANCES_WHERE_ID_EQUAL + pId2 + "\\\"}}) { nodeDefinitions {id} nodes {id}} }\"}")
                        .when().post(GRAPHQL)
                        .then()
                        .statusCode(200)
                        .body("data.ProcessInstances[0].nodeDefinitions", notNullValue())
                        .body("data.ProcessInstances[0].nodeDefinitions.size()", is(4))
                        .body("data.ProcessInstances[0].nodes.size()", is(2)));

        final String taskId = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body(QUERY_USER_TASK_INSTANCES_WHERE_PROCESS_INSTANCE_ID_EQUAL + pId2 + "\\\"}}) { id description potentialGroups } }\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(DATA_USER_TASK_INSTANCES_0_DESCRIPTION, nullValue())
                .body(DATA_USER_TASK_INSTANCES_0_POTENTIAL_GROUPS_0, equalTo(MANAGERS))
                .extract().path("data.UserTaskInstances[0].id");

        String taskSchema = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ UserTaskInstances (where: {id: {equal:\\\"" + taskId + "\\\" }}){ " +
                        "schema ( user: \\\"manager\\\", groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"] )" +
                        "}}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(ERRORS, nullValue())
                .extract().path("data.UserTaskInstances[0].schema");
        checkExpectedTaskSchema(taskSchema);

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"mutation{ UserTaskInstanceUpdate(" +
                                      TASK_ID1 + taskId + "\\\", " +
                                      USER_MANAGER +
                                "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                                "description: \\\"NewDescription\\\", " +
                                "priority: \\\"low\\\" " +
                                ")}\"}")
                        .when().post(GRAPHQL)
                        .then()
                        .statusCode(200)
                        .body(ERRORS, nullValue()));

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body(QUERY_USER_TASK_INSTANCES_WHERE_PROCESS_INSTANCE_ID_EQUAL + pId2 + "\\\"}}) { " +
                                "id description priority potentialGroups comments {id} attachments {id}} }\"}")
                        .when().post(GRAPHQL)
                        .then()
                        .statusCode(200)
                        .body(DATA_USER_TASK_INSTANCES_0_DESCRIPTION, equalTo(NEW_DESCRIPTION))
                        .body(DATA_USER_TASK_INSTANCES_0_PRIORITY, equalTo("low"))
                        .body(DATA_USER_TASK_INSTANCES_0_COMMENTS_SIZE, is(0))
                        .body(DATA_USER_TASK_INSTANCES_0_ATTACHMENTS_SIZE, is(0))
                        .body(DATA_USER_TASK_INSTANCES_0_POTENTIAL_GROUPS_0, equalTo(MANAGERS)));

        testProcessGatewayAPIComments(taskId, pId2);
        testProcessGatewayAPIAttachments(taskId, pId2);

        String vars = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body(QUERY_PROCESS_INSTANCES_WHERE_ID_EQUAL + pId2 + "\\\"}}) { variables} }\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200).extract().path("data.ProcessInstances[0].variables");

        if (vars != null) {
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body("{ \"query\" : \"mutation{ ProcessInstanceUpdateVariables(id:\\\"" + pId2 + "\\\", variables:\\\"" +
                                    vars.replace("Darth", "Anakin")
                                            .replace("\"", "\\\\\\\"")
                                    + "\\\")}\"}")
                            .when().post(GRAPHQL)
                            .then()
                            .statusCode(200)
                            .body(ERRORS, nullValue()));
            await()
                    .atMost(TIMEOUT)
                    .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                            .body(QUERY_PROCESS_INSTANCES_WHERE_ID_EQUAL + pId2 + "\\\"}}) { variables} }\"}")
                            .when().post(GRAPHQL)
                            .then()
                            .statusCode(200)
                            .body("data.ProcessInstances[0].variables", containsString("Anakin")));
        }
        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ NodeInstanceTrigger(id:\\\"" + pId2 + "\\\", nodeId:\\\"_8B62D3CA-5D03-4B2B-832B-126469288BB4\\\")}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(ERRORS, nullValue());

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ NodeInstanceRetrigger(id:\\\"" + pId2 + "\\\", nodeInstanceId:\\\"_8B62D3CA-5D03-4B2B-832B-126469288BB4\\\")}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(ERRORS_0_MESSAGE, containsString("FAILED: Retrigger NodeInstance"));

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ NodeInstanceCancel(id:\\\"" + pId2 + "\\\", nodeInstanceId:\\\"_8B62D3CA-5D03-4B2B-832B-126469288BB4\\\")}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(ERRORS_0_MESSAGE, notNullValue());

        await()
                .atMost(TIMEOUT).untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body("{ \"query\" : \"mutation {ProcessInstanceAbort( id: \\\"" + pId2 + "\\\")}\"}")
                        .when().post(GRAPHQL)
                        .then()
                        .statusCode(200)
                        .body(ERRORS, nullValue()));
        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> getProcessInstanceById(pId2, "ABORTED"));

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ ProcessInstanceRetry( id: \\\"" + pId2 + "\\\")}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body("data.ProcessInstanceRetry", nullValue());

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ ProcessInstanceSkip( id: \\\"" + pId2 + "\\\")}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(ERRORS_0_MESSAGE, containsString("FAILED: SKIP"));

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ UndefinedMutation( id: \\\"" + pId2 + "\\\")}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(ERRORS_0_MESSAGE, containsString("Field 'UndefinedMutation' in type 'Mutation' is undefined"));

    }

    public void testProcessGatewayAPIComments(String taskId, String processInstanceId) throws IOException {
        String commentContent = "NewTaskComment";
        String commentCreationResult = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ UserTaskInstanceCommentCreate(" +
                              TASK_ID1 + taskId + "\\\", " +
                              USER_MANAGER +
                        "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                        "comment: \\\"" + commentContent + "\\\" " +
                        ")}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(ERRORS, nullValue())
                .extract().path("data.UserTaskInstanceCommentCreate");

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .when()
                        .queryParam("user", MANAGER)
                        .queryParam(GROUP, MANAGERS)
                        .pathParam("id", processInstanceId)
                        .pathParam(TASK_ID, taskId)
                        .get("/approvals/{id}/firstLineApproval/{taskId}/comments")
                        .then()
                        .statusCode(200)
                        .body($_SIZE, is(1))
                        .body("[0].content", is(commentContent)));

        Map<String, String> commentMap = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body(QUERY_USER_TASK_INSTANCES_WHERE_PROCESS_INSTANCE_ID_EQUAL + processInstanceId + "\\\"}}) { " +
                        "id description priority potentialGroups comments {id content updatedBy updatedAt} } }\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(DATA_USER_TASK_INSTANCES_0_DESCRIPTION, equalTo(NEW_DESCRIPTION))
                .body(DATA_USER_TASK_INSTANCES_0_PRIORITY, equalTo("low"))
                .body(DATA_USER_TASK_INSTANCES_0_POTENTIAL_GROUPS_0, equalTo(MANAGERS))
                .body(DATA_USER_TASK_INSTANCES_0_COMMENTS_SIZE, is(1))
                .extract().jsonPath().getMap("data.UserTaskInstances[0].comments[0]");
        checkExpectedCreatedItemData(commentCreationResult, commentMap);
        String commentNewContent = "commentNewContent";
        String commentUpdateResult = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation { UserTaskInstanceCommentUpdate ( " +
                              USER_MANAGER +
                        "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                        "commentId:  \\\"" + commentMap.get("id") + "\\\"" +
                        "comment:  \\\"" + commentNewContent + "\\\"" +
                        ")}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(ERRORS, nullValue())
                .extract().path("data.UserTaskInstanceCommentUpdate");

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body(QUERY_USER_TASK_INSTANCES_WHERE_PROCESS_INSTANCE_ID_EQUAL + processInstanceId + "\\\"}}) { " +
                                "comments {id content updatedBy updatedAt} } }\"}")
                        .when().post(GRAPHQL)
                        .then()
                        .statusCode(200)
                        .body("data.UserTaskInstances[0].comments[0].content", equalTo(commentNewContent)));

        Map<String, String> comment2Map = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body(QUERY_USER_TASK_INSTANCES_WHERE_PROCESS_INSTANCE_ID_EQUAL + processInstanceId + "\\\"}}) { " +
                        "comments {id content updatedBy updatedAt} } }\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(DATA_USER_TASK_INSTANCES_0_COMMENTS_SIZE, is(1))
                .extract().jsonPath().getMap("data.UserTaskInstances[0].comments[0]");

        checkExpectedCreatedItemData(commentUpdateResult, comment2Map);

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation { UserTaskInstanceCommentDelete ( " +
                              USER_MANAGER +
                        "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                        "commentId:  \\\"" + commentMap.get("id") + "\\\"" +
                        ")}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(ERRORS, nullValue());

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body(QUERY_USER_TASK_INSTANCES_WHERE_PROCESS_INSTANCE_ID_EQUAL + processInstanceId + "\\\"}}) { " +
                                "comments {id} } }\"}")
                        .when().post(GRAPHQL)
                        .then()
                        .statusCode(200)
                        .body(DATA_USER_TASK_INSTANCES_0_COMMENTS_SIZE, is(0)));
    }

    public void testProcessGatewayAPIAttachments(String taskId, String processInstanceId) throws IOException {
        String attachmentName = "NewTaskAttachmentName";
        String attachmentCreationResult = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation{ UserTaskInstanceAttachmentCreate(" +
                              TASK_ID1 + taskId + "\\\", " +
                              USER_MANAGER +
                        "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                        "name: \\\"" + attachmentName + "\\\", " +
                        "uri: \\\"https://drive.google.com/file/d/1Z_Lipg2jzY9TNewTaskAttachmentUri\\\", " +
                        ")}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(ERRORS, nullValue())
                .extract().path("data.UserTaskInstanceAttachmentCreate");
        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().contentType(ContentType.JSON)
                        .when()
                        .queryParam("user", MANAGER)
                        .queryParam(GROUP, MANAGERS)
                        .pathParam("id", processInstanceId)
                        .pathParam(TASK_ID, taskId)
                        .get("/approvals/{id}/firstLineApproval/{taskId}/attachments")
                        .then()
                        .statusCode(200)
                        .body($_SIZE, is(1))
                        .body(NAME, is(attachmentName)));

        Map<String, String> attachmentMap = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body(QUERY_USER_TASK_INSTANCES_WHERE_PROCESS_INSTANCE_ID_EQUAL + processInstanceId + "\\\"}}) { " +
                        "id description priority potentialGroups attachments {id name content updatedBy updatedAt} } }\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(DATA_USER_TASK_INSTANCES_0_DESCRIPTION, equalTo(NEW_DESCRIPTION))
                .body(DATA_USER_TASK_INSTANCES_0_PRIORITY, equalTo("low"))
                .body(DATA_USER_TASK_INSTANCES_0_POTENTIAL_GROUPS_0, equalTo(MANAGERS))
                .body(DATA_USER_TASK_INSTANCES_0_ATTACHMENTS_SIZE, is(1))
                .body("data.UserTaskInstances[0].attachments[0].name", equalTo(attachmentName))
                .extract().jsonPath().getMap("data.UserTaskInstances[0].attachments[0]");

        checkExpectedCreatedItemData(attachmentCreationResult, attachmentMap);

        String updatedAttachmentName = "newAttachmentContent";
        String updatedAttachmentUri = "https://drive.google.com/file/d/1Z_Lipg2jzY9TUpdatedTaskAttachmentUri";
        String attachmentUpdateResult = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation { UserTaskInstanceAttachmentUpdate ( " +
                              USER_MANAGER +
                        "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                        "attachmentId:  \\\"" + attachmentMap.get("id") + "\\\"" +
                        "name:  \\\"" + updatedAttachmentName + "\\\"" +
                        "uri:  \\\"" + updatedAttachmentUri + "\\\"" +
                        ")}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(ERRORS, nullValue())
                .extract().path("data.UserTaskInstanceAttachmentUpdate");

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body(QUERY_USER_TASK_INSTANCES_WHERE_PROCESS_INSTANCE_ID_EQUAL + processInstanceId + "\\\"}}) { " +
                                "attachments {id name content updatedBy updatedAt} } }\"}")
                        .when().post(GRAPHQL)
                        .then()
                        .statusCode(200)
                        .body("data.UserTaskInstances[0].attachments[0].content", equalTo(updatedAttachmentUri)));

        Map<String, String> attachmentMap2 = given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body(QUERY_USER_TASK_INSTANCES_WHERE_PROCESS_INSTANCE_ID_EQUAL + processInstanceId + "\\\"}}) { " +
                        "attachments {id name content updatedBy updatedAt} } }\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(DATA_USER_TASK_INSTANCES_0_ATTACHMENTS_SIZE, is(1))
                .body("data.UserTaskInstances[0].attachments[0].name", equalTo(updatedAttachmentName))
                .extract().jsonPath().getMap("data.UserTaskInstances[0].attachments[0]");

        checkExpectedCreatedItemData(attachmentUpdateResult, attachmentMap2);

        given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"mutation { UserTaskInstanceAttachmentDelete ( " +
                              USER_MANAGER +
                        "groups: [\\\"managers\\\", \\\"users\\\", \\\"IT\\\"], " +
                        "attachmentId:  \\\"" + attachmentMap.get("id") + "\\\"" +
                        ")}\"}")
                .when().post(GRAPHQL)
                .then()
                .statusCode(200)
                .body(ERRORS, nullValue());

        await()
                .atMost(TIMEOUT)
                .untilAsserted(() -> given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                        .body(QUERY_USER_TASK_INSTANCES_WHERE_PROCESS_INSTANCE_ID_EQUAL + processInstanceId + "\\\"}}) { " +
                                "attachments {id} } }\"}")
                        .when().post(GRAPHQL)
                        .then()
                        .statusCode(200)
                        .body(DATA_USER_TASK_INSTANCES_0_ATTACHMENTS_SIZE, is(0)));
    }

    protected String createTestProcessInstance() {
        return given()
                .contentType(ContentType.JSON)
                .body("{\"traveller\" : {\"firstName\" : \"Darth\",\"lastName\" : \"Vader\",\"email\" : \"darth.vader@deathstar.com\",\"nationality\" : \"Tatooine\"}}")
                .when()
                .post("/approvals")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .extract()
                .path("id");
    }

    protected ValidatableResponse getProcessInstanceById(String processInstanceId, String state) {
        return given().spec(dataIndexSpec()).contentType(ContentType.JSON)
                .body("{ \"query\" : \"{ProcessInstances(where: {  id: {  equal : \\\"" + processInstanceId + "\\\"}}){ id, processId, state } }\" }")
                .when().post(GRAPHQL)
                .then().statusCode(200)
                .body(DATA_PROCESS_INSTANCES_SIZE, is(1))
                .body(DATA_PROCESS_INSTANCES_0_ID, is(processInstanceId))
                .body(DATA_PROCESS_INSTANCES_0_PROCESS_ID, is(APPROVALS))
                .body(DATA_PROCESS_INSTANCES_0_STATE, is(state));
    }

    private void checkExpectedCreatedItemData(String creationData, Map<String, String> resultMap) throws IOException {
        JsonNode creationJsonNode = mapper.readTree(creationData);
        assertEquals(resultMap.get("updatedBy"), creationJsonNode.at("/updatedBy").asText());
        assertEquals(ZonedDateTime.parse(resultMap.get("updatedAt")).withZoneSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS),
                ZonedDateTime.parse(creationJsonNode.at("/updatedAt").asText()).withZoneSameInstant(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
        assertEquals(resultMap.get("content"), creationJsonNode.at("/content").asText());
    }

    private void checkExpectedTaskSchema(String taskSchema) throws IOException {
        JsonNode schemaJsonNode = mapper.readTree(taskSchema);
        assertEquals("object", schemaJsonNode.at("/type").asText());

        // Check Schema phases
        assertEquals(4, schemaJsonNode.at("/phases").size());
        assertTrue(schemaJsonNode.get(PHASES).toString().contains("abort"));
        assertTrue(schemaJsonNode.get(PHASES).toString().contains("claim"));
        assertTrue(schemaJsonNode.get(PHASES).toString().contains("skip"));
        assertTrue(schemaJsonNode.get(PHASES).toString().contains("complete"));

        // Check Schema properties
        assertEquals(2, schemaJsonNode.at("/properties").size());

        assertEquals("true", schemaJsonNode.at("/properties/approved/output").asText());
        assertEquals("boolean", schemaJsonNode.at("/properties/approved/type").asText());
        assertEquals("#/$defs/Traveller", schemaJsonNode.at("/properties/traveller/$ref").asText());
        assertEquals("true", schemaJsonNode.at("/properties/traveller/input").asText());

        // Check Schema definitions
        assertEquals(2, schemaJsonNode.at("/$defs").size());

        assertEquals("object", schemaJsonNode.at("/$defs/Traveller/type").asText());
        assertEquals(6, schemaJsonNode.at("/$defs/Traveller/properties").size());
        assertEquals("#/$defs/Address", schemaJsonNode.at("/$defs/Traveller/properties/address/$ref").asText());
        assertEquals(STRING,
                     schemaJsonNode.at("/$defs/Traveller/properties/email/type").asText());
        assertEquals(STRING,
                     schemaJsonNode.at("/$defs/Traveller/properties/firstName/type").asText());
        assertEquals(STRING,
                     schemaJsonNode.at("/$defs/Traveller/properties/lastName/type").asText());
        assertEquals(STRING,
                     schemaJsonNode.at("/$defs/Traveller/properties/nationality/type").asText());
        assertEquals("boolean",
                schemaJsonNode.at("/$defs/Traveller/properties/processed/type").asText());

        assertEquals(4, schemaJsonNode.at("/$defs/Address/properties").size());
        assertEquals(STRING,
                     schemaJsonNode.at("/$defs/Address/properties/city/type").asText());
        assertEquals(STRING,
                     schemaJsonNode.at("/$defs/Address/properties/country/type").asText());
        assertEquals(STRING,
                     schemaJsonNode.at("/$defs/Address/properties/street/type").asText());
        assertEquals(STRING,
                     schemaJsonNode.at("/$defs/Address/properties/zipCode/type").asText());

    }

    public static String readFileContent(String file) throws URISyntaxException, IOException {
        Path path = Paths.get(Thread.currentThread().getContextClassLoader().getResource(file).toURI());
        return Files.readString(path);
    }

    public String getTestFileContentByFilename(String fileName) throws Exception {
        return readFileContent(fileName);
    }
}
