# CardanoEpochsApi

All URIs are relative to *https://cardano-mainnet.blockfrost.io/api/v0*

Method | HTTP request | Description
------------- | ------------- | -------------
[**epochsLatestGet**](CardanoEpochsApi.md#epochsLatestGet) | **GET** /epochs/latest | Latest epoch
[**epochsLatestGetWithHttpInfo**](CardanoEpochsApi.md#epochsLatestGetWithHttpInfo) | **GET** /epochs/latest | Latest epoch
[**epochsNumberBlocksGet**](CardanoEpochsApi.md#epochsNumberBlocksGet) | **GET** /epochs/{number}/blocks | Block distribution
[**epochsNumberBlocksGetWithHttpInfo**](CardanoEpochsApi.md#epochsNumberBlocksGetWithHttpInfo) | **GET** /epochs/{number}/blocks | Block distribution
[**epochsNumberBlocksPoolIdGet**](CardanoEpochsApi.md#epochsNumberBlocksPoolIdGet) | **GET** /epochs/{number}/blocks/{pool_id} | Block distribution
[**epochsNumberBlocksPoolIdGetWithHttpInfo**](CardanoEpochsApi.md#epochsNumberBlocksPoolIdGetWithHttpInfo) | **GET** /epochs/{number}/blocks/{pool_id} | Block distribution
[**epochsNumberGet**](CardanoEpochsApi.md#epochsNumberGet) | **GET** /epochs/{number} | Specific epoch
[**epochsNumberGetWithHttpInfo**](CardanoEpochsApi.md#epochsNumberGetWithHttpInfo) | **GET** /epochs/{number} | Specific epoch
[**epochsNumberNextGet**](CardanoEpochsApi.md#epochsNumberNextGet) | **GET** /epochs/{number}/next | Listing of next epochs
[**epochsNumberNextGetWithHttpInfo**](CardanoEpochsApi.md#epochsNumberNextGetWithHttpInfo) | **GET** /epochs/{number}/next | Listing of next epochs
[**epochsNumberParametersGet**](CardanoEpochsApi.md#epochsNumberParametersGet) | **GET** /epochs/{number}/parameters | Protocol parameters
[**epochsNumberParametersGetWithHttpInfo**](CardanoEpochsApi.md#epochsNumberParametersGetWithHttpInfo) | **GET** /epochs/{number}/parameters | Protocol parameters
[**epochsNumberPreviousGet**](CardanoEpochsApi.md#epochsNumberPreviousGet) | **GET** /epochs/{number}/previous | Listing of previous epochs
[**epochsNumberPreviousGetWithHttpInfo**](CardanoEpochsApi.md#epochsNumberPreviousGetWithHttpInfo) | **GET** /epochs/{number}/previous | Listing of previous epochs
[**epochsNumberStakesGet**](CardanoEpochsApi.md#epochsNumberStakesGet) | **GET** /epochs/{number}/stakes | Stake distribution
[**epochsNumberStakesGetWithHttpInfo**](CardanoEpochsApi.md#epochsNumberStakesGetWithHttpInfo) | **GET** /epochs/{number}/stakes | Stake distribution
[**epochsNumberStakesPoolIdGet**](CardanoEpochsApi.md#epochsNumberStakesPoolIdGet) | **GET** /epochs/{number}/stakes/{pool_id} | Stake distribution by pool
[**epochsNumberStakesPoolIdGetWithHttpInfo**](CardanoEpochsApi.md#epochsNumberStakesPoolIdGetWithHttpInfo) | **GET** /epochs/{number}/stakes/{pool_id} | Stake distribution by pool



## epochsLatestGet

> EpochContent epochsLatestGet()

Latest epoch

Return the information about the latest, therefore current, epoch.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        try {
            EpochContent result = apiInstance.epochsLatestGet();
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsLatestGet");
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

[**EpochContent**](EpochContent.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## epochsLatestGetWithHttpInfo

> ApiResponse<EpochContent> epochsLatestGet epochsLatestGetWithHttpInfo()

Latest epoch

Return the information about the latest, therefore current, epoch.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        try {
            ApiResponse<EpochContent> response = apiInstance.epochsLatestGetWithHttpInfo();
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsLatestGet");
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

ApiResponse<[**EpochContent**](EpochContent.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## epochsNumberBlocksGet

> List<String> epochsNumberBlocksGet(number, count, page, order)

Block distribution

Return the blocks minted for the epoch specified.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the epoch
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            List<String> result = apiInstance.epochsNumberBlocksGet(number, count, page, order);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberBlocksGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the epoch |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

### Return type

**List&lt;String&gt;**


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## epochsNumberBlocksGetWithHttpInfo

> ApiResponse<List<String>> epochsNumberBlocksGet epochsNumberBlocksGetWithHttpInfo(number, count, page, order)

Block distribution

Return the blocks minted for the epoch specified.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the epoch
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            ApiResponse<List<String>> response = apiInstance.epochsNumberBlocksGetWithHttpInfo(number, count, page, order);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberBlocksGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Response headers: " + e.getResponseHeaders());
            System.err.println("Reason: " + e.getResponseBody());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the epoch |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

### Return type

ApiResponse<**List&lt;String&gt;**>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## epochsNumberBlocksPoolIdGet

> List<String> epochsNumberBlocksPoolIdGet(number, poolId, count, page, order)

Block distribution

Return the block minted for the epoch specified by stake pool.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the epoch
        String poolId = "pool1pu5jlj4q9w9jlxeu370a3c9myx47md5j5m2str0naunn2q3lkdy"; // String | Stake pool ID to filter
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            List<String> result = apiInstance.epochsNumberBlocksPoolIdGet(number, poolId, count, page, order);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberBlocksPoolIdGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the epoch |
 **poolId** | **String**| Stake pool ID to filter |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

### Return type

**List&lt;String&gt;**


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## epochsNumberBlocksPoolIdGetWithHttpInfo

> ApiResponse<List<String>> epochsNumberBlocksPoolIdGet epochsNumberBlocksPoolIdGetWithHttpInfo(number, poolId, count, page, order)

Block distribution

Return the block minted for the epoch specified by stake pool.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the epoch
        String poolId = "pool1pu5jlj4q9w9jlxeu370a3c9myx47md5j5m2str0naunn2q3lkdy"; // String | Stake pool ID to filter
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            ApiResponse<List<String>> response = apiInstance.epochsNumberBlocksPoolIdGetWithHttpInfo(number, poolId, count, page, order);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberBlocksPoolIdGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Response headers: " + e.getResponseHeaders());
            System.err.println("Reason: " + e.getResponseBody());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the epoch |
 **poolId** | **String**| Stake pool ID to filter |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

### Return type

ApiResponse<**List&lt;String&gt;**>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## epochsNumberGet

> EpochContent epochsNumberGet(number)

Specific epoch

Return the content of the requested epoch.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        String number = "225"; // String | Number of the epoch
        try {
            EpochContent result = apiInstance.epochsNumberGet(number);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **String**| Number of the epoch |

### Return type

[**EpochContent**](EpochContent.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the epoch data. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## epochsNumberGetWithHttpInfo

> ApiResponse<EpochContent> epochsNumberGet epochsNumberGetWithHttpInfo(number)

Specific epoch

Return the content of the requested epoch.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        String number = "225"; // String | Number of the epoch
        try {
            ApiResponse<EpochContent> response = apiInstance.epochsNumberGetWithHttpInfo(number);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Response headers: " + e.getResponseHeaders());
            System.err.println("Reason: " + e.getResponseBody());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **String**| Number of the epoch |

### Return type

ApiResponse<[**EpochContent**](EpochContent.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the epoch data. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## epochsNumberNextGet

> List<EpochContent> epochsNumberNextGet(number, count, page)

Listing of next epochs

Return the list of epochs following a specific epoch.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the requested epoch.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        try {
            List<EpochContent> result = apiInstance.epochsNumberNextGet(number, count, page);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberNextGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the requested epoch. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]

### Return type

[**List&lt;EpochContent&gt;**](EpochContent.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## epochsNumberNextGetWithHttpInfo

> ApiResponse<List<EpochContent>> epochsNumberNextGet epochsNumberNextGetWithHttpInfo(number, count, page)

Listing of next epochs

Return the list of epochs following a specific epoch.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the requested epoch.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        try {
            ApiResponse<List<EpochContent>> response = apiInstance.epochsNumberNextGetWithHttpInfo(number, count, page);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberNextGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Response headers: " + e.getResponseHeaders());
            System.err.println("Reason: " + e.getResponseBody());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the requested epoch. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]

### Return type

ApiResponse<[**List&lt;EpochContent&gt;**](EpochContent.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## epochsNumberParametersGet

> EpochParamContent epochsNumberParametersGet(number)

Protocol parameters

Return the protocol parameters for the epoch specified.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the epoch
        try {
            EpochParamContent result = apiInstance.epochsNumberParametersGet(number);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberParametersGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the epoch |

### Return type

[**EpochParamContent**](EpochParamContent.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## epochsNumberParametersGetWithHttpInfo

> ApiResponse<EpochParamContent> epochsNumberParametersGet epochsNumberParametersGetWithHttpInfo(number)

Protocol parameters

Return the protocol parameters for the epoch specified.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the epoch
        try {
            ApiResponse<EpochParamContent> response = apiInstance.epochsNumberParametersGetWithHttpInfo(number);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberParametersGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Response headers: " + e.getResponseHeaders());
            System.err.println("Reason: " + e.getResponseBody());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the epoch |

### Return type

ApiResponse<[**EpochParamContent**](EpochParamContent.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## epochsNumberPreviousGet

> List<EpochContent> epochsNumberPreviousGet(number, count, page)

Listing of previous epochs

Return the list of epochs preceding a specific epoch.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the epoch
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results
        try {
            List<EpochContent> result = apiInstance.epochsNumberPreviousGet(number, count, page);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberPreviousGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the epoch |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results | [optional] [default to 1]

### Return type

[**List&lt;EpochContent&gt;**](EpochContent.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the epoch data |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## epochsNumberPreviousGetWithHttpInfo

> ApiResponse<List<EpochContent>> epochsNumberPreviousGet epochsNumberPreviousGetWithHttpInfo(number, count, page)

Listing of previous epochs

Return the list of epochs preceding a specific epoch.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the epoch
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results
        try {
            ApiResponse<List<EpochContent>> response = apiInstance.epochsNumberPreviousGetWithHttpInfo(number, count, page);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberPreviousGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Response headers: " + e.getResponseHeaders());
            System.err.println("Reason: " + e.getResponseBody());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the epoch |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results | [optional] [default to 1]

### Return type

ApiResponse<[**List&lt;EpochContent&gt;**](EpochContent.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the epoch data |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## epochsNumberStakesGet

> List<Object> epochsNumberStakesGet(number, count, page)

Stake distribution

Return the active stake distribution for the epoch specified.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the epoch
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        try {
            List<Object> result = apiInstance.epochsNumberStakesGet(number, count, page);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberStakesGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the epoch |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]

### Return type

**List&lt;Object&gt;**


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## epochsNumberStakesGetWithHttpInfo

> ApiResponse<List<Object>> epochsNumberStakesGet epochsNumberStakesGetWithHttpInfo(number, count, page)

Stake distribution

Return the active stake distribution for the epoch specified.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the epoch
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        try {
            ApiResponse<List<Object>> response = apiInstance.epochsNumberStakesGetWithHttpInfo(number, count, page);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberStakesGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Response headers: " + e.getResponseHeaders());
            System.err.println("Reason: " + e.getResponseBody());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the epoch |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]

### Return type

ApiResponse<**List&lt;Object&gt;**>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## epochsNumberStakesPoolIdGet

> List<Object> epochsNumberStakesPoolIdGet(number, poolId, count, page)

Stake distribution by pool

Return the active stake distribution for the epoch specified by stake pool.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the epoch
        String poolId = "pool1pu5jlj4q9w9jlxeu370a3c9myx47md5j5m2str0naunn2q3lkdy"; // String | Stake pool ID to filter
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        try {
            List<Object> result = apiInstance.epochsNumberStakesPoolIdGet(number, poolId, count, page);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberStakesPoolIdGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Reason: " + e.getResponseBody());
            System.err.println("Response headers: " + e.getResponseHeaders());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the epoch |
 **poolId** | **String**| Stake pool ID to filter |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]

### Return type

**List&lt;Object&gt;**


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## epochsNumberStakesPoolIdGetWithHttpInfo

> ApiResponse<List<Object>> epochsNumberStakesPoolIdGet epochsNumberStakesPoolIdGetWithHttpInfo(number, poolId, count, page)

Stake distribution by pool

Return the active stake distribution for the epoch specified by stake pool.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoEpochsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoEpochsApi apiInstance = new CardanoEpochsApi(defaultClient);
        Integer number = 225; // Integer | Number of the epoch
        String poolId = "pool1pu5jlj4q9w9jlxeu370a3c9myx47md5j5m2str0naunn2q3lkdy"; // String | Stake pool ID to filter
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        try {
            ApiResponse<List<Object>> response = apiInstance.epochsNumberStakesPoolIdGetWithHttpInfo(number, poolId, count, page);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoEpochsApi#epochsNumberStakesPoolIdGet");
            System.err.println("Status code: " + e.getCode());
            System.err.println("Response headers: " + e.getResponseHeaders());
            System.err.println("Reason: " + e.getResponseBody());
            e.printStackTrace();
        }
    }
}
```

### Parameters


Name | Type | Description  | Notes
------------- | ------------- | ------------- | -------------
 **number** | **Integer**| Number of the epoch |
 **poolId** | **String**| Stake pool ID to filter |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]

### Return type

ApiResponse<**List&lt;Object&gt;**>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the data about the epoch |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

