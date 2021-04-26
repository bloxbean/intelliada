# CardanoLedgerApi

All URIs are relative to *https://cardano-mainnet.blockfrost.io/api/v0*

Method | HTTP request | Description
------------- | ------------- | -------------
[**genesisGet**](CardanoLedgerApi.md#genesisGet) | **GET** /genesis | Blockchain genesis
[**genesisGetWithHttpInfo**](CardanoLedgerApi.md#genesisGetWithHttpInfo) | **GET** /genesis | Blockchain genesis



## genesisGet

> GenesisContent genesisGet()

Blockchain genesis

Return the information about blockchain genesis.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoLedgerApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoLedgerApi apiInstance = new CardanoLedgerApi(defaultClient);
        try {
            GenesisContent result = apiInstance.genesisGet();
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoLedgerApi#genesisGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
```

### Parameters

This endpoint does not need any parameter.

### Return type

[**GenesisContent**](GenesisContent.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the genesis parameters. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## genesisGetWithHttpInfo

> ApiResponse<GenesisContent> genesisGet genesisGetWithHttpInfo()

Blockchain genesis

Return the information about blockchain genesis.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoLedgerApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoLedgerApi apiInstance = new CardanoLedgerApi(defaultClient);
        try {
            ApiResponse<GenesisContent> response = apiInstance.genesisGetWithHttpInfo();
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoLedgerApi#genesisGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Response headers: " + e.getResponseHeaders());
            System.err.println("Reason: " + e.getResponseBody());
            e.printStackTrace();
        }
    }
}
```

### Parameters

This endpoint does not need any parameter.

### Return type

ApiResponse<[**GenesisContent**](GenesisContent.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the genesis parameters. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

