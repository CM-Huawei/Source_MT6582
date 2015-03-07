package com.mediatek.dialer.util;

import java.util.ArrayList;

public class CallLogSearchUtils {
    public static int indexOfWordForLetterOrDigit(CharSequence text, char[] prefix) {
        if (prefix == null || text == null) {
            return -1;
        }

        int textLength = text.length();
        int prefixLength = prefix.length;

        if (prefixLength == 0 || textLength < prefixLength) {
            return -1;
        }

        int i = 0;
        while (i < textLength) {
            while (i < textLength && !Character.isLetterOrDigit(text.charAt(i))) {
                i++;
            }

            if (i + prefixLength > textLength) {
                return -1;
            }

            int j;
            for (j = 0; j < prefixLength; j++) {
                if (Character.toUpperCase(text.charAt(i + j)) != prefix[j]) {
                    break;
                }
            }
            if (j == prefixLength) {
                return i;
            }

            i ++;
        }

        return -1;
    }

    public static int indexOfWordForInternationalCall(CharSequence text, char[] prefix) {
        if (prefix == null || text == null) {
            return -1;
        }

        int textLength = text.length();
        int prefixLength = prefix.length;

        if (prefixLength == 0 || textLength < prefixLength) {
            return -1;
        }

        int i = 0;
        while (i < textLength) {
            // Skip non-word characters
            while (i < textLength && !Character.isLetterOrDigit(text.charAt(i)) 
                    && text.charAt(i) != '@' && text.charAt(i) != '.') {
                i++;
            }

            if (i + prefixLength > textLength) {
                return -1;
            }

            // Compare the prefixes
            int j;
            for (j = 0; j < prefixLength; j++) {
                if (Character.toUpperCase(text.charAt(i + j)) != prefix[j]) {
                    break;
                }
            }
            if (j == prefixLength) {
                return i;
            }

            i ++;
        }

        return -1;
    }

    public static int indexOfWordForLetterOrDigit(CharSequence text, char[] prefix, ArrayList<Integer> ignore) {
        if (prefix == null || text == null) {
            return -1;
        }

        int textLength = text.length();
        int prefixLength = prefix.length;

        if (prefixLength == 0 || textLength < prefixLength) {
            return -1;
        }

        int i = 0;
        while (i < textLength) {
            while (i < textLength && !Character.isLetterOrDigit(text.charAt(i))) {
                i++;
            }

            if (i + prefixLength > textLength) {
                return -1;
            }

            int j;
            ignore.clear();
            for (j = 0; j < prefixLength; j++) {
                while (i + ignore.size() + j < textLength &&
                        !Character.isLetterOrDigit(text.charAt(i + ignore.size() + j))) {
                    ignore.add(Integer.valueOf(i + ignore.size() + j));
                }
                if (i + ignore.size() + j >= textLength) {
                    return -1;
                }
                if (Character.toUpperCase(text.charAt(i + ignore.size() + j)) != prefix[j]) {
                    break;
                }
            }
            if (j == prefixLength) {
                return i;
            }

            i ++;
        }

        return -1;
    }
}
