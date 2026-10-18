package dev.jasper.app.platform;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** A real pipe-owning child, independent of a native desktop. */
public final class DesktopChildMain {
    public static void main(String[] args) throws Exception {
        String mode = args[0];
        if (mode.equals("exit")) return;
        if (mode.equals("force")) Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { new java.util.concurrent.CountDownLatch(1).await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        }));
        if (mode.equals("malformed")) {
            System.out.write('{'); System.out.write(0xc3); System.out.flush();
            System.out.write(0x28); System.out.write('\n'); System.out.flush();
        } else if (mode.equals("oversized")) {
            System.out.write(new byte[65 * 1024]); System.out.flush();
        } else {
            if (mode.equals("stderr")) {
                System.err.write("x".repeat(100_000).getBytes(StandardCharsets.UTF_8)); System.err.flush();
            }
            System.out.println("{\"v\":1,\"kind\":\"portal\",\"owner\":" + (mode.equals("portal9") ? 9 : 0) + ",\"appearance\":\"dark\",\"reason\":\"\"}");
            System.out.flush();
        }
        var input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        String line; int samples = 0;
        while ((line = input.readLine()) != null) {
            if (mode.equals("stuck")) continue;
            String generation = line.replaceAll(".*\"generation\":([0-9]+).*", "$1");
            if (line.contains("\"ping\"")) {
                if (mode.equals("wrong-pong")) generation = Long.toString(Long.parseLong(generation) + 1);
                System.out.println("{\"v\":1,\"kind\":\"pong\",\"generation\":" + generation + "}");
                System.out.flush(); System.err.print("pong"); System.err.flush();
            }
            else {
                samples++;
                if (mode.equals("capability")) {
                    System.out.println("{\"v\":1,\"kind\":\"palette\",\"generation\":" + generation
                        + ",\"source\":\"gtk3\",\"appearance\":\"unknown\",\"implementation\":\"gtk3\",\"libraryVersion\":\"\",\"reason\":\"toolkit unavailable\"}");
                    System.out.flush(); continue;
                }
                String appearance = line.contains("\"dark\"") ? "dark" : "light";
                System.out.println("{\"v\":1,\"kind\":\"palette\",\"generation\":" + generation
                    + ",\"source\":\"gtk3\",\"appearance\":\"" + appearance
                    + "\",\"implementation\":\"fixture\",\"libraryVersion\":\"" + (mode.equals("count") ? samples : 1) + "\",\"reason\":\"\",\"window.bg\":\"0,0,0,1\",\"window.fg\":\"1,1,1,1\"}");
            }
            System.out.flush();
        }
        if (mode.equals("force")) new java.util.concurrent.CountDownLatch(1).await();
    }
}
