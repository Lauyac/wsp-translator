package com.wsp.translate;

/**
 * 非聊天文本判定工具：语音/图片等媒体条目识别。
 * 供 FloatService（读屏清洗）与 NLService（通知清洗）共享，保证两条链路过滤口径一致。
 */
public final class TextFilter {

    /** 语音、图片、贴纸等媒体条目的描述/文本前缀——这些内容没有可翻译的文字。 */
    private static final String[] MEDIA_PREFIX = {
        "语音消息", "语音", "图片", "照片", "视频", "贴纸", "表情符号", "动图", "GIF", "gif",
        "文件", "文档", "位置", "位置信息", "音频", "联系人名片", "已删除的消息",
        "Voice message", "Photo", "Video", "Sticker", "Emoji", "GIF", "Document",
        "Location", "Audio", "Contact card", "This message was deleted"
    };

    private TextFilter() { }

    /** 条目描述（无障碍 contentDescription 或文本）以媒体词开头，判定为媒体条目。 */
    public static boolean isMediaEntry(String s) {
        for (String p : MEDIA_PREFIX) {
            if (s.startsWith(p)) return true;
        }
        return false;
    }

    /** 文本形式的媒体条目：去掉时长/标点后仅剩媒体词（如“语音消息 0:07”）。 */
    public static boolean isMediaOnly(String s) {
        String t = s.replaceAll("[\\s:：0-9.,，。·]+", "");
        for (String p : MEDIA_PREFIX) {
            if (t.equals(p)) return true;
        }
        return false;
    }
}
