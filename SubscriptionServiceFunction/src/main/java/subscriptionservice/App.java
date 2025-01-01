package subscriptionservice;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.google.gson.Gson;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import subscriptionservice.models.CreateSubscriptionRequest;
import subscriptionservice.models.CreateSubscriptionResponse;

import java.util.Map;
import java.util.UUID;

/**
 * Handler for requests to Lambda function.
 */
public class App implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

    private static final String CREATE_SUBSCRIPTION_PATH = "POST /subscriptions";

    private final String tableName;
    private final DynamoDbClient dynamoDbClient;
    private final Gson gson;

    public App() {
        this.tableName = System.getenv("TABLE_NAME");
        this.dynamoDbClient = DynamoDbClient.create();
        this.gson = new Gson();
    }

    @Override
    public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent event, Context context) {
        String path = event.getRouteKey();

        return switch (path) {
            case CREATE_SUBSCRIPTION_PATH -> createSubscription(event);
            default -> APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.NOT_FOUND)
                    .withBody("Path Not Found")
                    .build();
        };
    }

    private APIGatewayV2HTTPResponse createSubscription(APIGatewayV2HTTPEvent event) {
        try {

            final var claims = event.getRequestContext()
                    .getAuthorizer()
                    .getJwt()
                    .getClaims();

            CreateSubscriptionRequest request = gson.fromJson(event.getBody(), CreateSubscriptionRequest.class);

            String subscriptionId = UUID.randomUUID().toString();
            final var item = Map.of(
                    "id", AttributeValue.builder().s(subscriptionId).build(),
                    "userId", AttributeValue.builder().s(claims.get("sub")).build(),
                    "subscriptionService", AttributeValue.builder().s(request.subscriptionService()).build(),
                    "subscriptionType", AttributeValue.builder().s(request.subscriptionType()).build(),
                    "subscriptionAttributes", AttributeValue.builder().s(gson.toJson(request.subscriptionAttributes())).build()
            );

            PutItemRequest putItemRequest = PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build();

            PutItemResponse putItemResponse = dynamoDbClient.putItem(putItemRequest);
            if (!putItemResponse.sdkHttpResponse().isSuccessful()) {
                throw new RuntimeException("Failed to put item in DynamoDB");
            }

            CreateSubscriptionResponse response = new CreateSubscriptionResponse(subscriptionId);

            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.CREATED)
                    .withBody(gson.toJson(response))
                    .build();

        } catch (Exception e) {
            return APIGatewayV2HTTPResponse.builder()
                    .withStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR)
                    .withBody("Error creating subscription: " + e.getMessage())
                    .build();
        }
    }
}
