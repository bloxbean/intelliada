package com.bloxbean.intelliada.idea.scripts.service;

import co.nstant.in.cbor.CborDecoder;
import co.nstant.in.cbor.model.Array;
import co.nstant.in.cbor.model.DataItem;
import com.bloxbean.cardano.client.crypto.SecretKey;
import com.bloxbean.cardano.client.crypto.VerificationKey;
import com.bloxbean.cardano.client.exception.CborSerializationException;
import com.bloxbean.cardano.client.transaction.spec.script.NativeScript;
import com.bloxbean.cardano.client.transaction.spec.script.ScriptType;
import com.bloxbean.cardano.client.util.HexUtil;
import com.bloxbean.intelliada.idea.util.JsonUtil;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.intellij.openapi.util.text.StringUtil;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Objects;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScriptInfo {
    private String uuid;
    private String name;
    private ScriptType type;
    private String scriptCbor;
    private NativeScript script;
    private SecretKey skey;
    private VerificationKey vKey;
    private String address;

    @JsonIgnore
    public NativeScript getScript() {
        if (script != null)
            return script;

        if (scriptCbor == null)
            return null;

        try {
            byte[] cborBytes = HexUtil.decodeHexString(scriptCbor);
            List<DataItem> dataItemList = CborDecoder.decode(cborBytes);
            if (dataItemList == null || dataItemList.size() == 0)
                return null;

            Array array = (Array) dataItemList.get(0);
            return NativeScript.deserialize(array);
        } catch (Exception e) {
//            e.printStackTrace();
            return null;
        }
    }

    @JsonIgnore
    public void setScript(NativeScript script) {
        this.script = script;
        try {
            scriptCbor = HexUtil.encodeHexString(this.script.serializeScriptBody());
        } catch (CborSerializationException e) {
            e.printStackTrace();
        }
    }

    public String getScriptCbor() {
        if (scriptCbor == null && script != null) {
            try {
                scriptCbor = HexUtil.encodeHexString(this.script.serializeScriptBody());
            } catch (CborSerializationException e) {
                e.printStackTrace();
            }
            return scriptCbor;
        } else {
            return scriptCbor;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScriptInfo that = (ScriptInfo) o;
        return uuid.equals(that.uuid);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid);
    }

    //Used for list
    public String toString() {
        String str = name + " : " + type;
        if (type == ScriptType.sig) {
            if (StringUtil.isEmpty(address)) {
                str += " :- (vkey) " + vKey.getCborHex();
            } else {
                str += " :- (address) " + address;
            }
        }

        return str;
    }

    public String printFormatted() {
        StringBuilder sb = new StringBuilder();
        sb.append("------------------ Policy Script ------------------\n");
        sb.append(JsonUtil.getPrettyJson(this.getScript()));
        sb.append("\n\n");

        try {
            String policyId = getScript().getPolicyId();
            sb.append("------------------ Policy Id ------------------\n");
            sb.append(policyId);
            sb.append("\n\n");
        } catch (Exception e) {
        }

        if (StringUtil.isEmpty(this.getAddress())) {
            if (this.getSkey() != null) {
                sb.append("------------------  Secret Key   ------------------\n");
                sb.append(JsonUtil.getPrettyJson(this.getSkey()));
                sb.append("\n\n");
            }

            if (this.getVKey() != null) {
                sb.append("------------------  Verification Key   ------------\n");
                sb.append(JsonUtil.getPrettyJson(this.getVKey()));
                sb.append("\n\n");
            }
        }

        return sb.toString();
    }
}
