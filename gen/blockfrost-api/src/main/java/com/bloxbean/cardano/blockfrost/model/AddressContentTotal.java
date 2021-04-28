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


package com.bloxbean.cardano.blockfrost.model;

import java.util.Objects;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import com.bloxbean.cardano.blockfrost.model.TxContentOutputAmount;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * AddressContentTotal
 */
@JsonPropertyOrder({
  AddressContentTotal.JSON_PROPERTY_RECEIVED_SUM,
  AddressContentTotal.JSON_PROPERTY_SENT_SUM,
  AddressContentTotal.JSON_PROPERTY_TX_COUNT
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-04-26T22:42:12.889086+08:00[Asia/Singapore]")
public class AddressContentTotal {
  public static final String JSON_PROPERTY_RECEIVED_SUM = "received_sum";
  private List<TxContentOutputAmount> receivedSum = new ArrayList<>();

  public static final String JSON_PROPERTY_SENT_SUM = "sent_sum";
  private List<TxContentOutputAmount> sentSum = new ArrayList<>();

  public static final String JSON_PROPERTY_TX_COUNT = "tx_count";
  private Integer txCount;


  public AddressContentTotal receivedSum(List<TxContentOutputAmount> receivedSum) {
    this.receivedSum = receivedSum;
    return this;
  }

  public AddressContentTotal addReceivedSumItem(TxContentOutputAmount receivedSumItem) {
    this.receivedSum.add(receivedSumItem);
    return this;
  }

   /**
   * Get receivedSum
   * @return receivedSum
  **/
  @ApiModelProperty(example = "[{\"unit\":\"lovelace\",\"quantity\":\"42000000\"},{\"unit\":\"b0d07d45fe9514f80213f4020e5a61241458be626841cde717cb38a76e7574636f696e\",\"quantity\":\"12\"}]", required = true, value = "")
  @JsonProperty(JSON_PROPERTY_RECEIVED_SUM)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public List<TxContentOutputAmount> getReceivedSum() {
    return receivedSum;
  }


  public void setReceivedSum(List<TxContentOutputAmount> receivedSum) {
    this.receivedSum = receivedSum;
  }


  public AddressContentTotal sentSum(List<TxContentOutputAmount> sentSum) {
    this.sentSum = sentSum;
    return this;
  }

  public AddressContentTotal addSentSumItem(TxContentOutputAmount sentSumItem) {
    this.sentSum.add(sentSumItem);
    return this;
  }

   /**
   * Get sentSum
   * @return sentSum
  **/
  @ApiModelProperty(example = "[{\"unit\":\"lovelace\",\"quantity\":\"42000000\"},{\"unit\":\"b0d07d45fe9514f80213f4020e5a61241458be626841cde717cb38a76e7574636f696e\",\"quantity\":\"12\"}]", required = true, value = "")
  @JsonProperty(JSON_PROPERTY_SENT_SUM)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public List<TxContentOutputAmount> getSentSum() {
    return sentSum;
  }


  public void setSentSum(List<TxContentOutputAmount> sentSum) {
    this.sentSum = sentSum;
  }


  public AddressContentTotal txCount(Integer txCount) {
    this.txCount = txCount;
    return this;
  }

   /**
   * Count of all transactions on the address
   * @return txCount
  **/
  @ApiModelProperty(example = "12", required = true, value = "Count of all transactions on the address")
  @JsonProperty(JSON_PROPERTY_TX_COUNT)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getTxCount() {
    return txCount;
  }


  public void setTxCount(Integer txCount) {
    this.txCount = txCount;
  }


  /**
   * Return true if this address_content_total object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    AddressContentTotal addressContentTotal = (AddressContentTotal) o;
    return Objects.equals(this.receivedSum, addressContentTotal.receivedSum) &&
        Objects.equals(this.sentSum, addressContentTotal.sentSum) &&
        Objects.equals(this.txCount, addressContentTotal.txCount);
  }

  @Override
  public int hashCode() {
    return Objects.hash(receivedSum, sentSum, txCount);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class AddressContentTotal {\n");
    sb.append("    receivedSum: ").append(toIndentedString(receivedSum)).append("\n");
    sb.append("    sentSum: ").append(toIndentedString(sentSum)).append("\n");
    sb.append("    txCount: ").append(toIndentedString(txCount)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(Object o) {
    if (o == null) {
      return "null";
    }
    return o.toString().replace("\n", "\n    ");
  }

}
