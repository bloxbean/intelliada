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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.math.BigDecimal;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


/**
 * GenesisContent
 */
@JsonPropertyOrder({
  GenesisContent.JSON_PROPERTY_ACTIVE_SLOTS_COEFFICIENT,
  GenesisContent.JSON_PROPERTY_UPDATE_QUORUM,
  GenesisContent.JSON_PROPERTY_MAX_LOVELACE_SUPPLY,
  GenesisContent.JSON_PROPERTY_NETWORK_MAGIC,
  GenesisContent.JSON_PROPERTY_EPOCH_LENGTH,
  GenesisContent.JSON_PROPERTY_SYSTEM_START,
  GenesisContent.JSON_PROPERTY_SLOTS_PER_KES_PERIOD,
  GenesisContent.JSON_PROPERTY_SLOT_LENGTH,
  GenesisContent.JSON_PROPERTY_MAX_KES_EVOLUTIONS,
  GenesisContent.JSON_PROPERTY_SECURITY_PARAM
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-04-26T22:42:12.889086+08:00[Asia/Singapore]")
public class GenesisContent {
  public static final String JSON_PROPERTY_ACTIVE_SLOTS_COEFFICIENT = "active_slots_coefficient";
  private BigDecimal activeSlotsCoefficient;

  public static final String JSON_PROPERTY_UPDATE_QUORUM = "update_quorum";
  private Integer updateQuorum;

  public static final String JSON_PROPERTY_MAX_LOVELACE_SUPPLY = "max_lovelace_supply";
  private String maxLovelaceSupply;

  public static final String JSON_PROPERTY_NETWORK_MAGIC = "network_magic";
  private Integer networkMagic;

  public static final String JSON_PROPERTY_EPOCH_LENGTH = "epoch_length";
  private Integer epochLength;

  public static final String JSON_PROPERTY_SYSTEM_START = "system_start";
  private Integer systemStart;

  public static final String JSON_PROPERTY_SLOTS_PER_KES_PERIOD = "slots_per_kes_period";
  private Integer slotsPerKesPeriod;

  public static final String JSON_PROPERTY_SLOT_LENGTH = "slot_length";
  private Integer slotLength;

  public static final String JSON_PROPERTY_MAX_KES_EVOLUTIONS = "max_kes_evolutions";
  private Integer maxKesEvolutions;

  public static final String JSON_PROPERTY_SECURITY_PARAM = "security_param";
  private Integer securityParam;


  public GenesisContent activeSlotsCoefficient(BigDecimal activeSlotsCoefficient) {
    this.activeSlotsCoefficient = activeSlotsCoefficient;
    return this;
  }

   /**
   * The proportion of slots in which blocks should be issued
   * @return activeSlotsCoefficient
  **/
  @ApiModelProperty(example = "0.05", required = true, value = "The proportion of slots in which blocks should be issued")
  @JsonProperty(JSON_PROPERTY_ACTIVE_SLOTS_COEFFICIENT)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public BigDecimal getActiveSlotsCoefficient() {
    return activeSlotsCoefficient;
  }


  public void setActiveSlotsCoefficient(BigDecimal activeSlotsCoefficient) {
    this.activeSlotsCoefficient = activeSlotsCoefficient;
  }


  public GenesisContent updateQuorum(Integer updateQuorum) {
    this.updateQuorum = updateQuorum;
    return this;
  }

   /**
   * Determines the quorum needed for votes on the protocol parameter updates
   * @return updateQuorum
  **/
  @ApiModelProperty(example = "5", required = true, value = "Determines the quorum needed for votes on the protocol parameter updates")
  @JsonProperty(JSON_PROPERTY_UPDATE_QUORUM)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getUpdateQuorum() {
    return updateQuorum;
  }


  public void setUpdateQuorum(Integer updateQuorum) {
    this.updateQuorum = updateQuorum;
  }


  public GenesisContent maxLovelaceSupply(String maxLovelaceSupply) {
    this.maxLovelaceSupply = maxLovelaceSupply;
    return this;
  }

   /**
   * The total number of lovelace in the system
   * @return maxLovelaceSupply
  **/
  @ApiModelProperty(example = "45000000000000000", required = true, value = "The total number of lovelace in the system")
  @JsonProperty(JSON_PROPERTY_MAX_LOVELACE_SUPPLY)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getMaxLovelaceSupply() {
    return maxLovelaceSupply;
  }


  public void setMaxLovelaceSupply(String maxLovelaceSupply) {
    this.maxLovelaceSupply = maxLovelaceSupply;
  }


  public GenesisContent networkMagic(Integer networkMagic) {
    this.networkMagic = networkMagic;
    return this;
  }

   /**
   * Network identifier
   * @return networkMagic
  **/
  @ApiModelProperty(example = "764824073", required = true, value = "Network identifier")
  @JsonProperty(JSON_PROPERTY_NETWORK_MAGIC)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getNetworkMagic() {
    return networkMagic;
  }


  public void setNetworkMagic(Integer networkMagic) {
    this.networkMagic = networkMagic;
  }


  public GenesisContent epochLength(Integer epochLength) {
    this.epochLength = epochLength;
    return this;
  }

   /**
   * Number of slots in an epoch
   * @return epochLength
  **/
  @ApiModelProperty(example = "432000", required = true, value = "Number of slots in an epoch")
  @JsonProperty(JSON_PROPERTY_EPOCH_LENGTH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getEpochLength() {
    return epochLength;
  }


  public void setEpochLength(Integer epochLength) {
    this.epochLength = epochLength;
  }


  public GenesisContent systemStart(Integer systemStart) {
    this.systemStart = systemStart;
    return this;
  }

   /**
   * Time of slot 0 in UNIX time
   * @return systemStart
  **/
  @ApiModelProperty(example = "1506203091", required = true, value = "Time of slot 0 in UNIX time")
  @JsonProperty(JSON_PROPERTY_SYSTEM_START)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getSystemStart() {
    return systemStart;
  }


  public void setSystemStart(Integer systemStart) {
    this.systemStart = systemStart;
  }


  public GenesisContent slotsPerKesPeriod(Integer slotsPerKesPeriod) {
    this.slotsPerKesPeriod = slotsPerKesPeriod;
    return this;
  }

   /**
   * Number of slots in an KES period
   * @return slotsPerKesPeriod
  **/
  @ApiModelProperty(example = "129600", required = true, value = "Number of slots in an KES period")
  @JsonProperty(JSON_PROPERTY_SLOTS_PER_KES_PERIOD)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getSlotsPerKesPeriod() {
    return slotsPerKesPeriod;
  }


  public void setSlotsPerKesPeriod(Integer slotsPerKesPeriod) {
    this.slotsPerKesPeriod = slotsPerKesPeriod;
  }


  public GenesisContent slotLength(Integer slotLength) {
    this.slotLength = slotLength;
    return this;
  }

   /**
   * Duration of one slot in seconds
   * @return slotLength
  **/
  @ApiModelProperty(example = "1", required = true, value = "Duration of one slot in seconds")
  @JsonProperty(JSON_PROPERTY_SLOT_LENGTH)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getSlotLength() {
    return slotLength;
  }


  public void setSlotLength(Integer slotLength) {
    this.slotLength = slotLength;
  }


  public GenesisContent maxKesEvolutions(Integer maxKesEvolutions) {
    this.maxKesEvolutions = maxKesEvolutions;
    return this;
  }

   /**
   * The maximum number of time a KES key can be evolved before a pool operator must create a new operational certificate
   * @return maxKesEvolutions
  **/
  @ApiModelProperty(example = "62", required = true, value = "The maximum number of time a KES key can be evolved before a pool operator must create a new operational certificate")
  @JsonProperty(JSON_PROPERTY_MAX_KES_EVOLUTIONS)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getMaxKesEvolutions() {
    return maxKesEvolutions;
  }


  public void setMaxKesEvolutions(Integer maxKesEvolutions) {
    this.maxKesEvolutions = maxKesEvolutions;
  }


  public GenesisContent securityParam(Integer securityParam) {
    this.securityParam = securityParam;
    return this;
  }

   /**
   * Security parameter k
   * @return securityParam
  **/
  @ApiModelProperty(example = "2160", required = true, value = "Security parameter k")
  @JsonProperty(JSON_PROPERTY_SECURITY_PARAM)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getSecurityParam() {
    return securityParam;
  }


  public void setSecurityParam(Integer securityParam) {
    this.securityParam = securityParam;
  }


  /**
   * Return true if this genesis_content object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GenesisContent genesisContent = (GenesisContent) o;
    return Objects.equals(this.activeSlotsCoefficient, genesisContent.activeSlotsCoefficient) &&
        Objects.equals(this.updateQuorum, genesisContent.updateQuorum) &&
        Objects.equals(this.maxLovelaceSupply, genesisContent.maxLovelaceSupply) &&
        Objects.equals(this.networkMagic, genesisContent.networkMagic) &&
        Objects.equals(this.epochLength, genesisContent.epochLength) &&
        Objects.equals(this.systemStart, genesisContent.systemStart) &&
        Objects.equals(this.slotsPerKesPeriod, genesisContent.slotsPerKesPeriod) &&
        Objects.equals(this.slotLength, genesisContent.slotLength) &&
        Objects.equals(this.maxKesEvolutions, genesisContent.maxKesEvolutions) &&
        Objects.equals(this.securityParam, genesisContent.securityParam);
  }

  @Override
  public int hashCode() {
    return Objects.hash(activeSlotsCoefficient, updateQuorum, maxLovelaceSupply, networkMagic, epochLength, systemStart, slotsPerKesPeriod, slotLength, maxKesEvolutions, securityParam);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class GenesisContent {\n");
    sb.append("    activeSlotsCoefficient: ").append(toIndentedString(activeSlotsCoefficient)).append("\n");
    sb.append("    updateQuorum: ").append(toIndentedString(updateQuorum)).append("\n");
    sb.append("    maxLovelaceSupply: ").append(toIndentedString(maxLovelaceSupply)).append("\n");
    sb.append("    networkMagic: ").append(toIndentedString(networkMagic)).append("\n");
    sb.append("    epochLength: ").append(toIndentedString(epochLength)).append("\n");
    sb.append("    systemStart: ").append(toIndentedString(systemStart)).append("\n");
    sb.append("    slotsPerKesPeriod: ").append(toIndentedString(slotsPerKesPeriod)).append("\n");
    sb.append("    slotLength: ").append(toIndentedString(slotLength)).append("\n");
    sb.append("    maxKesEvolutions: ").append(toIndentedString(maxKesEvolutions)).append("\n");
    sb.append("    securityParam: ").append(toIndentedString(securityParam)).append("\n");
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

