package nildumu.eval;

import org.apache.commons.io.IOUtils;

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

    public AnalysisResult analyse(AnalysisPacket packet, int runs, boolean print, boolean verbose) {
        System.out.println("run shell command " + packet.getShellCommand(timeLimit));
        List<AnalysisResult> results = new ArrayList<>();
        analyse(packet, verbose);
        for (int i = 0; i < runs; i++){
          AnalysisResult res = analyse(packet, verbose);
          if (!res.isValid() || res.hasTimeout){
              return new AnalysisResult(res.isValid(), -1, Duration.ofNanos(-1), res.hasTimeout);
          }
          results.add(res);
        }
        float leakageAvg = (float)results.stream().mapToDouble(r -> r.leakage).average().getAsDouble();
        float leakageStd = (float)Math.sqrt(results.stream().mapToDouble(r -> Math.pow(r.leakage - leakageAvg, 2)).average().getAsDouble() / (runs - 1));
        long runtimeAvg = (long)results.stream().mapToLong(r -> r.runtime.toNanos()).average().getAsDouble();
        float runtimeStd = (float)(Math.sqrt(results.stream().mapToDouble(r -> Math.pow(r.runtime.toNanos() - runtimeAvg, 2)).average().getAsDouble() / (runs - 1)));
        AnalysisResult res = new AnalysisResult(true, leakageAvg, Duration.ofNanos(runtimeAvg), false, leakageStd / leakageAvg, runtimeStd / runtimeAvg);
        if (print) {
            System.out.println("#####################################################");
            System.out.println(String.format("-------------- %fs +-%.3f %.3f +-%.3f -------------", res.runtime.toMillis() / 1000.0, res.runtimeStddev, res.leakage, res.leakageStddev));
            System.out.println("#####################################################");
        }
        return res;
    }

    public AnalysisResult analyse(AnalysisPacket packet, boolean verbose) {
        if (packet.emptyPacket){
            return new AnalysisResult(false, -1, null, false);
        }
        System.out.print("+");
        long start = System.nanoTime();
        Process process_ = null;
        try {
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
        try {
            if (!process.waitFor(timeLimit.getSeconds() + 5, TimeUnit.SECONDS)) {
                try {
                String out = output.get(timeLimit.getSeconds(), TimeUnit.SECONDS);
                String err = error.get(timeLimit.getSeconds(), TimeUnit.SECONDS);
                process.destroyForcibly();
                process.waitFor();
                /*if (output.get().length() > 0) {
                    System.out.println("OUT: " + output.get());
                }*/

                    if (err.length() > 0) {
                        System.err.println("ERR: " + err);
                    }
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
            if (output.get().length() > 0 && verbose) {
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

    public AnalysisResults analysePackets(Iterable<AnalysisPacket> packets, int parallelism, int runs, boolean verbose) {
        if (parallelism == 1) {
            Map<AnalysisPacket, AnalysisResult> results = new HashMap<>();
            int i = 1;
            List<AnalysisPacket> ps = new ArrayList<>();
            packets.forEach(ps::add);
            for (AnalysisPacket packet : packets) {
                System.out.println("#####################################################");
                System.out.println(String.format("------------------- Packet %d of %d -------------", i, ps.size()));
                System.out.println("#####################################################");
                results.put(packet, analyse(packet, runs, true, verbose));
                i++;
            }
            return new AnalysisResults(results);
        } else {
            Map<AnalysisPacket, AnalysisResult> results = new ConcurrentHashMap<>();
            ExecutorService pool = Executors.newWorkStealingPool(parallelism);
            List<Future<AnalysisResult>> futures = new ArrayList<>();
            for (AnalysisPacket packet : packets) {
                futures.add(pool.submit(() -> results.put(packet, analyse(packet, runs, true, verbose))));
                //results.put(packet, analyse(packet));
            }
            futures.forEach(f -> {
                try {
                    f.get();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (ExecutionException e) {
                    e.printStackTrace();
                }
            });
            return new AnalysisResults(results);
        }
    }
}
