package com.wsp.translate;

/** 语言表（与扩展 langmap.js 支持语种一致）与简单文本判定工具。 */
public class Langs {
    public static final String[][] ITEMS = {
        {"zh-CN", "简体中文"}, {"zh-TW", "繁体中文"},
        {"en", "英语"}, {"es", "西班牙语"}, {"pt", "葡萄牙语"},
        {"fr", "法语"}, {"de", "德语"}, {"ru", "俄语"},
        {"ar", "阿拉伯语"}, {"ja", "日语"}, {"ko", "韩语"},
        {"vi", "越南语"}, {"id", "印尼语"}, {"th", "泰语"},
        {"it", "意大利语"}, {"tr", "土耳其语"}
    };

    public static String label(String code) {
        for (String[] it : ITEMS) if (it[0].equals(code)) return it[1];
        return code;
    }

    public static int indexOf(String code) {
        for (int i = 0; i < ITEMS.length; i++) if (ITEMS[i][0].equals(code)) return i;
        return 2;
    }

    private static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF) || (c >= 0x3400 && c <= 0x4DBF)
            || (c >= 0x3040 && c <= 0x30FF) || (c >= 0xAC00 && c <= 0xD7AF)
            || (c >= 0x3000 && c <= 0x303F) || (c >= 0xF900 && c <= 0xFAFF);
    }

    /** CJK 字符占比（checks.js 的汉字残存比例的简化版）。 */
    public static double cjkRatio(String s) {
        if (s == null || s.isEmpty()) return 0;
        int cjk = 0, total = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isWhitespace(c) || Character.isDigit(c)) continue;
            total++;
            if (isCjk(c)) cjk++;
        }
        return total == 0 ? 0 : (double) cjk / total;
    }

    /** 译文残留判定：源文以中文为主、译文仍是大段中文 → 视为未翻译成功。 */
    public static boolean residueBlock(String src, String result) {
        return cjkRatio(src) >= 0.5 && cjkRatio(result) >= 0.5;
    }
}
