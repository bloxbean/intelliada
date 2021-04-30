package com.bloxbean.intelliada.idea.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;

public class AdaConversionUtil {
    public final static String LOVELACE = "lovelace";

    private static final BigInteger ONE_ADA = new BigInteger("1000000"); //1 ADA
    private static final int ADA_DECIMAL = 6;

    public static BigInteger adaToLovelace(BigDecimal amount) {
        return assetFromDecimal(amount, ADA_DECIMAL);
    }

    public static String lovelaceToAdaFormatted(BigInteger lovelaceAmt) {
        BigDecimal amtInAda = assetToDecimal(lovelaceAmt, ADA_DECIMAL); //1 Ada = 1000000 lovelace
        return formatBigDecimal(amtInAda, ADA_DECIMAL);
    }

    public static BigDecimal assetToDecimal(BigInteger amount, long decimals) {
        if(decimals == 0)
            return new BigDecimal(amount);

        double oneUnit = Math.pow(10, decimals);

        BigDecimal bigDecimalAmt = new BigDecimal(amount);
        BigDecimal decimalAmt = bigDecimalAmt.divide(new BigDecimal(oneUnit));

        return decimalAmt;
    }

    public static BigInteger assetFromDecimal(BigDecimal doubleAmout, long decimals) {
        if(decimals == 0)
            return doubleAmout.toBigInteger();

        double oneUnit = Math.pow(10, decimals);

        BigDecimal amount = new BigDecimal(oneUnit).multiply(doubleAmout);

        return amount.toBigInteger();
    }

    public static String formatBigDecimal(BigDecimal amount, int decimals) {
        if(amount == null) return null;

        amount = amount.setScale(decimals, BigDecimal.ROUND_DOWN);

        DecimalFormat df = new DecimalFormat();

        df.setMaximumFractionDigits(decimals);

        df.setMinimumFractionDigits(0);

        df.setGroupingUsed(true);

        String result = df.format(amount);
        return result;
    }

    public static String toAssetDecimalAmtFormatted(BigInteger assetAmt, int decimals) {
        BigDecimal assetAmtInDecimals = assetToDecimal(assetAmt, decimals);
        return formatBigDecimal(assetAmtInDecimals, decimals);
    }
}
