# IpfsPinsApi

All URIs are relative to *https://cardano-mainnet.blockfrost.io/api/v0*

Method | HTTP request | Description
------------- | ------------- | -------------
[**ipfsPinAddIPFSPathPost**](IpfsPinsApi.md#ipfsPinAddIPFSPathPost) | **POST** /ipfs/pin/add/{IPFS_path} | Pin an object
[**ipfsPinAddIPFSPathPostWithHttpInfo**](IpfsPinsApi.md#ipfsPinAddIPFSPathPostWithHttpInfo) | **POST** /ipfs/pin/add/{IPFS_path} | Pin an object
[**ipfsPinListGet**](IpfsPinsApi.md#ipfsPinListGet) | **GET** /ipfs/pin/list/ | 
[**ipfsPinListGetWithHttpInfo**](IpfsPinsApi.md#ipfsPinListGetWithHttpInfo) | **GET** /ipfs/pin/list/ | 
[**ipfsPinListIPFSPathGet**](IpfsPinsApi.md#ipfsPinListIPFSPathGet) | **GET** /ipfs/pin/list/{IPFS_path} | 
[**ipfsPinListIPFSPathGetWithHttpInfo**](IpfsPinsApi.md#ipfsPinListIPFSPathGetWithHttpInfo) | **GET** /ipfs/pin/list/{IPFS_path} | 
[**ipfsPinRemoveIPFSPathPost**](IpfsPinsApi.md#ipfsPinRemoveIPFSPathPost) | **POST** /ipfs/pin/remove/{IPFS_path} | 
[**ipfsPinRemoveIPFSPathPostWithHttpInfo**](IpfsPinsApi.md#ipfsPinRemoveIPFSPathPostWithHttpInfo) | **POST** /ipfs/pin/remove/{IPFS_path} | 



## ipfsPinAddIPFSPathPost

> InlineResponse2004 ipfsPinAddIPFSPathPost(ipFSPath)

Pin an object

Pinned objects are counted in your user storage quota.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.IpfsPinsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        IpfsPinsApi apiInstance = new IpfsPinsApi(defaultClient);
        String ipFSPath = "ipFSPath_example"; // String | 
        try {
            InlineResponse2004 result = apiInstance.ipfsPinAddIPFSPathPost(ipFSPath);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling IpfsPinsApi#ipfsPinAddIPFSPathPost");
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
 **ipFSPath** | **String**|  |

### Return type

[**InlineResponse2004**](InlineResponse2004.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Returns pinned object |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## ipfsPinAddIPFSPathPostWithHttpInfo

> ApiResponse<InlineResponse2004> ipfsPinAddIPFSPathPost ipfsPinAddIPFSPathPostWithHttpInfo(ipFSPath)

Pin an object

Pinned objects are counted in your user storage quota.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.IpfsPinsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        IpfsPinsApi apiInstance = new IpfsPinsApi(defaultClient);
        String ipFSPath = "ipFSPath_example"; // String | 
        try {
            ApiResponse<InlineResponse2004> response = apiInstance.ipfsPinAddIPFSPathPostWithHttpInfo(ipFSPath);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling IpfsPinsApi#ipfsPinAddIPFSPathPost");
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
 **ipFSPath** | **String**|  |

### Return type

ApiResponse<[**InlineResponse2004**](InlineResponse2004.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Returns pinned object |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## ipfsPinListGet

> List<InlineResponse2005> ipfsPinListGet(count, page, order)



List objects pinned to local storage

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.IpfsPinsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        IpfsPinsApi apiInstance = new IpfsPinsApi(defaultClient);
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            List<InlineResponse2005> result = apiInstance.ipfsPinListGet(count, page, order);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling IpfsPinsApi#ipfsPinListGet");
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
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

### Return type

[**List&lt;InlineResponse2005&gt;**](InlineResponse2005.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Returns pinned objects |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## ipfsPinListGetWithHttpInfo

> ApiResponse<List<InlineResponse2005>> ipfsPinListGet ipfsPinListGetWithHttpInfo(count, page, order)



List objects pinned to local storage

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.IpfsPinsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        IpfsPinsApi apiInstance = new IpfsPinsApi(defaultClient);
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            ApiResponse<List<InlineResponse2005>> response = apiInstance.ipfsPinListGetWithHttpInfo(count, page, order);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling IpfsPinsApi#ipfsPinListGet");
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
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

### Return type

ApiResponse<[**List&lt;InlineResponse2005&gt;**](InlineResponse2005.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Returns pinned objects |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## ipfsPinListIPFSPathGet

> InlineResponse2006 ipfsPinListIPFSPathGet(ipFSPath)



List objects pinned to local storage

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.IpfsPinsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        IpfsPinsApi apiInstance = new IpfsPinsApi(defaultClient);
        String ipFSPath = "ipFSPath_example"; // String | 
        try {
            InlineResponse2006 result = apiInstance.ipfsPinListIPFSPathGet(ipFSPath);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling IpfsPinsApi#ipfsPinListIPFSPathGet");
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
 **ipFSPath** | **String**|  |

### Return type

[**InlineResponse2006**](InlineResponse2006.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Returns the pins pinned |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## ipfsPinListIPFSPathGetWithHttpInfo

> ApiResponse<InlineResponse2006> ipfsPinListIPFSPathGet ipfsPinListIPFSPathGetWithHttpInfo(ipFSPath)



List objects pinned to local storage

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.IpfsPinsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        IpfsPinsApi apiInstance = new IpfsPinsApi(defaultClient);
        String ipFSPath = "ipFSPath_example"; // String | 
        try {
            ApiResponse<InlineResponse2006> response = apiInstance.ipfsPinListIPFSPathGetWithHttpInfo(ipFSPath);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling IpfsPinsApi#ipfsPinListIPFSPathGet");
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
 **ipFSPath** | **String**|  |

### Return type

ApiResponse<[**InlineResponse2006**](InlineResponse2006.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Returns the pins pinned |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## ipfsPinRemoveIPFSPathPost

> InlineResponse2007 ipfsPinRemoveIPFSPathPost(ipFSPath)



Remove pinned objects from local storage

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.IpfsPinsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        IpfsPinsApi apiInstance = new IpfsPinsApi(defaultClient);
        String ipFSPath = "ipFSPath_example"; // String | 
        try {
            InlineResponse2007 result = apiInstance.ipfsPinRemoveIPFSPathPost(ipFSPath);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling IpfsPinsApi#ipfsPinRemoveIPFSPathPost");
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
 **ipFSPath** | **String**|  |

### Return type

[**InlineResponse2007**](InlineResponse2007.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Returns the pins removed |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## ipfsPinRemoveIPFSPathPostWithHttpInfo

> ApiResponse<InlineResponse2007> ipfsPinRemoveIPFSPathPost ipfsPinRemoveIPFSPathPostWithHttpInfo(ipFSPath)



Remove pinned objects from local storage

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.IpfsPinsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        IpfsPinsApi apiInstance = new IpfsPinsApi(defaultClient);
        String ipFSPath = "ipFSPath_example"; // String | 
        try {
            ApiResponse<InlineResponse2007> response = apiInstance.ipfsPinRemoveIPFSPathPostWithHttpInfo(ipFSPath);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling IpfsPinsApi#ipfsPinRemoveIPFSPathPost");
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
 **ipFSPath** | **String**|  |

### Return type

ApiResponse<[**InlineResponse2007**](InlineResponse2007.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Returns the pins removed |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

