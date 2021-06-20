package com.bloxbean.intelliada.idea.nodeint.service.api;

import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;

public interface NetworkInfoService {
    Result<Genesis> getNetworkInfo() throws ApiCallException;
    Long getTtl() throws ApiCallException;
    Long getCurrentSlot() throws ApiCallException;
}
