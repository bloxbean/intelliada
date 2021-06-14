package com.bloxbean.intelliada.idea.nodeint.service.impl;

import com.bloxbean.cardano.client.backend.exception.ApiException;
import com.bloxbean.cardano.client.backend.model.Block;
import com.bloxbean.cardano.client.backend.model.Genesis;
import com.bloxbean.cardano.client.backend.model.Result;
import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
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
    public Result<Genesis> getNetworkInfo() throws ApiException {
        return backendService.getNetworkInfoService().getNetworkInfo();
    }

    @Override
    public Long getTtl() throws ApiException {
        Result<Block> result = backendService.getBlockService().getLastestBlock();
        if(result.isSuccessful()) {
            long latestSlot = result.getValue().getSlot();
            return latestSlot + 1000;
        } else {
            if(result != null)
                logListener.error(result.toString());
            throw new ApiException("Could not get block information from the network : \n" + result.toString());
        }
    }

    @Override
    public Long getCurrentSlot() throws ApiException {
        Result<Block> result = backendService.getBlockService().getLastestBlock();
        if(result.isSuccessful()) {
            long latestSlot = result.getValue().getSlot();
            return latestSlot;
        } else {
            if(result != null)
                logListener.error(result.toString());
            throw new ApiException("Could not get block information from the network : \n" + result.toString());
        }
    }
}
