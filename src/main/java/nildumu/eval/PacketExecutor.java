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
        this(Duration.ofSeconds(20));
    }

    public PacketExecutor(Duration timeLimit) {
        this.timeLimit = timeLimit;
    }

    public AnalysisResult analyse(AnalysisPacket packet) {
        if (packet.emptyPacket){
            return new AnalysisResult(false, -1, null, false);
        }
        System.out.println("Start analysis of " + packet);
        System.out.println(String.format("   Using shell command %s",
                packet.getShellCommand(timeLimit)));
        long start = System.nanoTime();
        Process process_ = null;
        try {
            System.err.println("bash -c 'timeout -s9 " + (timeLimit.getSeconds()) + " " +
                    StringEscapeUtils.escapeJava(packet.getShellCommand(timeLimit)) + "'");
            process_ = Runtime.getRuntime().exec(new String[]{"timeout", "-s9",
                    timeLimit.getSeconds() + "",
                    "bash", "-c", packet.getShellCommand(timeLimit)});
        } catch (IOException e) {
            e.printStackTrace();
            return new AnalysisResult(false, -1, null, true);
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
        AnalysisResult timeoutRes = new AnalysisResult(false, -1, null, true);
        newFixedThreadPool.shutdown();
        System.out.println("Hi" + timeLimit.getSeconds());
        try {
            if (!process.waitFor(timeLimit.getSeconds() + 5, TimeUnit.SECONDS)) {
                try {
                String out = output.get(timeLimit.getSeconds(), TimeUnit.SECONDS);
                String err = error.get(timeLimit.getSeconds(), TimeUnit.SECONDS);
                System.out.println("Ho");
                process.destroyForcibly();
                process.waitFor();
                System.out.println("Ho4");
                /*if (output.get().length() > 0) {
                    System.out.println("OUT: " + output.get());
                }*/

                    if (err.length() > 0) {
                        System.err.println("ERR: " + err);
                    }
                    System.out.println("Ho2");
                    if (process.exitValue() == 124){
                        return timeoutRes;
                    }
                    return new AnalysisResult(true, packet.getLeakageParser()
                            .parse(out, err), timeLimit, true);
                } catch (LeakageParserException | TimeoutException ex){
                    return timeoutRes;
                }
            }
            if (process.exitValue() == 124 || process.exitValue() == 137){
                return timeoutRes;
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
            if (process.exitValue() == 124 || process.exitValue() == 137){
                return timeoutRes;
            }
            return new AnalysisResult(
                    true, packet.getLeakageParser().parse(output.get(), error.get()),
                    Duration.ofNanos(System.nanoTime() - start),
                    false);
        } catch (InterruptedException | ExecutionException | LeakageParserException e) {
            e.printStackTrace();
        }
        return new AnalysisResult(false, -1, Duration.ofNanos(-1), false);
    }

    public AnalysisResults analysePackets(Iterable<AnalysisPacket> packets) {
        Map<AnalysisPacket, AnalysisResult> results = new HashMap<>();
        for (AnalysisPacket packet : packets) {
            results.put(packet, analyse(packet));
        }
        return new AnalysisResults(results);
    }
}