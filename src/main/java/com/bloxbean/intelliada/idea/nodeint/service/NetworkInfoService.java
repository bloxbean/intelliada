package com.bloxbean.intelliada.idea.nodeint.service;

import com.bloxbean.intelliada.idea.nodeint.model.Genesis;
import com.bloxbean.intelliada.idea.nodeint.model.Result;

public interface NetworkInfoService {
    public Result<Genesis> getNetworkInfo();
}
