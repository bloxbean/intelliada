package com.bloxbean.intelliada.idea.nodeint.service.impl;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.api.NetworkInfoService;

public class NetworkServiceImpl extends NodeBaseService implements NetworkInfoService {

    public NetworkServiceImpl(RemoteNode remoteNode, LogListener logListener) {
        super(remoteNode, logListener);
    }

    @Override
    public Result<Genesis> getNetworkInfo() throws ApiException {
        return backendService.getNetworkInfoService().getNetworkInfo();
    }
}
