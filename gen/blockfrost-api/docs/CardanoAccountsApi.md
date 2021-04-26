# CardanoAccountsApi

All URIs are relative to *https://cardano-mainnet.blockfrost.io/api/v0*

Method | HTTP request | Description
------------- | ------------- | -------------
[**accountsStakeAddressAddressesGet**](CardanoAccountsApi.md#accountsStakeAddressAddressesGet) | **GET** /accounts/{stake_address}/addresses | Account associated addresses
[**accountsStakeAddressAddressesGetWithHttpInfo**](CardanoAccountsApi.md#accountsStakeAddressAddressesGetWithHttpInfo) | **GET** /accounts/{stake_address}/addresses | Account associated addresses
[**accountsStakeAddressDelegationsGet**](CardanoAccountsApi.md#accountsStakeAddressDelegationsGet) | **GET** /accounts/{stake_address}/delegations | Account delegation history
[**accountsStakeAddressDelegationsGetWithHttpInfo**](CardanoAccountsApi.md#accountsStakeAddressDelegationsGetWithHttpInfo) | **GET** /accounts/{stake_address}/delegations | Account delegation history
[**accountsStakeAddressGet**](CardanoAccountsApi.md#accountsStakeAddressGet) | **GET** /accounts/{stake_address} | Specific account address
[**accountsStakeAddressGetWithHttpInfo**](CardanoAccountsApi.md#accountsStakeAddressGetWithHttpInfo) | **GET** /accounts/{stake_address} | Specific account address
[**accountsStakeAddressHistoryGet**](CardanoAccountsApi.md#accountsStakeAddressHistoryGet) | **GET** /accounts/{stake_address}/history | Account history
[**accountsStakeAddressHistoryGetWithHttpInfo**](CardanoAccountsApi.md#accountsStakeAddressHistoryGetWithHttpInfo) | **GET** /accounts/{stake_address}/history | Account history
[**accountsStakeAddressRegistrationsGet**](CardanoAccountsApi.md#accountsStakeAddressRegistrationsGet) | **GET** /accounts/{stake_address}/registrations | Account registration history
[**accountsStakeAddressRegistrationsGetWithHttpInfo**](CardanoAccountsApi.md#accountsStakeAddressRegistrationsGetWithHttpInfo) | **GET** /accounts/{stake_address}/registrations | Account registration history
[**accountsStakeAddressRewardsGet**](CardanoAccountsApi.md#accountsStakeAddressRewardsGet) | **GET** /accounts/{stake_address}/rewards | Account reward history
[**accountsStakeAddressRewardsGetWithHttpInfo**](CardanoAccountsApi.md#accountsStakeAddressRewardsGetWithHttpInfo) | **GET** /accounts/{stake_address}/rewards | Account reward history



## accountsStakeAddressAddressesGet

> List<Object> accountsStakeAddressAddressesGet(stakeAddress, count, page, order)

Account associated addresses

Obtain information about the addresses of a specific account.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoAccountsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoAccountsApi apiInstance = new CardanoAccountsApi(defaultClient);
        String stakeAddress = "stake1u9ylzsgxaa6xctf4juup682ar3juj85n8tx3hthnljg47zctvm3rc"; // String | Bech32 stake address.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            List<Object> result = apiInstance.accountsStakeAddressAddressesGet(stakeAddress, count, page, order);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoAccountsApi#accountsStakeAddressAddressesGet");
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
 **stakeAddress** | **String**| Bech32 stake address. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

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
| **200** | Return the account addresses content |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## accountsStakeAddressAddressesGetWithHttpInfo

> ApiResponse<List<Object>> accountsStakeAddressAddressesGet accountsStakeAddressAddressesGetWithHttpInfo(stakeAddress, count, page, order)

Account associated addresses

Obtain information about the addresses of a specific account.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoAccountsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoAccountsApi apiInstance = new CardanoAccountsApi(defaultClient);
        String stakeAddress = "stake1u9ylzsgxaa6xctf4juup682ar3juj85n8tx3hthnljg47zctvm3rc"; // String | Bech32 stake address.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            ApiResponse<List<Object>> response = apiInstance.accountsStakeAddressAddressesGetWithHttpInfo(stakeAddress, count, page, order);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoAccountsApi#accountsStakeAddressAddressesGet");
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
 **stakeAddress** | **String**| Bech32 stake address. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

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
| **200** | Return the account addresses content |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## accountsStakeAddressDelegationsGet

> List<Object> accountsStakeAddressDelegationsGet(stakeAddress, count, page, order)

Account delegation history

Obtain information about the delegation of a specific account.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoAccountsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoAccountsApi apiInstance = new CardanoAccountsApi(defaultClient);
        String stakeAddress = "stake1u9ylzsgxaa6xctf4juup682ar3juj85n8tx3hthnljg47zctvm3rc"; // String | Bech32 stake address.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            List<Object> result = apiInstance.accountsStakeAddressDelegationsGet(stakeAddress, count, page, order);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoAccountsApi#accountsStakeAddressDelegationsGet");
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
 **stakeAddress** | **String**| Bech32 stake address. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

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
| **200** | Return the account delegations content |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## accountsStakeAddressDelegationsGetWithHttpInfo

> ApiResponse<List<Object>> accountsStakeAddressDelegationsGet accountsStakeAddressDelegationsGetWithHttpInfo(stakeAddress, count, page, order)

Account delegation history

Obtain information about the delegation of a specific account.

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoAccountsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoAccountsApi apiInstance = new CardanoAccountsApi(defaultClient);
        String stakeAddress = "stake1u9ylzsgxaa6xctf4juup682ar3juj85n8tx3hthnljg47zctvm3rc"; // String | Bech32 stake address.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            ApiResponse<List<Object>> response = apiInstance.accountsStakeAddressDelegationsGetWithHttpInfo(stakeAddress, count, page, order);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoAccountsApi#accountsStakeAddressDelegationsGet");
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
 **stakeAddress** | **String**| Bech32 stake address. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

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
| **200** | Return the account delegations content |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## accountsStakeAddressGet

> AccountContent accountsStakeAddressGet(stakeAddress)

Specific account address

Obtain information about a specific stake account. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoAccountsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoAccountsApi apiInstance = new CardanoAccountsApi(defaultClient);
        String stakeAddress = "stake1u9ylzsgxaa6xctf4juup682ar3juj85n8tx3hthnljg47zctvm3rc"; // String | Bech32 stake address.
        try {
            AccountContent result = apiInstance.accountsStakeAddressGet(stakeAddress);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoAccountsApi#accountsStakeAddressGet");
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
 **stakeAddress** | **String**| Bech32 stake address. |

### Return type

[**AccountContent**](AccountContent.md)


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the account content. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## accountsStakeAddressGetWithHttpInfo

> ApiResponse<AccountContent> accountsStakeAddressGet accountsStakeAddressGetWithHttpInfo(stakeAddress)

Specific account address

Obtain information about a specific stake account. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoAccountsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoAccountsApi apiInstance = new CardanoAccountsApi(defaultClient);
        String stakeAddress = "stake1u9ylzsgxaa6xctf4juup682ar3juj85n8tx3hthnljg47zctvm3rc"; // String | Bech32 stake address.
        try {
            ApiResponse<AccountContent> response = apiInstance.accountsStakeAddressGetWithHttpInfo(stakeAddress);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoAccountsApi#accountsStakeAddressGet");
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
 **stakeAddress** | **String**| Bech32 stake address. |

### Return type

ApiResponse<[**AccountContent**](AccountContent.md)>


### Authorization

[ApiKeyAuth](../README.md#ApiKeyAuth)

### HTTP request headers

- **Content-Type**: Not defined
- **Accept**: application/json

### HTTP response details
| Status code | Description | Response headers |
|-------------|-------------|------------------|
| **200** | Return the account content. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## accountsStakeAddressHistoryGet

> List<Object> accountsStakeAddressHistoryGet(stakeAddress, count, page, order)

Account history

Obtain information about the history of a specific account. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoAccountsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoAccountsApi apiInstance = new CardanoAccountsApi(defaultClient);
        String stakeAddress = "stake1u9ylzsgxaa6xctf4juup682ar3juj85n8tx3hthnljg47zctvm3rc"; // String | Bech32 stake address.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            List<Object> result = apiInstance.accountsStakeAddressHistoryGet(stakeAddress, count, page, order);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoAccountsApi#accountsStakeAddressHistoryGet");
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
 **stakeAddress** | **String**| Bech32 stake address. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

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
| **200** | Return the account content. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## accountsStakeAddressHistoryGetWithHttpInfo

> ApiResponse<List<Object>> accountsStakeAddressHistoryGet accountsStakeAddressHistoryGetWithHttpInfo(stakeAddress, count, page, order)

Account history

Obtain information about the history of a specific account. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoAccountsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoAccountsApi apiInstance = new CardanoAccountsApi(defaultClient);
        String stakeAddress = "stake1u9ylzsgxaa6xctf4juup682ar3juj85n8tx3hthnljg47zctvm3rc"; // String | Bech32 stake address.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            ApiResponse<List<Object>> response = apiInstance.accountsStakeAddressHistoryGetWithHttpInfo(stakeAddress, count, page, order);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoAccountsApi#accountsStakeAddressHistoryGet");
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
 **stakeAddress** | **String**| Bech32 stake address. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

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
| **200** | Return the account content. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## accountsStakeAddressRegistrationsGet

> List<Object> accountsStakeAddressRegistrationsGet(stakeAddress, count, page, order)

Account registration history

Obtain information about the registrations and deregistrations of a specific account. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoAccountsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoAccountsApi apiInstance = new CardanoAccountsApi(defaultClient);
        String stakeAddress = "stake1u9ylzsgxaa6xctf4juup682ar3juj85n8tx3hthnljg47zctvm3rc"; // String | Bech32 stake address.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            List<Object> result = apiInstance.accountsStakeAddressRegistrationsGet(stakeAddress, count, page, order);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoAccountsApi#accountsStakeAddressRegistrationsGet");
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
 **stakeAddress** | **String**| Bech32 stake address. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

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
| **200** | Return the account registration content. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## accountsStakeAddressRegistrationsGetWithHttpInfo

> ApiResponse<List<Object>> accountsStakeAddressRegistrationsGet accountsStakeAddressRegistrationsGetWithHttpInfo(stakeAddress, count, page, order)

Account registration history

Obtain information about the registrations and deregistrations of a specific account. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoAccountsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoAccountsApi apiInstance = new CardanoAccountsApi(defaultClient);
        String stakeAddress = "stake1u9ylzsgxaa6xctf4juup682ar3juj85n8tx3hthnljg47zctvm3rc"; // String | Bech32 stake address.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            ApiResponse<List<Object>> response = apiInstance.accountsStakeAddressRegistrationsGetWithHttpInfo(stakeAddress, count, page, order);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoAccountsApi#accountsStakeAddressRegistrationsGet");
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
 **stakeAddress** | **String**| Bech32 stake address. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

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
| **200** | Return the account registration content. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |


## accountsStakeAddressRewardsGet

> List<Object> accountsStakeAddressRewardsGet(stakeAddress, count, page, order)

Account reward history

Obtain information about the history of a specific account. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoAccountsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoAccountsApi apiInstance = new CardanoAccountsApi(defaultClient);
        String stakeAddress = "stake1u9ylzsgxaa6xctf4juup682ar3juj85n8tx3hthnljg47zctvm3rc"; // String | Bech32 stake address.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            List<Object> result = apiInstance.accountsStakeAddressRewardsGet(stakeAddress, count, page, order);
            System.out.println(result);
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoAccountsApi#accountsStakeAddressRewardsGet");
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
 **stakeAddress** | **String**| Bech32 stake address. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

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
| **200** | Return the account content. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

## accountsStakeAddressRewardsGetWithHttpInfo

> ApiResponse<List<Object>> accountsStakeAddressRewardsGet accountsStakeAddressRewardsGetWithHttpInfo(stakeAddress, count, page, order)

Account reward history

Obtain information about the history of a specific account. 

### Example

```java
// Import classes:
import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Configuration;
import com.bloxbean.cardano.blockfrost.invoker.auth.*;
import com.bloxbean.cardano.blockfrost.invoker.models.*;
import com.bloxbean.cardano.blockfrost.api.CardanoAccountsApi;

public class Example {
    public static void main(String[] args) {
        ApiClient defaultClient = Configuration.getDefaultApiClient();
        defaultClient.setBasePath("https://cardano-mainnet.blockfrost.io/api/v0");
        
        // Configure API key authorization: ApiKeyAuth
        ApiKeyAuth ApiKeyAuth = (ApiKeyAuth) defaultClient.getAuthentication("ApiKeyAuth");
        ApiKeyAuth.setApiKey("YOUR API KEY");
        // Uncomment the following line to set a prefix for the API key, e.g. "Token" (defaults to null)
        //ApiKeyAuth.setApiKeyPrefix("Token");

        CardanoAccountsApi apiInstance = new CardanoAccountsApi(defaultClient);
        String stakeAddress = "stake1u9ylzsgxaa6xctf4juup682ar3juj85n8tx3hthnljg47zctvm3rc"; // String | Bech32 stake address.
        Integer count = 100; // Integer | The number of results displayed on one page.
        Integer page = 1; // Integer | The page number for listing the results.
        String order = "asc"; // String | The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last. 
        try {
            ApiResponse<List<Object>> response = apiInstance.accountsStakeAddressRewardsGetWithHttpInfo(stakeAddress, count, page, order);
            System.out.println("Status code: " + response.getStatusCode());
            System.out.println("Response headers: " + response.getHeaders());
            System.out.println("Response body: " + response.getData());
        } catch (ApiException e) {
            System.err.println("Exception when calling CardanoAccountsApi#accountsStakeAddressRewardsGet");
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
 **stakeAddress** | **String**| Bech32 stake address. |
 **count** | **Integer**| The number of results displayed on one page. | [optional] [default to 100]
 **page** | **Integer**| The page number for listing the results. | [optional] [default to 1]
 **order** | **String**| The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  | [optional] [default to asc] [enum: asc, desc]

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
| **200** | Return the account content. |  -  |
| **400** | Bad request |  -  |
| **403** | Authentication secret is missing or invalid |  -  |
| **404** | Component not found |  -  |
| **418** | IP has been auto-banned for extensive sending of requests after usage limit has been reached |  -  |
| **429** | Usage limit reached |  -  |
| **500** | Internal Server Error |  -  |

