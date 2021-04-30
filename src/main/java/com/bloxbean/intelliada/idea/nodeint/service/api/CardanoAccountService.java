package com.bloxbean.intelliada.idea.nodeint.service.api;

import com.bloxbean.intelliada.idea.nodeint.model.Result;

public interface CardanoAccountService {
    public Result<Long> getBalance(String address);
}
