package com.bloxbean.intelliada.idea.nodeint.service.impl;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;
import com.bloxbean.intelliada.idea.nodeint.exception.TargetNodeNotConfigured;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.api.NetworkInfoService;
import com.intellij.openapi.project.Project;

public class NetworkServiceImpl extends NodeBaseService implements NetworkInfoService {

    public NetworkServiceImpl(RemoteNode node, LogListener logListener) throws TargetNodeNotConfigured {
        super(node, logListener);
    }

    public NetworkServiceImpl(Project project, LogListener logListener) throws TargetNodeNotConfigured {
        super(project, logListener);
    }

    @Override
    public Result<Genesis> getNetworkInfo() throws ApiCallException {
        try {
            return backendService.getNetworkInfoService().getNetworkInfo();
        } catch (ApiException e) {
            throw new ApiCallException("Error getting network info", e);
        }
    }

    @Override
    public Long getTtl() throws ApiCallException {
        Result<Block> result = null;
        try {
            result = backendService.getBlockService().getLastestBlock();
        } catch (ApiException e) {
            throw new ApiCallException("Could not get block information from the network : \n" + result.toString());
        }

        if(result.isSuccessful()) {
            long latestSlot = result.getValue().getSlot();
            return latestSlot + 1000;
        } else {
            if(result != null)
                logListener.error(result.toString());
            throw new ApiCallException("Could not get block information from the network : \n" + result.toString());
        }
    }

    @Override
    public Long getCurrentSlot() throws ApiCallException {
        Result<Block> result = null;
        try {
            result = backendService.getBlockService().getLastestBlock();
        } catch (ApiException e) {
            throw new ApiCallException("Could not get block information from the network : \n" + result.toString());
        }

        if(result.isSuccessful()) {
            long latestSlot = result.getValue().getSlot();
            return latestSlot;
        } else {
            if(result != null)
                logListener.error(result.toString());
            throw new ApiCallException("Could not get block information from the network : \n" + result.toString());
        }
    }

    @Override
    public Result<Genesis> testAndGetNetworkInfo() throws ApiCallException {
        Result<Genesis> genesisResult = getNetworkInfo();
        clearCachedBackendService();
        return genesisResult;
    }
}
