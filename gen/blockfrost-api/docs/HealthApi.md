# HealthApi

All URIs are relative to *https://cardano-mainnet.blockfrost.io/api/v0*

Method | HTTP request | Description
------------- | ------------- | -------------
[**healthClockGet**](HealthApi.md#healthClockGet) | **GET** /health/clock | Current backend time
[**healthClockGetWithHttpInfo**](HealthApi.md#healthClockGetWithHttpInfo) | **GET** /health/clock | Current backend time
[**healthGet**](HealthApi.md#healthGet) | **GET** /health | Backend health status
[**healthGetWithHttpInfo**](HealthApi.md#healthGetWithHttpInfo) | **GET** /health | Backend health status
[**rootGet**](HealthApi.md#rootGet) | **GET** / | Root endpoint
[**rootGetWithHttpInfo**](HealthApi.md#rootGetWithHttpInfo) | **GET** / | Root endpoint



## healthClockGet

> InlineResponse2002 healthClockGet()

Current backend time

This endpoint provides the current UNIX time. Your application might use this to verify if the client clock is not out of sync. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.HealthApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");

        HealthApi apiInstance = new HealthApi(defaultClient);
        try {
            InlineResponse2002 result = apiInstance.healthClockGet();
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling HealthApi#healthClockGet");
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

[**InlineResponse2002**](InlineResponse2002.md)


### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the current UNIX time in milliseconds. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## healthClockGetWithHttpInfo

> ApiResponse<InlineResponse2002> healthClockGet healthClockGetWithHttpInfo()

Current backend time

This endpoint provides the current UNIX time. Your application might use this to verify if the client clock is not out of sync. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.HealthApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");

        HealthApi apiInstance = new HealthApi(defaultClient);
        try {
            ApiResponse<InlineResponse2002> response = apiInstance.healthClockGetWithHttpInfo();
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling HealthApi#healthClockGet");
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

ApiResponse<[**InlineResponse2002**](InlineResponse2002.md)>


### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the current UNIX time in milliseconds. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## healthGet

> InlineResponse2001 healthGet()

Backend health status

Return backend status as a boolean. Your application     should handle situations when backend for the given chain is unavailable. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.HealthApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");

        HealthApi apiInstance = new HealthApi(defaultClient);
        try {
            InlineResponse2001 result = apiInstance.healthGet();
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling HealthApi#healthGet");
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

[**InlineResponse2001**](InlineResponse2001.md)


### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the boolean indicating the health of the backend. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## healthGetWithHttpInfo

> ApiResponse<InlineResponse2001> healthGet healthGetWithHttpInfo()

Backend health status

Return backend status as a boolean. Your application     should handle situations when backend for the given chain is unavailable. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.HealthApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");

        HealthApi apiInstance = new HealthApi(defaultClient);
        try {
            ApiResponse<InlineResponse2001> response = apiInstance.healthGetWithHttpInfo();
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling HealthApi#healthGet");
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

ApiResponse<[**InlineResponse2001**](InlineResponse2001.md)>


### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the boolean indicating the health of the backend. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## rootGet

> InlineResponse200 rootGet()

Root endpoint

Root endpoint has no other function than to point end users to documentation. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.HealthApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");

        HealthApi apiInstance = new HealthApi(defaultClient);
        try {
            InlineResponse200 result = apiInstance.rootGet();
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling HealthApi#rootGet");
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

[**InlineResponse200**](InlineResponse200.md)


### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Information pointing to the documentation. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## rootGetWithHttpInfo

> ApiResponse<InlineResponse200> rootGet rootGetWithHttpInfo()

Root endpoint

Root endpoint has no other function than to point end users to documentation. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.HealthApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");

        HealthApi apiInstance = new HealthApi(defaultClient);
        try {
            ApiResponse<InlineResponse200> response = apiInstance.rootGetWithHttpInfo();
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling HealthApi#rootGet");
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

ApiResponse<[**InlineResponse200**](InlineResponse200.md)>


### Authorization

No authorization required

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Information pointing to the documentation. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

