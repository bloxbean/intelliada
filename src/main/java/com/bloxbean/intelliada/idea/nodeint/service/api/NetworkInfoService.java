package com.bloxbean.intelliada.idea.nodeint.service.api;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;

public interface NetworkInfoService {
    public Result<Genesis> getNetworkInfo() throws ApiException;
}
