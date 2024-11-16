package com.bloxbean.intelliada.idea.util;

import com.bloxbean.cardano.client.transaction.model.PaymentTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static com.bloxbean.intelliada.idea.util.AdaConversionUtil.LOVELACE;

public class JsonUtilTest {

    @Test
    public void getPrettyJson() {
        PaymentTransaction transaction = PaymentTransaction.builder()
                .receiver("addr_testkdsfjsldjfsjfslfsldfs")
                .fee(BigInteger.valueOf(800000))
                .unit(LOVELACE)
                .build();

        System.out.println(JsonUtil.getPrettyJson(transaction));
    }
}
