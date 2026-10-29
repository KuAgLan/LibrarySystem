package library.lock;

import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * 基于 Redis 的分布式锁。为了不引入 Jedis/Lettuce 依赖，这里手写了一个极简 RESP 客户端
 * （SET NX PX 抢锁、Lua 脚本做"判断持有者再删/续期"的原子操作）。
 *
 * <pre>
 * 抢锁：SET lock:name owner NX PX 10000        -- 不存在才设置，带过期时间
 * 续期：EVAL "if get==owner then pexpire" 1 k owner ttl
 * 释放：EVAL "if get==owner then del"     1 k owner
 * </pre>
 *
 * 用 Lua 而不是"先 GET 再 DEL"，是为了避免「判断完刚好过期、被别人抢占后又把人家的锁删掉」。
 */
public class RedisLock implements DistributedLock {

    private static final String UNLOCK_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    private static final String RENEW_LUA =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('pexpire', KEYS[1], ARGV[2]) else return 0 end";

    private final String owner = UUID.randomUUID().toString();
    private final String host;
    private final int port;
    private final String prefix;

    private Socket socket;
    private InputStream in;
    private OutputStream out;

    public RedisLock(String hostPort, String prefix) {
        String[] parts = hostPort.split(":");
        this.host = parts[0];
        this.port = parts.length > 1 ? Integer.parseInt(parts[1]) : 6379;
        this.prefix = prefix == null ? "lock:" : prefix;
    }

    // ------------------------------------------------------------ 锁操作

    @Override
    public boolean tryLock(String name, Duration ttl) {
        try {
            Object reply = command("SET", prefix + name, owner, "NX", "PX", String.valueOf(ttl.toMillis()));
            return "OK".equals(reply);
        } catch (IOException e) {
            throw new IllegalStateException("连接 Redis 失败：" + e.getMessage(), e);
        }
    }

    @Override
    public boolean renew(String name, Duration ttl) {
        try {
            Object r = command("EVAL", RENEW_LUA, "1", prefix + name, owner, String.valueOf(ttl.toMillis()));
            return r instanceof Long && (Long) r == 1L;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public boolean unlock(String name) {
        try {
            Object r = command("EVAL", UNLOCK_LUA, "1", prefix + name, owner);
            return r instanceof Long && (Long) r == 1L;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public String holder(String name) {
        try {
            Object r = command("GET", prefix + name);
            return r == null ? null : r.toString();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String owner() {
        return owner;
    }

    @Override
    public void close() {
        try { if (socket != null) socket.close(); } catch (IOException ignored) { }
        socket = null;
    }

    // ------------------------------------------------------------ 极简 RESP 客户端

    private void connect() throws IOException {
        if (socket != null && !socket.isClosed()) return;
        socket = new Socket(host, port);
        socket.setSoTimeout(3000);
        in = new BufferedInputStream(socket.getInputStream());
        out = new BufferedOutputStream(socket.getOutputStream());
    }

    /** 发送一条命令并读取回复：SimpleString→String，Integer→Long，BulkString→String，nil→null。 */
    private synchronized Object command(String... args) throws IOException {
        connect();

        StringBuilder sb = new StringBuilder("*").append(args.length).append("\r\n");
        for (String a : args) {
            byte[] bytes = a.getBytes(StandardCharsets.UTF_8);
            sb.append('$').append(bytes.length).append("\r\n").append(a).append("\r\n");
        }
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();

        return readReply();
    }

    private Object readReply() throws IOException {
        int type = in.read();
        if (type < 0) throw new EOFException("Redis 连接已关闭");
        String line = readLine();
        switch (type) {
            case '+': return line;                        // 简单字符串
            case '-': return null;                        // 错误：本场景按失败处理
            case ':': return Long.parseLong(line);        // 整数
            case '$': {                                   // 批量字符串
                int len = Integer.parseInt(line);
                if (len < 0) return null;
                byte[] buf = new byte[len];
                int read = 0;
                while (read < len) {
                    int n = in.read(buf, read, len - read);
                    if (n < 0) throw new EOFException();
                    read += n;
                }
                readLine();                               // 结尾 CRLF
                return new String(buf, StandardCharsets.UTF_8);
            }
            case '*': {                                   // 数组（EVAL 返回数组的场景这里用不到）
                int n = Integer.parseInt(line);
                for (int i = 0; i < Math.max(0, n); i++) readReply();
                return null;
            }
            default: return null;
        }
    }

    private String readLine() throws IOException {
        var sb = new StringBuilder();
        int c;
        while ((c = in.read()) != -1) {
            if (c == '\r') { in.read(); break; }
            sb.append((char) c);
        }
        return sb.toString();
    }
}
