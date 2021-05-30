package com.bloxbean.intelliada.idea.nodeint.service.api;


import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.intelliada.idea.nodeint.service.api.model.AssetBalance;

import java.util.List;

public interface CardanoAccountService {
    public Result<Long> getAdaBalance(String address);

    public List<AssetBalance> getBalance(String address);
}
