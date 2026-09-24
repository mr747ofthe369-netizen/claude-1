package com.memetaillab.beta1;

import java.math.BigInteger;
import java.util.Arrays;

final class Base58 {
    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger FIFTY_EIGHT = BigInteger.valueOf(58);

    static String encode(byte[] input) {
        if (input == null || input.length == 0) {
            return "";
        }
        int zeros = 0;
        while (zeros < input.length && input[zeros] == 0) {
            zeros++;
        }
        BigInteger n = new BigInteger(1, input);
        StringBuilder s = new StringBuilder();
        while (n.signum() > 0) {
            BigInteger[] qr = n.divideAndRemainder(FIFTY_EIGHT);
            s.append(ALPHABET.charAt(qr[1].intValue()));
            n = qr[0];
        }
        for (int i = 0; i < zeros; i++) {
            s.append('1');
        }
        return s.reverse().toString();
    }

    static byte[] decode(String s) {
        if (s == null || s.isEmpty()) {
            return new byte[0];
        }
        int zeros = 0;
        while (zeros < s.length() && s.charAt(zeros) == '1') {
            zeros++;
        }
        BigInteger n = BigInteger.ZERO;
        for (int i = zeros; i < s.length(); i++) {
            int d = ALPHABET.indexOf(s.charAt(i));
            if (d < 0) {
                throw new IllegalArgumentException("Invalid Base58 character");
            }
            n = n.multiply(FIFTY_EIGHT).add(BigInteger.valueOf(d));
        }
        byte[] raw = n.equals(BigInteger.ZERO) ? new byte[0] : n.toByteArray();
        if (raw.length > 0 && raw[0] == 0) {
            raw = Arrays.copyOfRange(raw, 1, raw.length);
        }
        byte[] out = new byte[raw.length + zeros];
        System.arraycopy(raw, 0, out, zeros, raw.length);
        return out;
    }

    private Base58() {
    }
}
