package com.ra.rabnbserver.crypto;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Base64 -> 自定义摩斯编码
 */
public final class MorseCodec {

    private static final Map<Character, String> HEX_TO_MORSE = Map.ofEntries(
            Map.entry('0', "-----"), Map.entry('1', ".----"),
            Map.entry('2', "..---"), Map.entry('3', "...--"),
            Map.entry('4', "....-"), Map.entry('5', "....."),
            Map.entry('6', "-...."), Map.entry('7', "--..."),
            Map.entry('8', "---.."), Map.entry('9', "----."),
            Map.entry('a', ".-"), Map.entry('b', "-..."),
            Map.entry('c', "-.-."), Map.entry('d', "-.."),
            Map.entry('e', "."), Map.entry('f', "..-.")
    );

    private MorseCodec() {
    }

    public static String encodeBase64ToCustomMorse(String base64Str) {
        if (base64Str == null || base64Str.isBlank()) {
            return "";
        }
        String hexStr = bytesToHex(base64Str.getBytes(StandardCharsets.UTF_8));
        return hexStr.chars()
                .mapToObj(c -> (char) c)
                .map(MorseCodec::hexCharToMorse)
                .collect(Collectors.joining("0"));
    }

    private static String hexCharToMorse(char hexChar) {
        String morse = HEX_TO_MORSE.get(hexChar);
        if (morse == null) {
            throw new IllegalArgumentException("无效十六进制字符: " + hexChar);
        }
        return morse.replace('.', 'o').replace('-', 'O');
    }

    private static String bytesToHex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(Character.forDigit((b >>> 4) & 0xF, 16));
            sb.append(Character.forDigit(b & 0xF, 16));
        }
        return sb.toString();
    }
}
