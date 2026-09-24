package com.memetaillab.beta1;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.util.Base64;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import net.i2p.crypto.eddsa.EdDSAEngine;
import net.i2p.crypto.eddsa.EdDSAPrivateKey;
import net.i2p.crypto.eddsa.spec.EdDSANamedCurveTable;
import net.i2p.crypto.eddsa.spec.EdDSAParameterSpec;
import net.i2p.crypto.eddsa.spec.EdDSAPrivateKeySpec;

final class SecureBotWallet {
    private static final String ALIAS = "meme_tail_live_aes_v1";
    private static final String ENC = "secret_ct";
    private static final String IV = "secret_iv";
    private static final String PUB = "public_key";
    private static final String STORE = "mtl_live_wallet";
    private final Context c;

    SecureBotWallet(Context c) {
        this.c = c.getApplicationContext();
    }

    boolean exists() {
        return prefs().contains(ENC) && !publicKey().isEmpty();
    }

    String publicKey() {
        return prefs().getString(PUB, "");
    }

    String generate() throws Exception {
        EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        SecureRandom r = new SecureRandom();
        byte[] seed = new byte[32];
        r.nextBytes(seed);
        EdDSAPrivateKey priv = new EdDSAPrivateKey(new EdDSAPrivateKeySpec(seed, spec));
        byte[] pub = priv.getAbyte();
        byte[] secret64 = new byte[64];
        System.arraycopy(seed, 0, secret64, 0, 32);
        System.arraycopy(pub, 0, secret64, 32, 32);
        save(secret64, Base58.encode(pub));
        Arrays.fill(seed, (byte) 0);
        Arrays.fill(secret64, (byte) 0);
        return Base58.encode(pub);
    }

    String importBase58(String secret) throws Exception {
        byte[] raw = Base58.decode(secret.trim());
        if (raw.length != 64 && raw.length != 32) {
            throw new IllegalArgumentException("Solana secret must decode to 32-byte seed or 64-byte keypair");
        }
        EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
        byte[] seed = new byte[32];
        System.arraycopy(raw, 0, seed, 0, 32);
        EdDSAPrivateKey priv = new EdDSAPrivateKey(new EdDSAPrivateKeySpec(seed, spec));
        byte[] pub = priv.getAbyte();
        byte[] secret64 = new byte[64];
        System.arraycopy(seed, 0, secret64, 0, 32);
        System.arraycopy(pub, 0, secret64, 32, 32);
        String p = Base58.encode(pub);
        save(secret64, p);
        Arrays.fill(raw, (byte) 0);
        Arrays.fill(seed, (byte) 0);
        Arrays.fill(secret64, (byte) 0);
        return p;
    }

    String exportBase58() throws Exception {
        byte[] raw = load();
        try {
            return Base58.encode(raw);
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    byte[] sign(byte[] message) throws Exception {
        byte[] raw = load();
        try {
            byte[] seed = new byte[32];
            System.arraycopy(raw, 0, seed, 0, 32);
            EdDSAParameterSpec spec = EdDSANamedCurveTable.getByName(EdDSANamedCurveTable.ED_25519);
            PrivateKey priv = new EdDSAPrivateKey(new EdDSAPrivateKeySpec(seed, spec));
            Signature sig = new EdDSAEngine(MessageDigest.getInstance(spec.getHashAlgorithm()));
            sig.initSign(priv);
            sig.update(message);
            return sig.sign();
        } finally {
            Arrays.fill(raw, (byte) 0);
        }
    }

    String signSolanaTransactionBase64(String unsignedB64) throws Exception {
        byte[] tx = Base64.decode(unsignedB64, 0);
        ShortVec sv = readShortVec(tx, 0);
        int count = sv.value;
        if (count < 1) {
            throw new IllegalArgumentException("Transaction has no signer slots");
        }
        int sigStart = sv.bytes;
        int msgStart = (count * 64) + sigStart;
        if (msgStart >= tx.length) {
            throw new IllegalArgumentException("Malformed Solana transaction");
        }
        byte[] msg = Arrays.copyOfRange(tx, msgStart, tx.length);
        byte[] sig = sign(msg);
        System.arraycopy(sig, 0, tx, sigStart, 64);
        return Base64.encodeToString(tx, 2);
    }

    void delete() {
        prefs().edit().clear().apply();
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            ks.deleteEntry(ALIAS);
        } catch (Exception e) {
        }
    }

    private void save(byte[] raw, String publicKey) throws Exception {
        SecretKey k = key();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(1, k);
        byte[] iv = cipher.getIV();
        byte[] ct = cipher.doFinal(raw);
        prefs().edit().putString(ENC, Base64.encodeToString(ct, 2)).putString(IV, Base64.encodeToString(iv, 2)).putString(PUB, publicKey).apply();
    }

    private byte[] load() throws Exception {
        if (!exists()) {
            throw new IllegalStateException("No bot wallet stored");
        }
        byte[] ct = Base64.decode(prefs().getString(ENC, ""), 0);
        byte[] iv = Base64.decode(prefs().getString(IV, ""), 0);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(2, key(), new GCMParameterSpec(128, iv));
        return cipher.doFinal(ct);
    }

    private SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if (!ks.containsAlias(ALIAS)) {
            KeyGenerator kg = KeyGenerator.getInstance("AES", "AndroidKeyStore");
            kg.init(new KeyGenParameterSpec.Builder(ALIAS, 3).setBlockModes("GCM").setEncryptionPaddings("NoPadding").setRandomizedEncryptionRequired(true).build());
            return kg.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) ks.getEntry(ALIAS, null)).getSecretKey();
    }

    private SharedPreferences prefs() {
        return this.c.getSharedPreferences(STORE, 0);
    }

    private static ShortVec readShortVec(byte[] b, int off) {
        int value = 0;
        int shift = 0;
        int i = 0;
        while (off + i < b.length) {
            int x = b[off + i] & 255;
            value |= (x & 127) << shift;
            i++;
            if ((x & 128) != 0) {
                shift += 7;
                if (shift > 28) {
                    break;
                }
            } else {
                return new ShortVec(value, i);
            }
        }
        throw new IllegalArgumentException("Malformed shortvec");
    }

    private static class ShortVec {
        final int bytes;
        final int value;

        ShortVec(int v, int b) {
            this.value = v;
            this.bytes = b;
        }
    }
}
