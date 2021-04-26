/*
 * Blockfrost.io ~ API Documentation
 * Blockfrost is an API as a service that allows users to interact with the Cardano blockchain and parts of its ecosystem.  ## Authentication  After signing up on https://blockfrost.io, a `project_id` token is automatically generated for each project. HTTP header of your request MUST include this `project_id` in order to authenticate against Blockfrost servers.  ## Available networks  At the moment, you can use the following networks. Please, note that each network has its own `project_id`.  <table>   <tr><td><b>Network</b></td><td><b>Endpoint</b></td></tr>   <tr><td>Cardano mainnet</td><td><tt>https://cardano-mainnet.blockfrost.io/api/v0</td></tt></tr>   <tr><td>Cardano testnet</td><td><tt>https://cardano-testnet.blockfrost.io/api/v0</tt></td></tr>   <tr><td>InterPlanetary File System</td><td><tt>https://ipfs.blockfrost.io/api/v0</tt></td></tr> </table>  ## Concepts  * All endpoints return either a JSON object or an array. * Data is returned in *ascending* (oldest first, newest last) order.   * You might use the `?order=desc` query parameter to reverse this order. * By default, we return 100 results at a time. You have to use `?page=2` to list through the results. * All time and timestamp related fields are in milliseconds of UNIX time. * All amounts are returned in Lovelaces, where 1 ADA = 1 000 000 Lovelaces. * Addresses, accounts and pool IDs are in Bech32 format. * All values are case sensitive. * All hex encoded values are lower case. * Examples are not based on real data. Any resemblance to actual events is purely coincidental. * We allow to upload files up to 100MB of size to IPFS. This might increase in the future.  ## Errors  ### HTTP Status codes  The following are HTTP status code your application might receive when reaching Blockfrost endpoints and it should handle all of these cases.  * HTTP `400` return code is used when the request is not valid. * HTTP `402` return code is used when the projects exceed their daily request limit. * HTTP `403` return code is used when the request is not authenticated. * HTTP `404` return code is used when the resource doesn't exist. * HTTP `418` return code is used when the user has been auto-banned for flooding too much after previously receiving error code `402` or `429`. * HTTP `429` return code is used when the user has sent too many requests in a given amount of time and therefore has been rate-limited. * HTTP `500` return code is used when our endpoints are having a problem.  ### Error codes  An internal error code number is used for better indication of the error in question. It is passed using the following payload.  ```json {   \"status_code\": 403,   \"error\": \"Forbidden\",   \"message\": \"Invalid project token.\" } ``` ## Limits   There are two types of limits we are enforcing. The first depends on your plan and is the number of request we allow per day. We defined  the day from midnight to midnight of UTC time. The second is rate limiting, where we limit an end user to 10 requests per second after a period  of 5 second burst. We believe this should be sufficient for most of the use cases. If it is not and you have a specific use case, please get in touch with us, and  we will make sure to take it into account as much as we can.  # Authentication  <!-- ReDoc-Inject: <security-definitions> -->
 *
 * The version of the OpenAPI document: 0.1.11
 * Contact: contact@blockfrost.io
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

package com.bloxbean.cardano.blockfrost.api;

import com.bloxbean.cardano.blockfrost.invoker.ApiClient;
import com.bloxbean.cardano.blockfrost.invoker.ApiException;
import com.bloxbean.cardano.blockfrost.invoker.ApiResponse;
import com.bloxbean.cardano.blockfrost.invoker.Pair;

import com.bloxbean.cardano.blockfrost.model.AccountContent;
import com.bloxbean.cardano.blockfrost.model.InlineResponse400;
import com.bloxbean.cardano.blockfrost.model.InlineResponse403;
import com.bloxbean.cardano.blockfrost.model.InlineResponse404;
import com.bloxbean.cardano.blockfrost.model.InlineResponse418;
import com.bloxbean.cardano.blockfrost.model.InlineResponse429;
import com.bloxbean.cardano.blockfrost.model.InlineResponse500;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

import java.util.ArrayList;
import java.util.StringJoiner;
import java.util.List;
import java.util.Map;

@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-04-26T22:42:12.889086+08:00[Asia/Singapore]")
public class CardanoAccountsApi {
  private final HttpClient memberVarHttpClient;
  private final ObjectMapper memberVarObjectMapper;
  private final String memberVarBaseUri;
  private final Consumer<HttpRequest.Builder> memberVarInterceptor;
  private final Duration memberVarReadTimeout;
  private final Consumer<HttpResponse<InputStream>> memberVarResponseInterceptor;

  public CardanoAccountsApi() {
    this(new ApiClient());
  }

  public CardanoAccountsApi(ApiClient apiClient) {
    memberVarHttpClient = apiClient.getHttpClient();
    memberVarObjectMapper = apiClient.getObjectMapper();
    memberVarBaseUri = apiClient.getBaseUri();
    memberVarInterceptor = apiClient.getRequestInterceptor();
    memberVarReadTimeout = apiClient.getReadTimeout();
    memberVarResponseInterceptor = apiClient.getResponseInterceptor();
  }

  /**
   * Account associated addresses
   * Obtain information about the addresses of a specific account.
   * @param stakeAddress Bech32 stake address. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @param order The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
   * @return List&lt;Object&gt;
   * @throws ApiException if fails to make API call
   */
  public List<Object> accountsStakeAddressAddressesGet(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    ApiResponse<List<Object>> localVarResponse = accountsStakeAddressAddressesGetWithHttpInfo(stakeAddress, count, page, order);
    return localVarResponse.getData();
  }

  /**
   * Account associated addresses
   * Obtain information about the addresses of a specific account.
   * @param stakeAddress Bech32 stake address. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @param order The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
   * @return ApiResponse&lt;List&lt;Object&gt;&gt;
   * @throws ApiException if fails to make API call
   */
  public ApiResponse<List<Object>> accountsStakeAddressAddressesGetWithHttpInfo(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    HttpRequest.Builder localVarRequestBuilder = accountsStakeAddressAddressesGetRequestBuilder(stakeAddress, count, page, order);
    try {
      HttpResponse<InputStream> localVarResponse = memberVarHttpClient.send(
          localVarRequestBuilder.build(),
          HttpResponse.BodyHandlers.ofInputStream());
      if (memberVarResponseInterceptor != null) {
        memberVarResponseInterceptor.accept(localVarResponse);
      }
      if (localVarResponse.statusCode()/ 100 != 2) {
        throw new ApiException(localVarResponse.statusCode(),
            "accountsStakeAddressAddressesGet call received non-success response",
            localVarResponse.headers(),
            localVarResponse.body() == null ? null : new String(localVarResponse.body().readAllBytes()));
      }
      return new ApiResponse<List<Object>>(
          localVarResponse.statusCode(),
          localVarResponse.headers().map(),
          memberVarObjectMapper.readValue(localVarResponse.body(), new TypeReference<List<Object>>() {})
        );
    } catch (IOException e) {
      throw new ApiException(e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(e);
    }
  }

  private HttpRequest.Builder accountsStakeAddressAddressesGetRequestBuilder(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    // verify the required parameter 'stakeAddress' is set
    if (stakeAddress == null) {
      throw new ApiException(400, "Missing the required parameter 'stakeAddress' when calling accountsStakeAddressAddressesGet");
    }

    HttpRequest.Builder localVarRequestBuilder = HttpRequest.newBuilder();

    String localVarPath = "/accounts/{stake_address}/addresses"
        .replace("{stake_address}", ApiClient.urlEncode(stakeAddress.toString()));

    List<Pair> localVarQueryParams = new ArrayList<>();
    localVarQueryParams.addAll(ApiClient.parameterToPairs("count", count));
    localVarQueryParams.addAll(ApiClient.parameterToPairs("page", page));
    localVarQueryParams.addAll(ApiClient.parameterToPairs("order", order));

    if (!localVarQueryParams.isEmpty()) {
      StringJoiner queryJoiner = new StringJoiner("&");
      localVarQueryParams.forEach(p -> queryJoiner.add(p.getName() + '=' + p.getValue()));
      localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath + '?' + queryJoiner.toString()));
    } else {
      localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath));
    }

    localVarRequestBuilder.header("Accept", "application/json");

    localVarRequestBuilder.method("GET", HttpRequest.BodyPublishers.noBody());
    if (memberVarReadTimeout != null) {
      localVarRequestBuilder.timeout(memberVarReadTimeout);
    }
    if (memberVarInterceptor != null) {
      memberVarInterceptor.accept(localVarRequestBuilder);
    }
    return localVarRequestBuilder;
  }
  /**
   * Account delegation history
   * Obtain information about the delegation of a specific account.
   * @param stakeAddress Bech32 stake address. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @param order The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
   * @return List&lt;Object&gt;
   * @throws ApiException if fails to make API call
   */
  public List<Object> accountsStakeAddressDelegationsGet(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    ApiResponse<List<Object>> localVarResponse = accountsStakeAddressDelegationsGetWithHttpInfo(stakeAddress, count, page, order);
    return localVarResponse.getData();
  }

  /**
   * Account delegation history
   * Obtain information about the delegation of a specific account.
   * @param stakeAddress Bech32 stake address. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @param order The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
   * @return ApiResponse&lt;List&lt;Object&gt;&gt;
   * @throws ApiException if fails to make API call
   */
  public ApiResponse<List<Object>> accountsStakeAddressDelegationsGetWithHttpInfo(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    HttpRequest.Builder localVarRequestBuilder = accountsStakeAddressDelegationsGetRequestBuilder(stakeAddress, count, page, order);
    try {
      HttpResponse<InputStream> localVarResponse = memberVarHttpClient.send(
          localVarRequestBuilder.build(),
          HttpResponse.BodyHandlers.ofInputStream());
      if (memberVarResponseInterceptor != null) {
        memberVarResponseInterceptor.accept(localVarResponse);
      }
      if (localVarResponse.statusCode()/ 100 != 2) {
        throw new ApiException(localVarResponse.statusCode(),
            "accountsStakeAddressDelegationsGet call received non-success response",
            localVarResponse.headers(),
            localVarResponse.body() == null ? null : new String(localVarResponse.body().readAllBytes()));
      }
      return new ApiResponse<List<Object>>(
          localVarResponse.statusCode(),
          localVarResponse.headers().map(),
          memberVarObjectMapper.readValue(localVarResponse.body(), new TypeReference<List<Object>>() {})
        );
    } catch (IOException e) {
      throw new ApiException(e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(e);
    }
  }

  private HttpRequest.Builder accountsStakeAddressDelegationsGetRequestBuilder(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    // verify the required parameter 'stakeAddress' is set
    if (stakeAddress == null) {
      throw new ApiException(400, "Missing the required parameter 'stakeAddress' when calling accountsStakeAddressDelegationsGet");
    }

    HttpRequest.Builder localVarRequestBuilder = HttpRequest.newBuilder();

    String localVarPath = "/accounts/{stake_address}/delegations"
        .replace("{stake_address}", ApiClient.urlEncode(stakeAddress.toString()));

    List<Pair> localVarQueryParams = new ArrayList<>();
    localVarQueryParams.addAll(ApiClient.parameterToPairs("count", count));
    localVarQueryParams.addAll(ApiClient.parameterToPairs("page", page));
    localVarQueryParams.addAll(ApiClient.parameterToPairs("order", order));

    if (!localVarQueryParams.isEmpty()) {
      StringJoiner queryJoiner = new StringJoiner("&");
      localVarQueryParams.forEach(p -> queryJoiner.add(p.getName() + '=' + p.getValue()));
      localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath + '?' + queryJoiner.toString()));
    } else {
      localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath));
    }

    localVarRequestBuilder.header("Accept", "application/json");

    localVarRequestBuilder.method("GET", HttpRequest.BodyPublishers.noBody());
    if (memberVarReadTimeout != null) {
      localVarRequestBuilder.timeout(memberVarReadTimeout);
    }
    if (memberVarInterceptor != null) {
      memberVarInterceptor.accept(localVarRequestBuilder);
    }
    return localVarRequestBuilder;
  }
  /**
   * Specific account address
   * Obtain information about a specific stake account. 
   * @param stakeAddress Bech32 stake address. (required)
   * @return AccountContent
   * @throws ApiException if fails to make API call
   */
  public AccountContent accountsStakeAddressGet(String stakeAddress) throws ApiException {
    ApiResponse<AccountContent> localVarResponse = accountsStakeAddressGetWithHttpInfo(stakeAddress);
    return localVarResponse.getData();
  }

  /**
   * Specific account address
   * Obtain information about a specific stake account. 
   * @param stakeAddress Bech32 stake address. (required)
   * @return ApiResponse&lt;AccountContent&gt;
   * @throws ApiException if fails to make API call
   */
  public ApiResponse<AccountContent> accountsStakeAddressGetWithHttpInfo(String stakeAddress) throws ApiException {
    HttpRequest.Builder localVarRequestBuilder = accountsStakeAddressGetRequestBuilder(stakeAddress);
    try {
      HttpResponse<InputStream> localVarResponse = memberVarHttpClient.send(
          localVarRequestBuilder.build(),
          HttpResponse.BodyHandlers.ofInputStream());
      if (memberVarResponseInterceptor != null) {
        memberVarResponseInterceptor.accept(localVarResponse);
      }
      if (localVarResponse.statusCode()/ 100 != 2) {
        throw new ApiException(localVarResponse.statusCode(),
            "accountsStakeAddressGet call received non-success response",
            localVarResponse.headers(),
            localVarResponse.body() == null ? null : new String(localVarResponse.body().readAllBytes()));
      }
      return new ApiResponse<AccountContent>(
          localVarResponse.statusCode(),
          localVarResponse.headers().map(),
          memberVarObjectMapper.readValue(localVarResponse.body(), new TypeReference<AccountContent>() {})
        );
    } catch (IOException e) {
      throw new ApiException(e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(e);
    }
  }

  private HttpRequest.Builder accountsStakeAddressGetRequestBuilder(String stakeAddress) throws ApiException {
    // verify the required parameter 'stakeAddress' is set
    if (stakeAddress == null) {
      throw new ApiException(400, "Missing the required parameter 'stakeAddress' when calling accountsStakeAddressGet");
    }

    HttpRequest.Builder localVarRequestBuilder = HttpRequest.newBuilder();

    String localVarPath = "/accounts/{stake_address}"
        .replace("{stake_address}", ApiClient.urlEncode(stakeAddress.toString()));

    localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath));

    localVarRequestBuilder.header("Accept", "application/json");

    localVarRequestBuilder.method("GET", HttpRequest.BodyPublishers.noBody());
    if (memberVarReadTimeout != null) {
      localVarRequestBuilder.timeout(memberVarReadTimeout);
    }
    if (memberVarInterceptor != null) {
      memberVarInterceptor.accept(localVarRequestBuilder);
    }
    return localVarRequestBuilder;
  }
  /**
   * Account history
   * Obtain information about the history of a specific account. 
   * @param stakeAddress Bech32 stake address. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @param order The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
   * @return List&lt;Object&gt;
   * @throws ApiException if fails to make API call
   */
  public List<Object> accountsStakeAddressHistoryGet(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    ApiResponse<List<Object>> localVarResponse = accountsStakeAddressHistoryGetWithHttpInfo(stakeAddress, count, page, order);
    return localVarResponse.getData();
  }

  /**
   * Account history
   * Obtain information about the history of a specific account. 
   * @param stakeAddress Bech32 stake address. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @param order The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
   * @return ApiResponse&lt;List&lt;Object&gt;&gt;
   * @throws ApiException if fails to make API call
   */
  public ApiResponse<List<Object>> accountsStakeAddressHistoryGetWithHttpInfo(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    HttpRequest.Builder localVarRequestBuilder = accountsStakeAddressHistoryGetRequestBuilder(stakeAddress, count, page, order);
    try {
      HttpResponse<InputStream> localVarResponse = memberVarHttpClient.send(
          localVarRequestBuilder.build(),
          HttpResponse.BodyHandlers.ofInputStream());
      if (memberVarResponseInterceptor != null) {
        memberVarResponseInterceptor.accept(localVarResponse);
      }
      if (localVarResponse.statusCode()/ 100 != 2) {
        throw new ApiException(localVarResponse.statusCode(),
            "accountsStakeAddressHistoryGet call received non-success response",
            localVarResponse.headers(),
            localVarResponse.body() == null ? null : new String(localVarResponse.body().readAllBytes()));
      }
      return new ApiResponse<List<Object>>(
          localVarResponse.statusCode(),
          localVarResponse.headers().map(),
          memberVarObjectMapper.readValue(localVarResponse.body(), new TypeReference<List<Object>>() {})
        );
    } catch (IOException e) {
      throw new ApiException(e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(e);
    }
  }

  private HttpRequest.Builder accountsStakeAddressHistoryGetRequestBuilder(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    // verify the required parameter 'stakeAddress' is set
    if (stakeAddress == null) {
      throw new ApiException(400, "Missing the required parameter 'stakeAddress' when calling accountsStakeAddressHistoryGet");
    }

    HttpRequest.Builder localVarRequestBuilder = HttpRequest.newBuilder();

    String localVarPath = "/accounts/{stake_address}/history"
        .replace("{stake_address}", ApiClient.urlEncode(stakeAddress.toString()));

    List<Pair> localVarQueryParams = new ArrayList<>();
    localVarQueryParams.addAll(ApiClient.parameterToPairs("count", count));
    localVarQueryParams.addAll(ApiClient.parameterToPairs("page", page));
    localVarQueryParams.addAll(ApiClient.parameterToPairs("order", order));

    if (!localVarQueryParams.isEmpty()) {
      StringJoiner queryJoiner = new StringJoiner("&");
      localVarQueryParams.forEach(p -> queryJoiner.add(p.getName() + '=' + p.getValue()));
      localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath + '?' + queryJoiner.toString()));
    } else {
      localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath));
    }

    localVarRequestBuilder.header("Accept", "application/json");

    localVarRequestBuilder.method("GET", HttpRequest.BodyPublishers.noBody());
    if (memberVarReadTimeout != null) {
      localVarRequestBuilder.timeout(memberVarReadTimeout);
    }
    if (memberVarInterceptor != null) {
      memberVarInterceptor.accept(localVarRequestBuilder);
    }
    return localVarRequestBuilder;
  }
  /**
   * Account registration history
   * Obtain information about the registrations and deregistrations of a specific account. 
   * @param stakeAddress Bech32 stake address. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @param order The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
   * @return List&lt;Object&gt;
   * @throws ApiException if fails to make API call
   */
  public List<Object> accountsStakeAddressRegistrationsGet(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    ApiResponse<List<Object>> localVarResponse = accountsStakeAddressRegistrationsGetWithHttpInfo(stakeAddress, count, page, order);
    return localVarResponse.getData();
  }

  /**
   * Account registration history
   * Obtain information about the registrations and deregistrations of a specific account. 
   * @param stakeAddress Bech32 stake address. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @param order The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
   * @return ApiResponse&lt;List&lt;Object&gt;&gt;
   * @throws ApiException if fails to make API call
   */
  public ApiResponse<List<Object>> accountsStakeAddressRegistrationsGetWithHttpInfo(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    HttpRequest.Builder localVarRequestBuilder = accountsStakeAddressRegistrationsGetRequestBuilder(stakeAddress, count, page, order);
    try {
      HttpResponse<InputStream> localVarResponse = memberVarHttpClient.send(
          localVarRequestBuilder.build(),
          HttpResponse.BodyHandlers.ofInputStream());
      if (memberVarResponseInterceptor != null) {
        memberVarResponseInterceptor.accept(localVarResponse);
      }
      if (localVarResponse.statusCode()/ 100 != 2) {
        throw new ApiException(localVarResponse.statusCode(),
            "accountsStakeAddressRegistrationsGet call received non-success response",
            localVarResponse.headers(),
            localVarResponse.body() == null ? null : new String(localVarResponse.body().readAllBytes()));
      }
      return new ApiResponse<List<Object>>(
          localVarResponse.statusCode(),
          localVarResponse.headers().map(),
          memberVarObjectMapper.readValue(localVarResponse.body(), new TypeReference<List<Object>>() {})
        );
    } catch (IOException e) {
      throw new ApiException(e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(e);
    }
  }

  private HttpRequest.Builder accountsStakeAddressRegistrationsGetRequestBuilder(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    // verify the required parameter 'stakeAddress' is set
    if (stakeAddress == null) {
      throw new ApiException(400, "Missing the required parameter 'stakeAddress' when calling accountsStakeAddressRegistrationsGet");
    }

    HttpRequest.Builder localVarRequestBuilder = HttpRequest.newBuilder();

    String localVarPath = "/accounts/{stake_address}/registrations"
        .replace("{stake_address}", ApiClient.urlEncode(stakeAddress.toString()));

    List<Pair> localVarQueryParams = new ArrayList<>();
    localVarQueryParams.addAll(ApiClient.parameterToPairs("count", count));
    localVarQueryParams.addAll(ApiClient.parameterToPairs("page", page));
    localVarQueryParams.addAll(ApiClient.parameterToPairs("order", order));

    if (!localVarQueryParams.isEmpty()) {
      StringJoiner queryJoiner = new StringJoiner("&");
      localVarQueryParams.forEach(p -> queryJoiner.add(p.getName() + '=' + p.getValue()));
      localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath + '?' + queryJoiner.toString()));
    } else {
      localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath));
    }

    localVarRequestBuilder.header("Accept", "application/json");

    localVarRequestBuilder.method("GET", HttpRequest.BodyPublishers.noBody());
    if (memberVarReadTimeout != null) {
      localVarRequestBuilder.timeout(memberVarReadTimeout);
    }
    if (memberVarInterceptor != null) {
      memberVarInterceptor.accept(localVarRequestBuilder);
    }
    return localVarRequestBuilder;
  }
  /**
   * Account reward history
   * Obtain information about the history of a specific account. 
   * @param stakeAddress Bech32 stake address. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @param order The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
   * @return List&lt;Object&gt;
   * @throws ApiException if fails to make API call
   */
  public List<Object> accountsStakeAddressRewardsGet(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    ApiResponse<List<Object>> localVarResponse = accountsStakeAddressRewardsGetWithHttpInfo(stakeAddress, count, page, order);
    return localVarResponse.getData();
  }

  /**
   * Account reward history
   * Obtain information about the history of a specific account. 
   * @param stakeAddress Bech32 stake address. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @param order The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
   * @return ApiResponse&lt;List&lt;Object&gt;&gt;
   * @throws ApiException if fails to make API call
   */
  public ApiResponse<List<Object>> accountsStakeAddressRewardsGetWithHttpInfo(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    HttpRequest.Builder localVarRequestBuilder = accountsStakeAddressRewardsGetRequestBuilder(stakeAddress, count, page, order);
    try {
      HttpResponse<InputStream> localVarResponse = memberVarHttpClient.send(
          localVarRequestBuilder.build(),
          HttpResponse.BodyHandlers.ofInputStream());
      if (memberVarResponseInterceptor != null) {
        memberVarResponseInterceptor.accept(localVarResponse);
      }
      if (localVarResponse.statusCode()/ 100 != 2) {
        throw new ApiException(localVarResponse.statusCode(),
            "accountsStakeAddressRewardsGet call received non-success response",
            localVarResponse.headers(),
            localVarResponse.body() == null ? null : new String(localVarResponse.body().readAllBytes()));
      }
      return new ApiResponse<List<Object>>(
          localVarResponse.statusCode(),
          localVarResponse.headers().map(),
          memberVarObjectMapper.readValue(localVarResponse.body(), new TypeReference<List<Object>>() {})
        );
    } catch (IOException e) {
      throw new ApiException(e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(e);
    }
  }

  private HttpRequest.Builder accountsStakeAddressRewardsGetRequestBuilder(String stakeAddress, Integer count, Integer page, String order) throws ApiException {
    // verify the required parameter 'stakeAddress' is set
    if (stakeAddress == null) {
      throw new ApiException(400, "Missing the required parameter 'stakeAddress' when calling accountsStakeAddressRewardsGet");
    }

    HttpRequest.Builder localVarRequestBuilder = HttpRequest.newBuilder();

    String localVarPath = "/accounts/{stake_address}/rewards"
        .replace("{stake_address}", ApiClient.urlEncode(stakeAddress.toString()));

    List<Pair> localVarQueryParams = new ArrayList<>();
    localVarQueryParams.addAll(ApiClient.parameterToPairs("count", count));
    localVarQueryParams.addAll(ApiClient.parameterToPairs("page", page));
    localVarQueryParams.addAll(ApiClient.parameterToPairs("order", order));

    if (!localVarQueryParams.isEmpty()) {
      StringJoiner queryJoiner = new StringJoiner("&");
      localVarQueryParams.forEach(p -> queryJoiner.add(p.getName() + '=' + p.getValue()));
      localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath + '?' + queryJoiner.toString()));
    } else {
      localVarRequestBuilder.uri(URI.create(memberVarBaseUri + localVarPath));
    }

    localVarRequestBuilder.header("Accept", "application/json");

    localVarRequestBuilder.method("GET", HttpRequest.BodyPublishers.noBody());
    if (memberVarReadTimeout != null) {
      localVarRequestBuilder.timeout(memberVarReadTimeout);
    }
    if (memberVarInterceptor != null) {
      memberVarInterceptor.accept(localVarRequestBuilder);
    }
    return localVarRequestBuilder;
  }
}
