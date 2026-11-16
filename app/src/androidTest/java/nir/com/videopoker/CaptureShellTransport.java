package nir.com.videopoker;

/** Pure Java transport contract shared by the device driver and host regression. */
public final class CaptureShellTransport {
    private CaptureShellTransport() {}
    public static String command(String... args) {
        if (args.length == 0) throw new IllegalArgumentException("empty command");
        StringBuilder out = new StringBuilder();
        for (String arg : args) {
            // Runtime.exec(String) tokenizes whitespace, it does NOT parse shell quotes.
            if (arg == null || !arg.matches("[A-Za-z0-9_./%:+-]+"))
                throw new IllegalArgumentException("not a plain executable argument: " + arg);
            if (out.length() > 0) out.append(' ');
            out.append(arg);
        }
        return out.toString();
    }
    public static final class Result {
        public final byte[] stdout, stderr;
        Result(byte[] out, byte[] err) { stdout = out; stderr = err; }
    }
    private static byte[] drain(java.io.InputStream in, int limit) throws Exception {
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        for (int n; (n = in.read(buf)) != -1;) {
            if (out.size() + n > limit) throw new java.io.IOException("output limit exceeded");
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
    public static Result collect(java.io.InputStream stdout, java.io.InputStream stderr)
            throws Exception {
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(
                2, r -> { Thread t = new Thread(r, "capture-shell-drain"); t.setDaemon(true); return t; });
        long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
        try {
            java.util.concurrent.Future<byte[]> out = pool.submit(() -> drain(stdout, 24 * 1024 * 1024));
            java.util.concurrent.Future<byte[]> err = pool.submit(() -> drain(stderr, 64 * 1024));
            byte[] ob = out.get(Math.max(1, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
            byte[] eb = err.get(Math.max(1, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
            return new Result(ob, eb);
        } finally {
            try { stdout.close(); } finally { try { stderr.close(); } finally { pool.shutdownNow(); } }
        }
    }
}
