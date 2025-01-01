package subscriptionservice.models;

import java.util.Map;

public record CreateSubscriptionRequest(String subscriptionService,
                                        String subscriptionType,
                                        Map<String, Object> subscriptionAttributes) {
}
