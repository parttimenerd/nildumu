package nildumu.eval;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringEscapeUtils;

import java.io.IOException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * Allows to execute {@link AnalysisPacket}s and return the
 * calculated leakage
 */
public class PacketExecutor {

    public final Duration timeLimit;

    public PacketExecutor(){
        this(Duration.ofHours(10));
    }

    public PacketExecutor(Duration timeLimit) {
        this.timeLimit = timeLimit;
    }

    public AnalysisResult analyse(AnalysisPacket packet) {
        System.out.println("Start analysis of " + packet);
        System.out.println(String.format("   Using shell command %s", packet.getShellCommand()));
        long start = System.nanoTime();
        Process process_ = null;
        try {
            System.err.println("bash -c '" + StringEscapeUtils.escapeJava(packet.getShellCommand()) + "'");
            process_ = Runtime.getRuntime().exec(new String[]{"bash", "-c", packet.getShellCommand()});
        } catch (IOException e) {
            e.printStackTrace();
            return new AnalysisResult(false, -1, null);
        }
        Process process = process_;
        // see https://stackoverflow.com/a/41492679
        ExecutorService newFixedThreadPool = Executors.newFixedThreadPool(2);
        Future<String> output = newFixedThreadPool.submit(() -> {
            return IOUtils.toString(process.getInputStream());
        });
        Future<String> error = newFixedThreadPool.submit(() -> {
            return IOUtils.toString(process.getErrorStream());
        });

        newFixedThreadPool.shutdown();
        try {
            if (!process.waitFor(timeLimit.getSeconds(), TimeUnit.SECONDS)) {
                process.destroy();
                if (output.get().length() > 0) {
                    System.out.println("OUT: " + output.get());
                }
                if (error.get().length() > 0) {
                    System.err.println("ERR: " + error.get());
                }
                return new AnalysisResult(false, -1, null);
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        try {
            if (output.get().length() > 0) {
                System.out.println("OUT: " + output.get());
            }
            if (error.get().length() > 0) {
                System.err.println("ERR: " + error.get());
            }
            return new AnalysisResult(
                    true, packet.getLeakageParser().parse(output.get(), error.get()),
                    Duration.ofNanos(System.nanoTime() - start)
                    );
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        return new AnalysisResult(false, -1, Duration.ofNanos(-1));
    }

    public AnalysisResults analysePackets(Iterable<AnalysisPacket> packets) {
        Map<AnalysisPacket, AnalysisResult> results = new HashMap<>();
        for (AnalysisPacket packet : packets) {
            results.put(packet, analyse(packet));
        }
        return new AnalysisResults(results);
    }
}
