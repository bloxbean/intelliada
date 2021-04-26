# CardanoBlocksApi

All URIs are relative to *https://cardano-mainnet.blockfrost.io/api/v0*

Method | HTTP request | Description
------------- | ------------- | -------------
[**blocksHashOrNumberGet**](CardanoBlocksApi.md#blocksHashOrNumberGet) | **GET** /blocks/{hash_or_number} | Specific block
[**blocksHashOrNumberGetWithHttpInfo**](CardanoBlocksApi.md#blocksHashOrNumberGetWithHttpInfo) | **GET** /blocks/{hash_or_number} | Specific block
[**blocksHashOrNumberNextGet**](CardanoBlocksApi.md#blocksHashOrNumberNextGet) | **GET** /blocks/{hash_or_number}/next | Listing of next blocks
[**blocksHashOrNumberNextGetWithHttpInfo**](CardanoBlocksApi.md#blocksHashOrNumberNextGetWithHttpInfo) | **GET** /blocks/{hash_or_number}/next | Listing of next blocks
[**blocksHashOrNumberPreviousGet**](CardanoBlocksApi.md#blocksHashOrNumberPreviousGet) | **GET** /blocks/{hash_or_number}/previous | Listing of previous blocks
[**blocksHashOrNumberPreviousGetWithHttpInfo**](CardanoBlocksApi.md#blocksHashOrNumberPreviousGetWithHttpInfo) | **GET** /blocks/{hash_or_number}/previous | Listing of previous blocks
[**blocksHashOrNumberTxsGet**](CardanoBlocksApi.md#blocksHashOrNumberTxsGet) | **GET** /blocks/{hash_or_number}/txs | Block transactions
[**blocksHashOrNumberTxsGetWithHttpInfo**](CardanoBlocksApi.md#blocksHashOrNumberTxsGetWithHttpInfo) | **GET** /blocks/{hash_or_number}/txs | Block transactions
[**blocksLatestGet**](CardanoBlocksApi.md#blocksLatestGet) | **GET** /blocks/latest | Latest block
[**blocksLatestGetWithHttpInfo**](CardanoBlocksApi.md#blocksLatestGetWithHttpInfo) | **GET** /blocks/latest | Latest block



## blocksHashOrNumberGet

> BlockContent blocksHashOrNumberGet(hashOrNumber)

Specific block

Return the content of a requested block. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoBlocksApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoBlocksApi apiInstance = new CardanoBlocksApi(defaultClient);
        String hashOrNumber = "4ea1ba291e8eef538635a53e59fddba7810d1679631cc3aed7c8e6c4091a516a"; // String | Hash of the requested block.
        try {
            BlockContent result = apiInstance.blocksHashOrNumberGet(hashOrNumber);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoBlocksApi#blocksHashOrNumberGet");
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
 **hashOrNumber** | **String**| Hash of the requested block. |

### Return type

[**BlockContent**](BlockContent.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the contents of the block |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## blocksHashOrNumberGetWithHttpInfo

> ApiResponse<BlockContent> blocksHashOrNumberGet blocksHashOrNumberGetWithHttpInfo(hashOrNumber)

Specific block

Return the content of a requested block. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoBlocksApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoBlocksApi apiInstance = new CardanoBlocksApi(defaultClient);
        String hashOrNumber = "4ea1ba291e8eef538635a53e59fddba7810d1679631cc3aed7c8e6c4091a516a"; // String | Hash of the requested block.
        try {
            ApiResponse<BlockContent> response = apiInstance.blocksHashOrNumberGetWithHttpInfo(hashOrNumber);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoBlocksApi#blocksHashOrNumberGet");
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
 **hashOrNumber** | **String**| Hash of the requested block. |

### Return type

ApiResponse<[**BlockContent**](BlockContent.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the contents of the block |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## blocksHashOrNumberNextGet

> List<BlockContent> blocksHashOrNumberNextGet(hashOrNumber, count, page)

Listing of next blocks

Return the list of blocks following a specific block. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoBlocksApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoBlocksApi apiInstance = new CardanoBlocksApi(defaultClient);
        String hashOrNumber = "5ea1ba291e8eef538635a53e59fddba7810d1679631cc3aed7c8e6c4091a516a"; // String | Hash of the requested block.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        try {
            List<BlockContent> result = apiInstance.blocksHashOrNumberNextGet(hashOrNumber, count, page);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoBlocksApi#blocksHashOrNumberNextGet");
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
 **hashOrNumber** | **String**| Hash of the requested block. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]

### Return type

[**List&lt;BlockContent&gt;**](BlockContent.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the contents of the block. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## blocksHashOrNumberNextGetWithHttpInfo

> ApiResponse<List<BlockContent>> blocksHashOrNumberNextGet blocksHashOrNumberNextGetWithHttpInfo(hashOrNumber, count, page)

Listing of next blocks

Return the list of blocks following a specific block. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoBlocksApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoBlocksApi apiInstance = new CardanoBlocksApi(defaultClient);
        String hashOrNumber = "5ea1ba291e8eef538635a53e59fddba7810d1679631cc3aed7c8e6c4091a516a"; // String | Hash of the requested block.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        try {
            ApiResponse<List<BlockContent>> response = apiInstance.blocksHashOrNumberNextGetWithHttpInfo(hashOrNumber, count, page);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoBlocksApi#blocksHashOrNumberNextGet");
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
 **hashOrNumber** | **String**| Hash of the requested block. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]

### Return type

ApiResponse<[**List&lt;BlockContent&gt;**](BlockContent.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the contents of the block. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## blocksHashOrNumberPreviousGet

> List<BlockContent> blocksHashOrNumberPreviousGet(hashOrNumber, count, page)

Listing of previous blocks

Return the list of blocks preceding a specific block. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoBlocksApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoBlocksApi apiInstance = new CardanoBlocksApi(defaultClient);
        String hashOrNumber = "4873401"; // String | Hash of the requested block
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        try {
            List<BlockContent> result = apiInstance.blocksHashOrNumberPreviousGet(hashOrNumber, count, page);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoBlocksApi#blocksHashOrNumberPreviousGet");
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
 **hashOrNumber** | **String**| Hash of the requested block |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]

### Return type

[**List&lt;BlockContent&gt;**](BlockContent.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the contents of the block |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## blocksHashOrNumberPreviousGetWithHttpInfo

> ApiResponse<List<BlockContent>> blocksHashOrNumberPreviousGet blocksHashOrNumberPreviousGetWithHttpInfo(hashOrNumber, count, page)

Listing of previous blocks

Return the list of blocks preceding a specific block. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoBlocksApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoBlocksApi apiInstance = new CardanoBlocksApi(defaultClient);
        String hashOrNumber = "4873401"; // String | Hash of the requested block
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        try {
            ApiResponse<List<BlockContent>> response = apiInstance.blocksHashOrNumberPreviousGetWithHttpInfo(hashOrNumber, count, page);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoBlocksApi#blocksHashOrNumberPreviousGet");
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
 **hashOrNumber** | **String**| Hash of the requested block |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]

### Return type

ApiResponse<[**List&lt;BlockContent&gt;**](BlockContent.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the contents of the block |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## blocksHashOrNumberTxsGet

> List<String> blocksHashOrNumberTxsGet(hashOrNumber, count, page, order)

Block transactions

Return the transactions within the block.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoBlocksApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoBlocksApi apiInstance = new CardanoBlocksApi(defaultClient);
        String hashOrNumber = "4873401"; // String | Hash of the requested block.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | Ordered by tx index in the block. The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            List<String> result = apiInstance.blocksHashOrNumberTxsGet(hashOrNumber, count, page, order);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoBlocksApi#blocksHashOrNumberTxsGet");
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
 **hashOrNumber** | **String**| Hash of the requested block. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| Ordered by tx index in the block. The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

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
| **200** | Return the transactions within the block. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## blocksHashOrNumberTxsGetWithHttpInfo

> ApiResponse<List<String>> blocksHashOrNumberTxsGet blocksHashOrNumberTxsGetWithHttpInfo(hashOrNumber, count, page, order)

Block transactions

Return the transactions within the block.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoBlocksApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoBlocksApi apiInstance = new CardanoBlocksApi(defaultClient);
        String hashOrNumber = "4873401"; // String | Hash of the requested block.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | Ordered by tx index in the block. The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            ApiResponse<List<String>> response = apiInstance.blocksHashOrNumberTxsGetWithHttpInfo(hashOrNumber, count, page, order);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoBlocksApi#blocksHashOrNumberTxsGet");
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
 **hashOrNumber** | **String**| Hash of the requested block. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| Ordered by tx index in the block. The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

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
| **200** | Return the transactions within the block. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## blocksLatestGet

> BlockContent blocksLatestGet()

Latest block

Return the latest block available to the backends, also known as the tip of the blockchain. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoBlocksApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoBlocksApi apiInstance = new CardanoBlocksApi(defaultClient);
        try {
            BlockContent result = apiInstance.blocksLatestGet();
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoBlocksApi#blocksLatestGet");
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

[**BlockContent**](BlockContent.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the contents of the latest block. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## blocksLatestGetWithHttpInfo

> ApiResponse<BlockContent> blocksLatestGet blocksLatestGetWithHttpInfo()

Latest block

Return the latest block available to the backends, also known as the tip of the blockchain. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoBlocksApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoBlocksApi apiInstance = new CardanoBlocksApi(defaultClient);
        try {
            ApiResponse<BlockContent> response = apiInstance.blocksLatestGetWithHttpInfo();
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoBlocksApi#blocksLatestGet");
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

ApiResponse<[**BlockContent**](BlockContent.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the contents of the latest block. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

