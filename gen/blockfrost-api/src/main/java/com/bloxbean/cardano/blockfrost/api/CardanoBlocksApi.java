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

import com.bloxbean.cardano.blockfrost.model.BlockContent;
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
public class CardanoBlocksApi {
  private final HttpClient memberVarHttpClient;
  private final ObjectMapper memberVarObjectMapper;
  private final String memberVarBaseUri;
  private final Consumer<HttpRequest.Builder> memberVarInterceptor;
  private final Duration memberVarReadTimeout;
  private final Consumer<HttpResponse<InputStream>> memberVarResponseInterceptor;

  public CardanoBlocksApi() {
    this(new ApiClient());
  }

  public CardanoBlocksApi(ApiClient apiClient) {
    memberVarHttpClient = apiClient.getHttpClient();
    memberVarObjectMapper = apiClient.getObjectMapper();
    memberVarBaseUri = apiClient.getBaseUri();
    memberVarInterceptor = apiClient.getRequestInterceptor();
    memberVarReadTimeout = apiClient.getReadTimeout();
    memberVarResponseInterceptor = apiClient.getResponseInterceptor();
  }

  /**
   * Specific block
   * Return the content of a requested block. 
   * @param hashOrNumber Hash of the requested block. (required)
   * @return BlockContent
   * @throws ApiException if fails to make API call
   */
  public BlockContent blocksHashOrNumberGet(String hashOrNumber) throws ApiException {
    ApiResponse<BlockContent> localVarResponse = blocksHashOrNumberGetWithHttpInfo(hashOrNumber);
    return localVarResponse.getData();
  }

  /**
   * Specific block
   * Return the content of a requested block. 
   * @param hashOrNumber Hash of the requested block. (required)
   * @return ApiResponse&lt;BlockContent&gt;
   * @throws ApiException if fails to make API call
   */
  public ApiResponse<BlockContent> blocksHashOrNumberGetWithHttpInfo(String hashOrNumber) throws ApiException {
    HttpRequest.Builder localVarRequestBuilder = blocksHashOrNumberGetRequestBuilder(hashOrNumber);
    try {
      HttpResponse<InputStream> localVarResponse = memberVarHttpClient.send(
          localVarRequestBuilder.build(),
          HttpResponse.BodyHandlers.ofInputStream());
      if (memberVarResponseInterceptor != null) {
        memberVarResponseInterceptor.accept(localVarResponse);
      }
      if (localVarResponse.statusCode()/ 100 != 2) {
        throw new ApiException(localVarResponse.statusCode(),
            "blocksHashOrNumberGet call received non-success response",
            localVarResponse.headers(),
            localVarResponse.body() == null ? null : new String(localVarResponse.body().readAllBytes()));
      }
      return new ApiResponse<BlockContent>(
          localVarResponse.statusCode(),
          localVarResponse.headers().map(),
          memberVarObjectMapper.readValue(localVarResponse.body(), new TypeReference<BlockContent>() {})
        );
    } catch (IOException e) {
      throw new ApiException(e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(e);
    }
  }

  private HttpRequest.Builder blocksHashOrNumberGetRequestBuilder(String hashOrNumber) throws ApiException {
    // verify the required parameter 'hashOrNumber' is set
    if (hashOrNumber == null) {
      throw new ApiException(400, "Missing the required parameter 'hashOrNumber' when calling blocksHashOrNumberGet");
    }

    HttpRequest.Builder localVarRequestBuilder = HttpRequest.newBuilder();

    String localVarPath = "/blocks/{hash_or_number}"
        .replace("{hash_or_number}", ApiClient.urlEncode(hashOrNumber.toString()));

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
   * Listing of next blocks
   * Return the list of blocks following a specific block. 
   * @param hashOrNumber Hash of the requested block. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @return List&lt;BlockContent&gt;
   * @throws ApiException if fails to make API call
   */
  public List<BlockContent> blocksHashOrNumberNextGet(String hashOrNumber, Integer count, Integer page) throws ApiException {
    ApiResponse<List<BlockContent>> localVarResponse = blocksHashOrNumberNextGetWithHttpInfo(hashOrNumber, count, page);
    return localVarResponse.getData();
  }

  /**
   * Listing of next blocks
   * Return the list of blocks following a specific block. 
   * @param hashOrNumber Hash of the requested block. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @return ApiResponse&lt;List&lt;BlockContent&gt;&gt;
   * @throws ApiException if fails to make API call
   */
  public ApiResponse<List<BlockContent>> blocksHashOrNumberNextGetWithHttpInfo(String hashOrNumber, Integer count, Integer page) throws ApiException {
    HttpRequest.Builder localVarRequestBuilder = blocksHashOrNumberNextGetRequestBuilder(hashOrNumber, count, page);
    try {
      HttpResponse<InputStream> localVarResponse = memberVarHttpClient.send(
          localVarRequestBuilder.build(),
          HttpResponse.BodyHandlers.ofInputStream());
      if (memberVarResponseInterceptor != null) {
        memberVarResponseInterceptor.accept(localVarResponse);
      }
      if (localVarResponse.statusCode()/ 100 != 2) {
        throw new ApiException(localVarResponse.statusCode(),
            "blocksHashOrNumberNextGet call received non-success response",
            localVarResponse.headers(),
            localVarResponse.body() == null ? null : new String(localVarResponse.body().readAllBytes()));
      }
      return new ApiResponse<List<BlockContent>>(
          localVarResponse.statusCode(),
          localVarResponse.headers().map(),
          memberVarObjectMapper.readValue(localVarResponse.body(), new TypeReference<List<BlockContent>>() {})
        );
    } catch (IOException e) {
      throw new ApiException(e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(e);
    }
  }

  private HttpRequest.Builder blocksHashOrNumberNextGetRequestBuilder(String hashOrNumber, Integer count, Integer page) throws ApiException {
    // verify the required parameter 'hashOrNumber' is set
    if (hashOrNumber == null) {
      throw new ApiException(400, "Missing the required parameter 'hashOrNumber' when calling blocksHashOrNumberNextGet");
    }

    HttpRequest.Builder localVarRequestBuilder = HttpRequest.newBuilder();

    String localVarPath = "/blocks/{hash_or_number}/next"
        .replace("{hash_or_number}", ApiClient.urlEncode(hashOrNumber.toString()));

    List<Pair> localVarQueryParams = new ArrayList<>();
    localVarQueryParams.addAll(ApiClient.parameterToPairs("count", count));
    localVarQueryParams.addAll(ApiClient.parameterToPairs("page", page));

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
   * Listing of previous blocks
   * Return the list of blocks preceding a specific block. 
   * @param hashOrNumber Hash of the requested block (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @return List&lt;BlockContent&gt;
   * @throws ApiException if fails to make API call
   */
  public List<BlockContent> blocksHashOrNumberPreviousGet(String hashOrNumber, Integer count, Integer page) throws ApiException {
    ApiResponse<List<BlockContent>> localVarResponse = blocksHashOrNumberPreviousGetWithHttpInfo(hashOrNumber, count, page);
    return localVarResponse.getData();
  }

  /**
   * Listing of previous blocks
   * Return the list of blocks preceding a specific block. 
   * @param hashOrNumber Hash of the requested block (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @return ApiResponse&lt;List&lt;BlockContent&gt;&gt;
   * @throws ApiException if fails to make API call
   */
  public ApiResponse<List<BlockContent>> blocksHashOrNumberPreviousGetWithHttpInfo(String hashOrNumber, Integer count, Integer page) throws ApiException {
    HttpRequest.Builder localVarRequestBuilder = blocksHashOrNumberPreviousGetRequestBuilder(hashOrNumber, count, page);
    try {
      HttpResponse<InputStream> localVarResponse = memberVarHttpClient.send(
          localVarRequestBuilder.build(),
          HttpResponse.BodyHandlers.ofInputStream());
      if (memberVarResponseInterceptor != null) {
        memberVarResponseInterceptor.accept(localVarResponse);
      }
      if (localVarResponse.statusCode()/ 100 != 2) {
        throw new ApiException(localVarResponse.statusCode(),
            "blocksHashOrNumberPreviousGet call received non-success response",
            localVarResponse.headers(),
            localVarResponse.body() == null ? null : new String(localVarResponse.body().readAllBytes()));
      }
      return new ApiResponse<List<BlockContent>>(
          localVarResponse.statusCode(),
          localVarResponse.headers().map(),
          memberVarObjectMapper.readValue(localVarResponse.body(), new TypeReference<List<BlockContent>>() {})
        );
    } catch (IOException e) {
      throw new ApiException(e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(e);
    }
  }

  private HttpRequest.Builder blocksHashOrNumberPreviousGetRequestBuilder(String hashOrNumber, Integer count, Integer page) throws ApiException {
    // verify the required parameter 'hashOrNumber' is set
    if (hashOrNumber == null) {
      throw new ApiException(400, "Missing the required parameter 'hashOrNumber' when calling blocksHashOrNumberPreviousGet");
    }

    HttpRequest.Builder localVarRequestBuilder = HttpRequest.newBuilder();

    String localVarPath = "/blocks/{hash_or_number}/previous"
        .replace("{hash_or_number}", ApiClient.urlEncode(hashOrNumber.toString()));

    List<Pair> localVarQueryParams = new ArrayList<>();
    localVarQueryParams.addAll(ApiClient.parameterToPairs("count", count));
    localVarQueryParams.addAll(ApiClient.parameterToPairs("page", page));

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
   * Block transactions
   * Return the transactions within the block.
   * @param hashOrNumber Hash of the requested block. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @param order Ordered by tx index in the block. The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
   * @return List&lt;String&gt;
   * @throws ApiException if fails to make API call
   */
  public List<String> blocksHashOrNumberTxsGet(String hashOrNumber, Integer count, Integer page, String order) throws ApiException {
    ApiResponse<List<String>> localVarResponse = blocksHashOrNumberTxsGetWithHttpInfo(hashOrNumber, count, page, order);
    return localVarResponse.getData();
  }

  /**
   * Block transactions
   * Return the transactions within the block.
   * @param hashOrNumber Hash of the requested block. (required)
   * @param count The number of results displayed on one page. (optional, default to 100)
   * @param page The page number for listing the results. (optional, default to 1)
   * @param order Ordered by tx index in the block. The ordering of items from the point of view of the blockchain, not the page listing itself. By default, we return oldest first, newest last.  (optional, default to asc)
   * @return ApiResponse&lt;List&lt;String&gt;&gt;
   * @throws ApiException if fails to make API call
   */
  public ApiResponse<List<String>> blocksHashOrNumberTxsGetWithHttpInfo(String hashOrNumber, Integer count, Integer page, String order) throws ApiException {
    HttpRequest.Builder localVarRequestBuilder = blocksHashOrNumberTxsGetRequestBuilder(hashOrNumber, count, page, order);
    try {
      HttpResponse<InputStream> localVarResponse = memberVarHttpClient.send(
          localVarRequestBuilder.build(),
          HttpResponse.BodyHandlers.ofInputStream());
      if (memberVarResponseInterceptor != null) {
        memberVarResponseInterceptor.accept(localVarResponse);
      }
      if (localVarResponse.statusCode()/ 100 != 2) {
        throw new ApiException(localVarResponse.statusCode(),
            "blocksHashOrNumberTxsGet call received non-success response",
            localVarResponse.headers(),
            localVarResponse.body() == null ? null : new String(localVarResponse.body().readAllBytes()));
      }
      return new ApiResponse<List<String>>(
          localVarResponse.statusCode(),
          localVarResponse.headers().map(),
          memberVarObjectMapper.readValue(localVarResponse.body(), new TypeReference<List<String>>() {})
        );
    } catch (IOException e) {
      throw new ApiException(e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(e);
    }
  }

  private HttpRequest.Builder blocksHashOrNumberTxsGetRequestBuilder(String hashOrNumber, Integer count, Integer page, String order) throws ApiException {
    // verify the required parameter 'hashOrNumber' is set
    if (hashOrNumber == null) {
      throw new ApiException(400, "Missing the required parameter 'hashOrNumber' when calling blocksHashOrNumberTxsGet");
    }

    HttpRequest.Builder localVarRequestBuilder = HttpRequest.newBuilder();

    String localVarPath = "/blocks/{hash_or_number}/txs"
        .replace("{hash_or_number}", ApiClient.urlEncode(hashOrNumber.toString()));

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
   * Latest block
   * Return the latest block available to the backends, also known as the tip of the blockchain. 
   * @return BlockContent
   * @throws ApiException if fails to make API call
   */
  public BlockContent blocksLatestGet() throws ApiException {
    ApiResponse<BlockContent> localVarResponse = blocksLatestGetWithHttpInfo();
    return localVarResponse.getData();
  }

  /**
   * Latest block
   * Return the latest block available to the backends, also known as the tip of the blockchain. 
   * @return ApiResponse&lt;BlockContent&gt;
   * @throws ApiException if fails to make API call
   */
  public ApiResponse<BlockContent> blocksLatestGetWithHttpInfo() throws ApiException {
    HttpRequest.Builder localVarRequestBuilder = blocksLatestGetRequestBuilder();
    try {
      HttpResponse<InputStream> localVarResponse = memberVarHttpClient.send(
          localVarRequestBuilder.build(),
          HttpResponse.BodyHandlers.ofInputStream());
      if (memberVarResponseInterceptor != null) {
        memberVarResponseInterceptor.accept(localVarResponse);
      }
      if (localVarResponse.statusCode()/ 100 != 2) {
        throw new ApiException(localVarResponse.statusCode(),
            "blocksLatestGet call received non-success response",
            localVarResponse.headers(),
            localVarResponse.body() == null ? null : new String(localVarResponse.body().readAllBytes()));
      }
      return new ApiResponse<BlockContent>(
          localVarResponse.statusCode(),
          localVarResponse.headers().map(),
          memberVarObjectMapper.readValue(localVarResponse.body(), new TypeReference<BlockContent>() {})
        );
    } catch (IOException e) {
      throw new ApiException(e);
    }
    catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new ApiException(e);
    }
  }

  private HttpRequest.Builder blocksLatestGetRequestBuilder() throws ApiException {

    HttpRequest.Builder localVarRequestBuilder = HttpRequest.newBuilder();

    String localVarPath = "/blocks/latest";

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
}