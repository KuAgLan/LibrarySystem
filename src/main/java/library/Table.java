package library;

import java.util.List;

/** 等宽表格：中文字符按 2 列宽计算，保证控制台对齐。 */
public final class Table {

    private Table() { }

    public static int width(String s) {
        if (s == null) return 0;
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            w += isWide(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return w;
    }

    private static boolean isWide(int c) {
        return (c >= 0x1100 && c <= 0x115F)
            || (c >= 0x2E80 && c <= 0xA4CF)
            || (c >= 0xAC00 && c <= 0xD7A3)
            || (c >= 0xF900 && c <= 0xFAFF)
            || (c >= 0xFE30 && c <= 0xFE6F)
            || (c >= 0xFF00 && c <= 0xFF60)
            || (c >= 0xFFE0 && c <= 0xFFE6);
    }

    public static String pad(String s, int width) {
        if (s == null) s = "";
        int pad = Math.max(0, width - width(s));
        return s + " ".repeat(pad);
    }

    public static String fit(String s, int max) {
        if (s == null) s = "";
        if (width(s) <= max) return s;
        StringBuilder sb = new StringBuilder();
        int w = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int cw = isWide(cp) ? 2 : 1;
            if (w + cw > max - 1) break;
            sb.appendCodePoint(cp);
            w += cw;
            i += Character.charCount(cp);
        }
        return sb + "…";
    }

    /** 打印一张表：headers + rows，右对齐列用 rightAlign 指定。 */
    public static void print(String[] headers, List<String[]> rows, boolean[] rightAlign) {
        int[] widths = new int[headers.length];
        for (int i = 0; i < headers.length; i++) {
            widths[i] = width(headers[i]);
            for (String[] r : rows) widths[i] = Math.max(widths[i], width(r[i]));
        }

        StringBuilder head = new StringBuilder("  ");
        StringBuilder line = new StringBuilder("  ");
        for (int i = 0; i < headers.length; i++) {
            head.append(align(headers[i], widths[i], rightAlign != null && rightAlign[i])).append("  ");
            line.append("-".repeat(widths[i])).append("  ");
        }
        System.out.println(head);
        System.out.println(line);
        for (String[] r : rows) {
            StringBuilder sb = new StringBuilder("  ");
            for (int i = 0; i < headers.length; i++)
                sb.append(align(r[i], widths[i], rightAlign != null && rightAlign[i])).append("  ");
            System.out.println(sb);
        }
        if (rows.isEmpty()) System.out.println("  （无数据）");
    }

    private static String align(String s, int width, boolean right) {
        if (!right) return pad(s, width);
        int pad = Math.max(0, width - width(s));
        return " ".repeat(pad) + s;
    }
}
