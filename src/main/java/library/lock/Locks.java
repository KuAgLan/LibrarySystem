package library.lock;

/** 根据配置创建锁实现：--lock=mysql（默认） 或 --lock=redis --redis=127.0.0.1:6379。 */
public final class Locks {

    public enum Kind { MYSQL, REDIS }

    private static Kind kind = Kind.MYSQL;
    private static String redisAddress = "127.0.0.1:6379";

    private Locks() { }

    public static void configure(String lockArg, String redisArg) {
        if (lockArg != null && lockArg.equalsIgnoreCase("redis")) kind = Kind.REDIS;
        else if (lockArg != null && lockArg.equalsIgnoreCase("mysql")) kind = Kind.MYSQL;
        if (redisArg != null && !redisArg.isBlank()) redisAddress = redisArg;
    }

    public static Kind kind() { return kind; }

    public static String describe() {
        return kind == Kind.REDIS ? "Redis(" + redisAddress + ")" : "MySQL(lock_guard 表)";
    }

    public static DistributedLock create() {
        return kind == Kind.REDIS ? new RedisLock(redisAddress, "lock:") : new MysqlLock();
    }
}
