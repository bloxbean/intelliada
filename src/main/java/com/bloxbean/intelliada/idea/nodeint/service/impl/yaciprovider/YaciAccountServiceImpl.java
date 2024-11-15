package com.bloxbean.intelliada.idea.nodeint.service.impl.yaciprovider;

import com.bloxbean.cardano.client.api.common.OrderEnum;
import com.bloxbean.cardano.client.api.exception.ApiException;
import com.bloxbean.cardano.client.api.model.Result;
import com.bloxbean.cardano.client.api.model.Utxo;
import com.bloxbean.cardano.client.api.util.AssetUtil;
import com.bloxbean.cardano.client.transaction.spec.Asset;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;
import com.bloxbean.intelliada.idea.nodeint.exception.TargetNodeNotConfigured;
import com.bloxbean.intelliada.idea.nodeint.service.api.CardanoAccountService;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.api.model.AssetBalance;
import com.bloxbean.intelliada.idea.nodeint.service.impl.yaciprovider.model.AddressBalanceDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.project.Project;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.bloxbean.intelliada.idea.util.AdaConversionUtil.LOVELACE;

public class YaciAccountServiceImpl extends YaciBaseService implements CardanoAccountService {

    public YaciAccountServiceImpl(Project project) throws TargetNodeNotConfigured {
        super(project);
    }

    public YaciAccountServiceImpl(RemoteNode remoteNode, LogListener logListener) throws TargetNodeNotConfigured {
        super(remoteNode, logListener);
    }

    @Override
    public Result<Long> getAdaBalance(String address) {
        try {
            String apiUrl = baseUrl + "addresses/" + address + "/balance";
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(apiUrl))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                AddressBalanceDto addressBalanceDto = mapper.readValue(response.body(), AddressBalanceDto.class);
                if (addressBalanceDto != null)
                    return addressBalanceDto.getAmounts().stream()
                            .filter(amt -> amt.getUnit().equals(LOVELACE))
                            .map(amt -> amt.getQuantity())
                            .findFirst()
                            .map(amount -> Result.success(amount.toString()).withValue(amount).code(200))
                            .orElse(Result.error("No lovelace amount found"));
                else
                    return Result.error("No lovelace amount found");
            } else {
                return Result.error("GET request failed with status code: " + response.statusCode());
            }
        } catch (Exception e) {
            return Result.error("Error getting account balance : " + e.getMessage());
        }

    }

    @Override
    public List<AssetBalance> getBalance(String address) throws ApiCallException {
        try {
            String apiUrl = baseUrl + "addresses/" + address + "/balance";
            HttpClient client = HttpClient.newHttpClient();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(apiUrl))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                AddressBalanceDto addressBalanceDto = mapper.readValue(response.body(), AddressBalanceDto.class);
                if (addressBalanceDto != null) {
                    var amts = addressBalanceDto.getAmounts();
                    List<AssetBalance> assetBalances = new ArrayList<>();
                    if (amts != null) {
                        for (var amt : amts) {
                            if (!amt.getUnit().equals(LOVELACE)) {
                                AssetBalance assetBalance = new AssetBalance();
                                assetBalance.setUnit(amt.getUnit());

                                var policyAssetName = AssetUtil.getPolicyIdAndAssetName(amt.getUnit());
                                assetBalance.setPolicy(policyAssetName._1);
                                assetBalance.setAssetName(new String(HexUtil.decodeHexString(policyAssetName._2)));
                                assetBalance.setQuantity(amt.getQuantity());

                                Asset asset = new Asset(assetBalance.getAssetName(), amt.getQuantity());
                                assetBalance.setAssetName(asset.getName());
                                assetBalance.setFingerPrint(AssetUtil.calculateFingerPrint(assetBalance.getPolicy(), asset.getNameAsHex()));
                                assetBalances.add(assetBalance);
                            } else {
                                AssetBalance assetBalance = new AssetBalance();
                                assetBalance.setUnit(LOVELACE);
                                assetBalance.setQuantity(amt.getQuantity());
                                assetBalances.add(0, assetBalance);
                            }
                        }
                    }

                    return assetBalances;

                } else
                    return Collections.emptyList();
            } else {
                return Collections.emptyList();
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public List<Utxo> getUtxos(String address, int count, int page, OrderEnum order) throws ApiCallException {
        try {
            var result = backendService.getUtxoService().getUtxos(address, count, page, order);
            if (result.isSuccessful()) {
                return result.getValue();
            } else {
                throw new ApiCallException(result.getResponse());
            }
        } catch (ApiException e) {
            throw new ApiCallException("Error getting UTXOs : " + e.getMessage());
        }
    }

    @Override
    public List<String> getRecentTransactions(String address, int count, int page, OrderEnum order) throws ApiCallException {
        try {
            var result = backendService.getAddressService().getTransactions(address, count, page, order);
            if (result.isSuccessful()) {
                return result.getValue()
                        .stream()
                        .map(tx -> tx.getTxHash())
                        .toList();
            } else {
                throw new ApiCallException(result.getResponse());
            }
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
