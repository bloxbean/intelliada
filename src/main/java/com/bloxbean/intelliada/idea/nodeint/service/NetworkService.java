package com.bloxbean.intelliada.idea.nodeint.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NetworkService {

//    public Object getNetworkInfo(RemoteNode remoteNode) throws Exception {
//        ActorSystem as = ActorSystem.create();
//        ExecutorService es = Executors.newFixedThreadPool(1);
//        CardanoApiBuilder builder =
//                CardanoApiBuilder.create(remoteNode.getWalletApiEndpoint())
//                        .withActorSystem(as) // <- ActorSystem optional
//                        .withExecutorService(es); // <- ExecutorService optional
//
//        CardanoApi api = builder.build();
//        CardanoApiCodec.NetworkInfo networkInfo = api.networkInfo().toCompletableFuture().get();
//        return networkInfo;
//
////        Retrofit retrofit = new Retrofit.Builder()
////                .baseUrl(remoteNode.getWalletApiEndpoint())
////                .build();
////
////        NetworkInfoService service = retrofit.create(NetworkInfoService.class);
////        Object body = service.networkInformation().execute().body();
////
////        return body;
//    }
}
