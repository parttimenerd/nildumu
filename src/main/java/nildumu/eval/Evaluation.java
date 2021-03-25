package nildumu.eval;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

import nildumu.*;
import nildumu.eval.tools.*;
import picocli.CommandLine;
import static picocli.CommandLine.*;
import swp.util.*;

public class Evaluation {

    public static final Path DEFAULT_SPECIMEN_DIR =
            Paths.get("eval-specimen");

    public final Path specimenDirectory;
    public final IntegerType integerType;

    public Evaluation(){
        this(IntegerType.INT, "");
    }

    public Evaluation(IntegerType integerType, String type){
        this(DEFAULT_SPECIMEN_DIR.resolve(type), integerType);
    }

    public Evaluation(Path specimenDirectory, IntegerType integerType) {
        this.specimenDirectory = specimenDirectory;
        this.integerType = integerType;
    }

    public TestProgram loadSpecimen(Path path) {
        assert path.toString().endsWith(".nd");
        try {
            TestProgram program = new TestProgram(path, path.toFile().getName(),
                    (Parser.ProgramNode) Parser.generator.parse(String.join("\n",
                    Files.readAllLines(path))), integerType);
            String baseName = path.getFileName().toString().split("\\.")[0];
            Files.list(path.getParent())
                    .filter(f -> !f.toString().endsWith(".nd") && f.getFileName().toString().startsWith(baseName + "."))
                    .forEach(p -> {
                        String[] split = p.getFileName().toString().split("\\.");
                        program.addSpecialVersion(split[1], split[0]);
                    });
            return program;
        } catch (IOException e) {
            return null;
        } catch (ParserError e){
            System.err.println(String.format("Error for %s", path));
            e.printStackTrace();
            throw e;
        }
    }

    public List<TestProgram> getAllSpecimen() throws IOException {
        return Files.list(specimenDirectory)
                .filter(f -> f.toString().endsWith(".nd"))
                .map(this::loadSpecimen)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    public PacketList getAllPackets(Path baseFolder, boolean buildOwnVersions) throws IOException {
        return getPacketsForToolsOrDie(AbstractTool.getDefaultTools(), getAllSpecimen(), baseFolder, buildOwnVersions);
    }

    public static PacketList getPacketsForToolsOrDie(List<AbstractTool> tools,
                                                     List<TestProgram> programs,
                                                     Path baseFolder,
                                                     boolean buildOwnVersions){
        return programs.stream()
                .flatMap(p -> getPacketsForToolsOrDie(tools, p, baseFolder.resolve(p.name), buildOwnVersions).stream())
                .collect(PacketList.collector());
    }

    public static PacketList getPacketsForToolsOrDie(List<AbstractTool> tools, TestProgram program,
                                                     Path baseFolder, boolean buildOwnVersions){
        PacketList packets = new PacketList();
        tools.forEach(t -> {
            Path folder = baseFolder.resolve(t.name);
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
            }
            if (!buildOwnVersions) {
                if (!program.hasSpecialVersion(t.fileEnding) && !t.fileEnding.equals("nd")) {
                    packets.add(AnalysisPacket.empty(t, program));
                } else {
                    packets.add(t.createDirectPacket(program));
                }
            } else {
                try {
                    packets.add(t.createPacket(program, folder));
                } catch (UnsupportedLanguageFeatureException ex) {
                    packets.add(AnalysisPacket.empty(t, program));
                }
            }
        });
        return packets;
    }

    public static PacketList getPacketsForToolsOrDie(TestProgram program, Path baseFolder, boolean buildOwnVersions){
        return getPacketsForToolsOrDie(AbstractTool.getDefaultTools(), program, baseFolder, buildOwnVersions);
    }

    public PacketList getPacketList(Path path, String name, String program, Path baseFolder, boolean buildOwnVersions){
        return getPacketsForToolsOrDie(new TestProgram(path, name, Parser.process(program), integerType), baseFolder, buildOwnVersions);
    }

    enum Mode {
        SCAL,
        NORMAL
    }

    @Command(description="Evaluation tool")
    static class Cmd {

        @Parameters(description="scal or normal or list", defaultValue = "normal")
        private String mode = "normal";

        @Option(description="scalability benchmark", names = "--scal")
        private List<String> scalBench = Collections.singletonList(ScalBench.ALL.name().toLowerCase());

        @Option(names={"--maxScalAlpha", "--alpha"})
        private int maxScalAlpha = 5;

        @Option(names={"--minScalAlpha", "--start"})
        private int minScalAlpha = 0;

        @Option(names="--duration")
        private String maxDuration = "PT2H";

        @Option(names="--normal")
        private List<String> normalBench = Collections.singletonList("all");

        @Option(names="--dont_split_temci")
        private boolean dontSplitTemciFiles = false;

        @Option(names="--tools", description = "all (paper), full (+ other MAXSAT and GraphTT)")
        private List<String> tools = Collections.singletonList("all");

        @Option(names="--parallelism", description = "cores to use")
        private int parallelism = 1;

        @Option(names = {"--unwind", "-u"}, description = "unwinds used")
        private List<Integer> unwinds = Arrays.asList(2, 8, 32);

        @Option(names="--runs")
        private int runs = 5;

        @Option(names="--verbose", description = "log all tool output")
        private boolean verbose = false;
    }

    public static void main(String[] args) {
        Cmd cmd = new Cmd();
        CommandLine commandLine = new CommandLine(cmd);
        commandLine.parseArgs(args);
        Evaluation evaluation = new Evaluation(IntegerType.INT, "eval");
        try {

            List<AbstractTool> tools_ = AbstractTool.getDefaultTools(cmd.unwinds.isEmpty() ?
                    new int[]{AbstractTool.DEFAULT_UNWIND} : cmd.unwinds.stream().mapToInt(i -> i).toArray());
            if (!cmd.tools.get(0).equals("all")){
                if (cmd.tools.get(0).equals("full")) {
                    tools_ = AbstractTool.getAllTools(cmd.unwinds.isEmpty() ?
                            new int[]{AbstractTool.DEFAULT_UNWIND} : cmd.unwinds.stream().mapToInt(i -> i).toArray());
                } else {
                    tools_ = tools_.stream().filter(t -> cmd.tools.contains(t.name)).collect(Collectors.toList());
                }
            }
            List<AbstractTool> tools = tools_;
            System.out.println(cmd.maxDuration);
            Duration duration = Duration.parse(cmd.maxDuration);
            if (cmd.mode.equals("list")){
                System.out.println("== Normal ==");
                evaluation.getAllSpecimen().stream().forEach(s -> System.out.println(s.name.substring(0, s.name.length() - 3)));
                System.out.println("== Bench ==");
                for (ScalBench bench : ScalBench.values()) {
                    System.out.println(bench.name().toLowerCase());
                }
            } else if (!cmd.mode.equals("normal")){
                cmd.scalBench.forEach(s -> ScalBench.valueOf(s.toUpperCase()).benchmark(cmd.minScalAlpha, cmd.maxScalAlpha, duration, cmd.parallelism, cmd.runs, unwind -> {
                    List<AbstractTool> ts = AbstractTool.getDefaultTools(unwind);
                    if (!cmd.tools.get(0).equals("all")){
                        ts = ts.stream().filter(t -> cmd.tools.contains(t.name)).collect(Collectors.toList());
                    }
                    return ts;
                }));
            } else {
                List<TestProgram> specimen = new ArrayList<>();
                cmd.normalBench.forEach(s -> {
                    try {
                        if (s.equals("all")){
                            specimen.addAll(evaluation.getAllSpecimen());
                        } else {
                            specimen.add(evaluation.loadSpecimen(evaluation.specimenDirectory.resolve(s + ".nd")));
                        }
                    } catch (IOException ex){
                        ex.printStackTrace();
                    }
                });
                PacketList packets = getPacketsForToolsOrDie(tools, specimen, Paths.get("eval"), false);
                if (!cmd.dontSplitTemciFiles){
                    packets.writeTemciConfigOrDiePerProgram(Paths.get("eval"), "run.yaml", duration);
                } else {
                    packets.writeTemciConfigOrDie("run.yaml", duration);
                }
                AggregatedAnalysisResults results =
                        new PacketExecutor(duration).analysePackets(packets, cmd.parallelism, cmd.runs, cmd.verbose).aggregate();
                storeAndPrintAnalysisResults(results, "eval/results.csv");
            }
          } catch (ParameterException | IOException e) {
            System.out.println(e.getMessage());
            commandLine.usage(System.out);
        }
    }

    public static void evalPackets(PacketList packets, String temciFile, String csvFile, Duration duration, int parallelism, int runs, boolean verbose){
        packets.writeTemciConfigOrDie(temciFile, duration);
        AggregatedAnalysisResults results =
                new PacketExecutor(duration).analysePackets(packets, parallelism, runs, verbose).aggregate();
        storeAndPrintAnalysisResults(results, csvFile);
    }

    public static void storeAndPrintAnalysisResults(AggregatedAnalysisResults results, String csvFile){
        String csv = results.toStringPerProgram(AggregatedAnalysisResults.LEAKAGE, AggregatedAnalysisResults.Mode.NICE) + "\n\n" +
                results.toStringPerProgram(AggregatedAnalysisResults.RUNTIME, AggregatedAnalysisResults.Mode.NICE);
        System.out.println(csv);
        try {
            Files.write(Paths.get(csvFile), Arrays.asList(csv.split("\n")));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public enum ScalBench {
        ALL(i -> null){
            @Override
            void benchmark(int start, int endIncl, Duration duration, int parallelism, int runs, Function<Integer, List<AbstractTool>> toolsForUnwind) {
                for (ScalBench bench : ScalBench.values()) {
                    if (bench != ALL){
                        bench.benchmark(start, endIncl, duration, parallelism, runs, toolsForUnwind);
                    }
                }
            }
        },
        IF_STATEMENTS(Generator::createProgramOfIfStmtsWithEqsAndBasicAssign),
        IF_STATEMENTS2(Generator::createProgramOfIfStmtsWithEqsAndBasicAssign2),
        IF_WHILE_STATEMENTS(Generator::createProgramOfIfStmtsWithEqsSurroundedByCountingLoop),
        REPEATED_FIBONACCIS(Generator::repeatedFibonaccis),
        REPEATED_MANY_FIBONACCIS(Generator::repeatedManyFibonaccis),
        WHILE_UNWINDING(alpha -> (Parser.ProgramNode)Parser.generator.parse(String.format("h input int h = 0buuuuuuuuuuuuuuuuuuuuuuuuuuuuuuuu; int z = 0; while (0 < h && h < %s){z = z + 1; h = h + 1} l output int o = z;", alpha))) {
            @Override
            void benchmark(int start, int endIncl, Duration duration, int parallelism, int runs, Function<Integer, List<AbstractTool>> toolsForUnwind) {
                evalPackets(IntStream.rangeClosed(start, endIncl)
                        .mapToObj(alpha -> getPacketsForToolsOrDie(toolsForUnwind.apply(1 << alpha), new TestProgram(null, "while_unwinding_" + alpha, programGenerator.apply(1 << alpha), IntegerType.INT), Paths.get("bench").resolve("while_unwinding_" + alpha), true))
                        .flatMap(PacketList::stream)
                        .collect(PacketList.collector()),"bench/temci_run.yaml",
                        "bench/results.csv", duration, parallelism, runs, true);       }
        };
        final Function<Integer, Parser.ProgramNode> programGenerator;

        ScalBench(Function<Integer, Parser.ProgramNode> programGenerator) {
            this.programGenerator = programGenerator;
        }

        void benchmark(int start, int endIncl, Duration duration, int parallelism, int runs, Function<Integer, List<AbstractTool>> toolsForUnwind){
            evalBenchmark(this.name().toLowerCase(), start, endIncl, "bench/" + name().toLowerCase(), programGenerator, duration, parallelism, runs, toolsForUnwind.apply(AbstractTool.DEFAULT_UNWIND));
        }
    }

    public static void evalBenchmark(String name, int start, int endIncl, String folder, Function<Integer, Parser.ProgramNode> programGenerator, Duration duration, int parallelism, int runs, List<AbstractTool> tools){
        evalPackets(getPacketsForToolsOrDie(tools, IntStream.rangeClosed(start, endIncl)
                .mapToObj(alpha -> new TestProgram(null, name + "_" + alpha, programGenerator.apply(1 << alpha), IntegerType.INT))
                .collect(Collectors.toList()), Paths.get(folder), true), folder + "/temci_run.yaml",
                folder + "/results.csv", duration, parallelism, runs, true);
    }

}
