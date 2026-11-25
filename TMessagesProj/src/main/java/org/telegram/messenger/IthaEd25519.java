package org.telegram.messenger;

import java.math.BigInteger;
import java.security.MessageDigest;

/** Verificacion de firmas Ed25519 (RFC 8032) en Java puro, para Android sin soporte nativo. */
public final class IthaEd25519 {

    private static final BigInteger ONE = BigInteger.ONE;
    private static final BigInteger TWO = BigInteger.valueOf(2);
    private static final BigInteger P = ONE.shiftLeft(255).subtract(BigInteger.valueOf(19));
    private static final BigInteger Q = ONE.shiftLeft(252).add(new BigInteger("27742317777372353535851937790883648493"));
    private static final BigInteger D = BigInteger.valueOf(-121665).multiply(inv(BigInteger.valueOf(121666))).mod(P);
    private static final BigInteger SQRT_M1 = TWO.modPow(P.subtract(ONE).shiftRight(2), P);
    private static final BigInteger[] G;

    static {
        BigInteger gy = BigInteger.valueOf(4).multiply(inv(BigInteger.valueOf(5))).mod(P);
        BigInteger gx = recoverX(gy, 0);
        G = new BigInteger[]{gx, gy, ONE, gx.multiply(gy).mod(P)};
    }

    private IthaEd25519() {}

    private static BigInteger inv(BigInteger x) {
        return x.modPow(P.subtract(TWO), P);
    }

    private static BigInteger[] add(BigInteger[] a, BigInteger[] b) {
        BigInteger A = a[1].subtract(a[0]).multiply(b[1].subtract(b[0])).mod(P);
        BigInteger B = a[1].add(a[0]).multiply(b[1].add(b[0])).mod(P);
        BigInteger C = a[3].multiply(TWO).multiply(D).multiply(b[3]).mod(P);
        BigInteger Dd = a[2].multiply(TWO).multiply(b[2]).mod(P);
        BigInteger E = B.subtract(A);
        BigInteger F = Dd.subtract(C);
        BigInteger Gg = Dd.add(C);
        BigInteger H = B.add(A);
        return new BigInteger[]{E.multiply(F).mod(P), Gg.multiply(H).mod(P), F.multiply(Gg).mod(P), E.multiply(H).mod(P)};
    }

    private static BigInteger[] mul(BigInteger s, BigInteger[] point) {
        BigInteger[] r = new BigInteger[]{BigInteger.ZERO, ONE, ONE, BigInteger.ZERO};
        BigInteger[] p = point;
        for (int i = 0, n = s.bitLength(); i < n; i++) {
            if (s.testBit(i)) {
                r = add(r, p);
            }
            p = add(p, p);
        }
        return r;
    }

    private static boolean equal(BigInteger[] a, BigInteger[] b) {
        if (a[0].multiply(b[2]).subtract(b[0].multiply(a[2])).mod(P).signum() != 0) {
            return false;
        }
        return a[1].multiply(b[2]).subtract(b[1].multiply(a[2])).mod(P).signum() == 0;
    }

    private static BigInteger recoverX(BigInteger y, int sign) {
        if (y.compareTo(P) >= 0) {
            return null;
        }
        BigInteger x2 = y.multiply(y).subtract(ONE).multiply(inv(D.multiply(y).multiply(y).add(ONE))).mod(P);
        if (x2.signum() == 0) {
            return sign != 0 ? null : BigInteger.ZERO;
        }
        BigInteger x = x2.modPow(P.add(BigInteger.valueOf(3)).shiftRight(3), P);
        if (x.multiply(x).subtract(x2).mod(P).signum() != 0) {
            x = x.multiply(SQRT_M1).mod(P);
        }
        if (x.multiply(x).subtract(x2).mod(P).signum() != 0) {
            return null;
        }
        if ((x.testBit(0) ? 1 : 0) != sign) {
            x = P.subtract(x);
        }
        return x;
    }

    private static BigInteger littleEndian(byte[] b, int off, int len) {
        byte[] be = new byte[len];
        for (int i = 0; i < len; i++) {
            be[len - 1 - i] = b[off + i];
        }
        return new BigInteger(1, be);
    }

    private static BigInteger[] decompress(byte[] s) {
        byte[] copy = s.clone();
        int sign = (copy[31] >> 7) & 1;
        copy[31] &= 0x7f;
        BigInteger y = littleEndian(copy, 0, 32);
        BigInteger x = recoverX(y, sign);
        if (x == null) {
            return null;
        }
        return new BigInteger[]{x, y, ONE, x.multiply(y).mod(P)};
    }

    public static boolean verify(byte[] publicKey, byte[] message, byte[] signature) {
        try {
            if (publicKey == null || signature == null || publicKey.length != 32 || signature.length != 64) {
                return false;
            }
            BigInteger[] a = decompress(publicKey);
            byte[] rBytes = new byte[32];
            System.arraycopy(signature, 0, rBytes, 0, 32);
            BigInteger[] r = decompress(rBytes);
            if (a == null || r == null) {
                return false;
            }
            BigInteger s = littleEndian(signature, 32, 32);
            if (s.compareTo(Q) >= 0) {
                return false;
            }
            MessageDigest sha = MessageDigest.getInstance("SHA-512");
            sha.update(rBytes);
            sha.update(publicKey);
            sha.update(message);
            byte[] hash = sha.digest();
            BigInteger h = littleEndian(hash, 0, 64).mod(Q);
            return equal(mul(s, G), add(r, mul(h, a)));
        } catch (Throwable e) {
            return false;
        }
    }
}
