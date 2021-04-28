package com.bloxbean.intelliada.idea.nodeint.service.blockfrost;

import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import okhttp3.HttpUrl;

public class BFConnectionFactory {
    private String apiUrl;
    private String projectId;

    public BFConnectionFactory(String apiUrl, String projectId) {
        this.apiUrl = apiUrl;
        this.projectId = projectId;
    }

    public ApiClient connect() {
        HttpUrl url = HttpUrl.parse(apiUrl);
        ApiClient apiClient = new ApiClient();

        apiClient.setHost(url.host());
        apiClient.setBasePath(url.encodedPath());
        apiClient.setPort(url.port());
        apiClient.setScheme(url.scheme());
        apiClient.setRequestInterceptor(reqBuilder -> reqBuilder.setHeader("project_id", projectId));

        return apiClient;
    }
}
