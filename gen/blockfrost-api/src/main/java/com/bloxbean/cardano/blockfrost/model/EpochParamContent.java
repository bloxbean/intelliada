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
 * EpochParamContent
 */
@JsonPropertyOrder({
  EpochParamContent.JSON_PROPERTY_MIN_FEE_A,
  EpochParamContent.JSON_PROPERTY_MIN_FEE_B,
  EpochParamContent.JSON_PROPERTY_MAX_BLOCK_SIZE,
  EpochParamContent.JSON_PROPERTY_MAX_TX_SIZE,
  EpochParamContent.JSON_PROPERTY_MAX_BLOCK_HEADER_SIZE,
  EpochParamContent.JSON_PROPERTY_KEY_DEPOSIT,
  EpochParamContent.JSON_PROPERTY_POOL_DEPOSIT,
  EpochParamContent.JSON_PROPERTY_E_MAX,
  EpochParamContent.JSON_PROPERTY_N_OPT,
  EpochParamContent.JSON_PROPERTY_A0,
  EpochParamContent.JSON_PROPERTY_RHO,
  EpochParamContent.JSON_PROPERTY_TAU,
  EpochParamContent.JSON_PROPERTY_DECENTRALISATION_PARAM,
  EpochParamContent.JSON_PROPERTY_EXTRA_ENTROPY,
  EpochParamContent.JSON_PROPERTY_PROTOCOL_MAJOR_VER,
  EpochParamContent.JSON_PROPERTY_PROTOCOL_MINOR_VER,
  EpochParamContent.JSON_PROPERTY_MIN_UTXO,
  EpochParamContent.JSON_PROPERTY_MIN_POOL_COST,
  EpochParamContent.JSON_PROPERTY_NONCE
})
@javax.annotation.processing.Generated(value = "org.openapitools.codegen.languages.JavaClientCodegen", date = "2021-04-26T22:42:12.889086+08:00[Asia/Singapore]")
public class EpochParamContent {
  public static final String JSON_PROPERTY_MIN_FEE_A = "min_fee_a";
  private Integer minFeeA;

  public static final String JSON_PROPERTY_MIN_FEE_B = "min_fee_b";
  private Integer minFeeB;

  public static final String JSON_PROPERTY_MAX_BLOCK_SIZE = "max_block_size";
  private Integer maxBlockSize;

  public static final String JSON_PROPERTY_MAX_TX_SIZE = "max_tx_size";
  private Integer maxTxSize;

  public static final String JSON_PROPERTY_MAX_BLOCK_HEADER_SIZE = "max_block_header_size";
  private Integer maxBlockHeaderSize;

  public static final String JSON_PROPERTY_KEY_DEPOSIT = "key_deposit";
  private String keyDeposit;

  public static final String JSON_PROPERTY_POOL_DEPOSIT = "pool_deposit";
  private String poolDeposit;

  public static final String JSON_PROPERTY_E_MAX = "e_max";
  private Integer eMax;

  public static final String JSON_PROPERTY_N_OPT = "n_opt";
  private Integer nOpt;

  public static final String JSON_PROPERTY_A0 = "a0";
  private BigDecimal a0;

  public static final String JSON_PROPERTY_RHO = "rho";
  private BigDecimal rho;

  public static final String JSON_PROPERTY_TAU = "tau";
  private BigDecimal tau;

  public static final String JSON_PROPERTY_DECENTRALISATION_PARAM = "decentralisation_param";
  private BigDecimal decentralisationParam;

  public static final String JSON_PROPERTY_EXTRA_ENTROPY = "extra_entropy";
  private Object extraEntropy;

  public static final String JSON_PROPERTY_PROTOCOL_MAJOR_VER = "protocol_major_ver";
  private Integer protocolMajorVer;

  public static final String JSON_PROPERTY_PROTOCOL_MINOR_VER = "protocol_minor_ver";
  private Integer protocolMinorVer;

  public static final String JSON_PROPERTY_MIN_UTXO = "min_utxo";
  private String minUtxo;

  public static final String JSON_PROPERTY_MIN_POOL_COST = "min_pool_cost";
  private String minPoolCost;

  public static final String JSON_PROPERTY_NONCE = "nonce";
  private String nonce;


  public EpochParamContent minFeeA(Integer minFeeA) {
    this.minFeeA = minFeeA;
    return this;
  }

   /**
   * The linear factor for the minimum fee calculation for given epoch
   * @return minFeeA
  **/
  @ApiModelProperty(example = "44", required = true, value = "The linear factor for the minimum fee calculation for given epoch")
  @JsonProperty(JSON_PROPERTY_MIN_FEE_A)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getMinFeeA() {
    return minFeeA;
  }


  public void setMinFeeA(Integer minFeeA) {
    this.minFeeA = minFeeA;
  }


  public EpochParamContent minFeeB(Integer minFeeB) {
    this.minFeeB = minFeeB;
    return this;
  }

   /**
   * The constant factor for the minimum fee calculation
   * @return minFeeB
  **/
  @ApiModelProperty(example = "155381", required = true, value = "The constant factor for the minimum fee calculation")
  @JsonProperty(JSON_PROPERTY_MIN_FEE_B)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getMinFeeB() {
    return minFeeB;
  }


  public void setMinFeeB(Integer minFeeB) {
    this.minFeeB = minFeeB;
  }


  public EpochParamContent maxBlockSize(Integer maxBlockSize) {
    this.maxBlockSize = maxBlockSize;
    return this;
  }

   /**
   * Maximum block body size in Bytes
   * @return maxBlockSize
  **/
  @ApiModelProperty(example = "65536", required = true, value = "Maximum block body size in Bytes")
  @JsonProperty(JSON_PROPERTY_MAX_BLOCK_SIZE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getMaxBlockSize() {
    return maxBlockSize;
  }


  public void setMaxBlockSize(Integer maxBlockSize) {
    this.maxBlockSize = maxBlockSize;
  }


  public EpochParamContent maxTxSize(Integer maxTxSize) {
    this.maxTxSize = maxTxSize;
    return this;
  }

   /**
   * Maximum transaction size
   * @return maxTxSize
  **/
  @ApiModelProperty(example = "16384", required = true, value = "Maximum transaction size")
  @JsonProperty(JSON_PROPERTY_MAX_TX_SIZE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getMaxTxSize() {
    return maxTxSize;
  }


  public void setMaxTxSize(Integer maxTxSize) {
    this.maxTxSize = maxTxSize;
  }


  public EpochParamContent maxBlockHeaderSize(Integer maxBlockHeaderSize) {
    this.maxBlockHeaderSize = maxBlockHeaderSize;
    return this;
  }

   /**
   * Maximum block header size
   * @return maxBlockHeaderSize
  **/
  @ApiModelProperty(example = "1100", required = true, value = "Maximum block header size")
  @JsonProperty(JSON_PROPERTY_MAX_BLOCK_HEADER_SIZE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getMaxBlockHeaderSize() {
    return maxBlockHeaderSize;
  }


  public void setMaxBlockHeaderSize(Integer maxBlockHeaderSize) {
    this.maxBlockHeaderSize = maxBlockHeaderSize;
  }


  public EpochParamContent keyDeposit(String keyDeposit) {
    this.keyDeposit = keyDeposit;
    return this;
  }

   /**
   * The amount of a key registration deposit in Lovelaces
   * @return keyDeposit
  **/
  @ApiModelProperty(example = "2000000", required = true, value = "The amount of a key registration deposit in Lovelaces")
  @JsonProperty(JSON_PROPERTY_KEY_DEPOSIT)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getKeyDeposit() {
    return keyDeposit;
  }


  public void setKeyDeposit(String keyDeposit) {
    this.keyDeposit = keyDeposit;
  }


  public EpochParamContent poolDeposit(String poolDeposit) {
    this.poolDeposit = poolDeposit;
    return this;
  }

   /**
   * The amount of a pool registration deposit in Lovelaces
   * @return poolDeposit
  **/
  @ApiModelProperty(example = "500000000", required = true, value = "The amount of a pool registration deposit in Lovelaces")
  @JsonProperty(JSON_PROPERTY_POOL_DEPOSIT)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getPoolDeposit() {
    return poolDeposit;
  }


  public void setPoolDeposit(String poolDeposit) {
    this.poolDeposit = poolDeposit;
  }


  public EpochParamContent eMax(Integer eMax) {
    this.eMax = eMax;
    return this;
  }

   /**
   * Epoch bound on pool retirement
   * @return eMax
  **/
  @ApiModelProperty(example = "18", required = true, value = "Epoch bound on pool retirement")
  @JsonProperty(JSON_PROPERTY_E_MAX)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer geteMax() {
    return eMax;
  }


  public void seteMax(Integer eMax) {
    this.eMax = eMax;
  }


  public EpochParamContent nOpt(Integer nOpt) {
    this.nOpt = nOpt;
    return this;
  }

   /**
   * Desired number of pools
   * @return nOpt
  **/
  @ApiModelProperty(example = "150", required = true, value = "Desired number of pools")
  @JsonProperty(JSON_PROPERTY_N_OPT)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getnOpt() {
    return nOpt;
  }


  public void setnOpt(Integer nOpt) {
    this.nOpt = nOpt;
  }


  public EpochParamContent a0(BigDecimal a0) {
    this.a0 = a0;
    return this;
  }

   /**
   * Pool pledge influence
   * @return a0
  **/
  @ApiModelProperty(example = "0.3", required = true, value = "Pool pledge influence")
  @JsonProperty(JSON_PROPERTY_A0)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public BigDecimal getA0() {
    return a0;
  }


  public void setA0(BigDecimal a0) {
    this.a0 = a0;
  }


  public EpochParamContent rho(BigDecimal rho) {
    this.rho = rho;
    return this;
  }

   /**
   * Monetary expansion
   * @return rho
  **/
  @ApiModelProperty(example = "0.003", required = true, value = "Monetary expansion")
  @JsonProperty(JSON_PROPERTY_RHO)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public BigDecimal getRho() {
    return rho;
  }


  public void setRho(BigDecimal rho) {
    this.rho = rho;
  }


  public EpochParamContent tau(BigDecimal tau) {
    this.tau = tau;
    return this;
  }

   /**
   * Treasury expansion
   * @return tau
  **/
  @ApiModelProperty(example = "0.2", required = true, value = "Treasury expansion")
  @JsonProperty(JSON_PROPERTY_TAU)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public BigDecimal getTau() {
    return tau;
  }


  public void setTau(BigDecimal tau) {
    this.tau = tau;
  }


  public EpochParamContent decentralisationParam(BigDecimal decentralisationParam) {
    this.decentralisationParam = decentralisationParam;
    return this;
  }

   /**
   * Percentage of blocks produced by federated nodes
   * @return decentralisationParam
  **/
  @ApiModelProperty(example = "0.5", required = true, value = "Percentage of blocks produced by federated nodes")
  @JsonProperty(JSON_PROPERTY_DECENTRALISATION_PARAM)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public BigDecimal getDecentralisationParam() {
    return decentralisationParam;
  }


  public void setDecentralisationParam(BigDecimal decentralisationParam) {
    this.decentralisationParam = decentralisationParam;
  }


  public EpochParamContent extraEntropy(Object extraEntropy) {
    this.extraEntropy = extraEntropy;
    return this;
  }

   /**
   * Seed for extra entropy
   * @return extraEntropy
  **/
  @javax.annotation.Nullable
  @ApiModelProperty(required = true, value = "Seed for extra entropy")
  @JsonProperty(JSON_PROPERTY_EXTRA_ENTROPY)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Object getExtraEntropy() {
    return extraEntropy;
  }


  public void setExtraEntropy(Object extraEntropy) {
    this.extraEntropy = extraEntropy;
  }


  public EpochParamContent protocolMajorVer(Integer protocolMajorVer) {
    this.protocolMajorVer = protocolMajorVer;
    return this;
  }

   /**
   * Accepted protocol major version
   * @return protocolMajorVer
  **/
  @ApiModelProperty(example = "2", required = true, value = "Accepted protocol major version")
  @JsonProperty(JSON_PROPERTY_PROTOCOL_MAJOR_VER)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getProtocolMajorVer() {
    return protocolMajorVer;
  }


  public void setProtocolMajorVer(Integer protocolMajorVer) {
    this.protocolMajorVer = protocolMajorVer;
  }


  public EpochParamContent protocolMinorVer(Integer protocolMinorVer) {
    this.protocolMinorVer = protocolMinorVer;
    return this;
  }

   /**
   * Accepted protocol minor version
   * @return protocolMinorVer
  **/
  @ApiModelProperty(example = "0", required = true, value = "Accepted protocol minor version")
  @JsonProperty(JSON_PROPERTY_PROTOCOL_MINOR_VER)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public Integer getProtocolMinorVer() {
    return protocolMinorVer;
  }


  public void setProtocolMinorVer(Integer protocolMinorVer) {
    this.protocolMinorVer = protocolMinorVer;
  }


  public EpochParamContent minUtxo(String minUtxo) {
    this.minUtxo = minUtxo;
    return this;
  }

   /**
   * Minimum UTXO value
   * @return minUtxo
  **/
  @ApiModelProperty(example = "1000000", required = true, value = "Minimum UTXO value")
  @JsonProperty(JSON_PROPERTY_MIN_UTXO)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getMinUtxo() {
    return minUtxo;
  }


  public void setMinUtxo(String minUtxo) {
    this.minUtxo = minUtxo;
  }


  public EpochParamContent minPoolCost(String minPoolCost) {
    this.minPoolCost = minPoolCost;
    return this;
  }

   /**
   * Minimum stake cost forced on the pool
   * @return minPoolCost
  **/
  @ApiModelProperty(example = "340000000", required = true, value = "Minimum stake cost forced on the pool")
  @JsonProperty(JSON_PROPERTY_MIN_POOL_COST)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getMinPoolCost() {
    return minPoolCost;
  }


  public void setMinPoolCost(String minPoolCost) {
    this.minPoolCost = minPoolCost;
  }


  public EpochParamContent nonce(String nonce) {
    this.nonce = nonce;
    return this;
  }

   /**
   * Epoch number only used once
   * @return nonce
  **/
  @ApiModelProperty(example = "1a3be38bcbb7911969283716ad7aa550250226b76a61fc51cc9a9a35d9276d81", required = true, value = "Epoch number only used once")
  @JsonProperty(JSON_PROPERTY_NONCE)
  @JsonInclude(value = JsonInclude.Include.ALWAYS)

  public String getNonce() {
    return nonce;
  }


  public void setNonce(String nonce) {
    this.nonce = nonce;
  }


  /**
   * Return true if this epoch_param_content object is equal to o.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    EpochParamContent epochParamContent = (EpochParamContent) o;
    return Objects.equals(this.minFeeA, epochParamContent.minFeeA) &&
        Objects.equals(this.minFeeB, epochParamContent.minFeeB) &&
        Objects.equals(this.maxBlockSize, epochParamContent.maxBlockSize) &&
        Objects.equals(this.maxTxSize, epochParamContent.maxTxSize) &&
        Objects.equals(this.maxBlockHeaderSize, epochParamContent.maxBlockHeaderSize) &&
        Objects.equals(this.keyDeposit, epochParamContent.keyDeposit) &&
        Objects.equals(this.poolDeposit, epochParamContent.poolDeposit) &&
        Objects.equals(this.eMax, epochParamContent.eMax) &&
        Objects.equals(this.nOpt, epochParamContent.nOpt) &&
        Objects.equals(this.a0, epochParamContent.a0) &&
        Objects.equals(this.rho, epochParamContent.rho) &&
        Objects.equals(this.tau, epochParamContent.tau) &&
        Objects.equals(this.decentralisationParam, epochParamContent.decentralisationParam) &&
        Objects.equals(this.extraEntropy, epochParamContent.extraEntropy) &&
        Objects.equals(this.protocolMajorVer, epochParamContent.protocolMajorVer) &&
        Objects.equals(this.protocolMinorVer, epochParamContent.protocolMinorVer) &&
        Objects.equals(this.minUtxo, epochParamContent.minUtxo) &&
        Objects.equals(this.minPoolCost, epochParamContent.minPoolCost) &&
        Objects.equals(this.nonce, epochParamContent.nonce);
  }

  @Override
  public int hashCode() {
    return Objects.hash(minFeeA, minFeeB, maxBlockSize, maxTxSize, maxBlockHeaderSize, keyDeposit, poolDeposit, eMax, nOpt, a0, rho, tau, decentralisationParam, extraEntropy, protocolMajorVer, protocolMinorVer, minUtxo, minPoolCost, nonce);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class EpochParamContent {\n");
    sb.append("    minFeeA: ").append(toIndentedString(minFeeA)).append("\n");
    sb.append("    minFeeB: ").append(toIndentedString(minFeeB)).append("\n");
    sb.append("    maxBlockSize: ").append(toIndentedString(maxBlockSize)).append("\n");
    sb.append("    maxTxSize: ").append(toIndentedString(maxTxSize)).append("\n");
    sb.append("    maxBlockHeaderSize: ").append(toIndentedString(maxBlockHeaderSize)).append("\n");
    sb.append("    keyDeposit: ").append(toIndentedString(keyDeposit)).append("\n");
    sb.append("    poolDeposit: ").append(toIndentedString(poolDeposit)).append("\n");
    sb.append("    eMax: ").append(toIndentedString(eMax)).append("\n");
    sb.append("    nOpt: ").append(toIndentedString(nOpt)).append("\n");
    sb.append("    a0: ").append(toIndentedString(a0)).append("\n");
    sb.append("    rho: ").append(toIndentedString(rho)).append("\n");
    sb.append("    tau: ").append(toIndentedString(tau)).append("\n");
    sb.append("    decentralisationParam: ").append(toIndentedString(decentralisationParam)).append("\n");
    sb.append("    extraEntropy: ").append(toIndentedString(extraEntropy)).append("\n");
    sb.append("    protocolMajorVer: ").append(toIndentedString(protocolMajorVer)).append("\n");
    sb.append("    protocolMinorVer: ").append(toIndentedString(protocolMinorVer)).append("\n");
    sb.append("    minUtxo: ").append(toIndentedString(minUtxo)).append("\n");
    sb.append("    minPoolCost: ").append(toIndentedString(minPoolCost)).append("\n");
    sb.append("    nonce: ").append(toIndentedString(nonce)).append("\n");
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
