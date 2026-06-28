package net.verity.config;

/**
 * Обфускация встроенных ключей.
 * Ключи хранятся XOR-закодированными, чтобы при декомпиляции
 * не были видны в открытом виде.
 */
public final class KeyVault {

    private static final byte[] MASK = {(byte)0x5A, 0x3C, 0x7F, 0x21, 0x6B, 0x0D, 0x4E, (byte)0x93};

    private KeyVault() {}

    /**
     * Кодирует строку в XOR-массив байтов.
     * (используется только при разработке для генерации закодированных ключей)
     */
    public static byte[] encode(String plain) {
        byte[] data = plain.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] result = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = (byte) (data[i] ^ MASK[i % MASK.length]);
        }
        return result;
    }

    /**
     * Декодирует XOR-массив байтов обратно в строку.
     */
    public static String decode(byte[] encoded) {
        byte[] result = new byte[encoded.length];
        for (int i = 0; i < encoded.length; i++) {
            result[i] = (byte) (encoded[i] ^ MASK[i % MASK.length]);
        }
        return new String(result, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Декодирует массив закодированных строк.
     */
    public static java.util.List<String> decodeAll(byte[][] encoded) {
        java.util.List<String> result = new java.util.ArrayList<>(encoded.length);
        for (byte[] e : encoded) {
            result.add(decode(e));
        }
        return result;
    }
}
