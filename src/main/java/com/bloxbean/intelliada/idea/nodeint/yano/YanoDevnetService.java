package com.bloxbean.intelliada.idea.nodeint.yano;

import com.bloxbean.intelliada.idea.nodeint.yano.model.*;
import com.intellij.openapi.diagnostic.Logger;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * REST client for Yano devnet operations:
 * fund, snapshots, rollback, time advance, epoch shifting, tx evaluation.
 */
public class YanoDevnetService {
    private static final Logger LOG = Logger.getInstance(YanoDevnetService.class);

    private final String baseUrl;
    private final HttpClient httpClient;

    public YanoDevnetService(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = HttpClient.newBuilder().build();
    }

    // ---- Faucet ----

    public FundResponse fundAddress(String address, BigDecimal ada) throws IOException, InterruptedException {
        JSONObject body = new JSONObject();
        body.put("address", address);
        body.put("ada", ada);

        JSONObject resp = post("/api/v1/devnet/fund", body);
        return new FundResponse(
                resp.optString("tx_hash"),
                resp.optInt("index"),
                resp.optLong("lovelace")
        );
    }

    // ---- Snapshots ----

    public SnapshotResponse createSnapshot(String name) throws IOException, InterruptedException {
        JSONObject body = new JSONObject();
        body.put("name", name);
        return parseSnapshot(post("/api/v1/devnet/snapshot", body));
    }

    public void restoreSnapshot(String name) throws IOException, InterruptedException {
        post("/api/v1/devnet/restore/" + name, null);
    }

    public List<SnapshotResponse> listSnapshots() throws IOException, InterruptedException {
        String json = get("/api/v1/devnet/snapshots");
        JSONArray arr = new JSONArray(json);
        List<SnapshotResponse> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            list.add(parseSnapshot(arr.getJSONObject(i)));
        }
        return list;
    }

    public void deleteSnapshot(String name) throws IOException, InterruptedException {
        delete("/api/v1/devnet/snapshot/" + name);
    }

    // ---- Rollback ----

    public RollbackResponse rollbackBySlot(long slot) throws IOException, InterruptedException {
        JSONObject body = new JSONObject();
        body.put("slot", slot);
        return parseRollback(post("/api/v1/devnet/rollback", body));
    }

    public RollbackResponse rollbackByBlockNumber(long blockNumber) throws IOException, InterruptedException {
        JSONObject body = new JSONObject();
        body.put("block_number", blockNumber);
        return parseRollback(post("/api/v1/devnet/rollback", body));
    }

    public RollbackResponse rollbackByCount(int count) throws IOException, InterruptedException {
        JSONObject body = new JSONObject();
        body.put("count", count);
        return parseRollback(post("/api/v1/devnet/rollback", body));
    }

    // ---- Time Advance ----

    public TimeAdvanceResponse advanceBySlots(int slots) throws IOException, InterruptedException {
        JSONObject body = new JSONObject();
        body.put("slots", slots);
        return parseTimeAdvance(post("/api/v1/devnet/time/advance", body));
    }

    public TimeAdvanceResponse advanceBySeconds(int seconds) throws IOException, InterruptedException {
        JSONObject body = new JSONObject();
        body.put("seconds", seconds);
        return parseTimeAdvance(post("/api/v1/devnet/time/advance", body));
    }

    public TimeAdvanceResponse advanceByEpochs(int epochs) throws IOException, InterruptedException {
        JSONObject body = new JSONObject();
        body.put("epochs", epochs);
        return parseTimeAdvance(post("/api/v1/devnet/time/advance", body));
    }

    // ---- Time Travel (Epoch Shifting) ----

    public EpochShiftResponse shiftEpochs(int epochs) throws IOException, InterruptedException {
        JSONObject body = new JSONObject();
        body.put("epochs", epochs);
        JSONObject resp = post("/api/v1/devnet/epochs/shift", body);
        return new EpochShiftResponse(
                resp.optString("message"),
                resp.optLong("shift_millis"),
                resp.optString("new_system_start"),
                resp.optLong("genesis_slot")
        );
    }

    public TimeAdvanceResponse catchUpToWallClock() throws IOException, InterruptedException {
        return parseTimeAdvance(post("/api/v1/devnet/epochs/catch-up", null));
    }

    // ---- Transaction Evaluation ----

    public String evaluateTransaction(String txCborHex) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/utils/txs/evaluate"))
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString(txCborHex))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("Evaluation failed: " + response.body());
        }
        return response.body();
    }

    // ---- Genesis Download ----

    public byte[] downloadGenesis() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/v1/devnet/genesis/download"))
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IOException("Genesis download failed: " + response.statusCode());
        }
        return response.body();
    }

    // ---- Helpers ----

    private JSONObject post(String path, JSONObject body) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json");

        if (body != null) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        } else {
            builder.POST(HttpRequest.BodyPublishers.noBody());
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }

        String respBody = response.body();
        if (respBody == null || respBody.isBlank()) {
            return new JSONObject();
        }
        return new JSONObject(respBody);
    }

    private String get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    private void delete(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .DELETE()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IOException("HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private SnapshotResponse parseSnapshot(JSONObject obj) {
        return new SnapshotResponse(
                obj.optString("name"),
                obj.optLong("slot"),
                obj.optLong("block_number"),
                obj.optString("created_at")
        );
    }

    private RollbackResponse parseRollback(JSONObject obj) {
        return new RollbackResponse(
                obj.optString("message"),
                obj.optLong("slot"),
                obj.optLong("block_number")
        );
    }

    private TimeAdvanceResponse parseTimeAdvance(JSONObject obj) {
        return new TimeAdvanceResponse(
                obj.optString("message"),
                obj.optLong("new_slot"),
                obj.optLong("new_block_number"),
                obj.optInt("blocks_produced")
        );
    }
}
