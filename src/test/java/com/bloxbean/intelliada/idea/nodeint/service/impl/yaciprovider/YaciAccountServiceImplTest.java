package com.bloxbean.intelliada.idea.nodeint.service.impl.yaciprovider;

import com.bloxbean.intelliada.idea.configuration.model.RemoteNode;
import com.bloxbean.intelliada.idea.nodeint.exception.ApiCallException;
import com.bloxbean.intelliada.idea.nodeint.exception.TargetNodeNotConfigured;
import com.bloxbean.intelliada.idea.nodeint.service.api.LogListener;
import com.bloxbean.intelliada.idea.nodeint.service.api.model.AssetBalance;
import junit.framework.TestCase;

import java.util.List;

public class YaciAccountServiceImplTest extends TestCase {

    public static final String BASE_URL = "http://localhost:8080/api/v1/";

    public void testGetAdaBalance() throws TargetNodeNotConfigured {
        var remoteNode = new RemoteNode();
        remoteNode.setApiEndpoint(BASE_URL);
        YaciAccountServiceImpl yaciAccountServiceImpl = new YaciAccountServiceImpl(remoteNode, new DummyLogListener());

        var result = yaciAccountServiceImpl.getAdaBalance("addr_test1qrh3nrahcd0pj6ps3g9htnlw2jjxuylgdhfn2s5rxqyrr43yzewr2766qsfeq6stl65t546cwvclpqm2rpkkxtksgxuq90xn5f");
        System.out.println(result);
        assertTrue(result.isSuccessful());
    }

    public void testAssets() throws TargetNodeNotConfigured, ApiCallException {
        var remoteNode = new RemoteNode();
        remoteNode.setApiEndpoint("http://localhost:8080/api/v1/");
        YaciAccountServiceImpl yaciAccountServiceImpl = new YaciAccountServiceImpl(remoteNode, new DummyLogListener());

        List<AssetBalance> assetBalances = yaciAccountServiceImpl.getBalance("addr_test1qrh3nrahcd0pj6ps3g9htnlw2jjxuylgdhfn2s5rxqyrr43yzewr2766qsfeq6stl65t546cwvclpqm2rpkkxtksgxuq90xn5f");
        System.out.println(assetBalances);
    }

    class DummyLogListener implements LogListener {

        @Override
        public void info(String msg) {

        }

        @Override
        public void error(String msg) {

        }

        @Override
        public void warn(String msg) {

        }
    }
}
