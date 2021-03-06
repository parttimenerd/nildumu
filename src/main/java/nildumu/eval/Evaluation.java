package nildumu.eval;

import com.beust.jcommander.*;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

import nildumu.*;
import nildumu.eval.tools.*;
import swp.util.*;

public class Evaluation {

    public static final Path DEFAULT_SPECIMEN_DIR =
            Paths.get("src/main/java/nildumu/eval/specimen");

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
        try {
            TestProgram program = new TestProgram(path.toFile().getName(),
                    (Parser.ProgramNode) Parser.generator.parse(String.join("\n",
                    Files.readAllLines(path))), integerType);
            Files.list(path.getParent())
                    .filter(f -> !f.toString().endsWith(".nd"))
                    .map(f -> {
                        try {
                            return new Pair<>(f.toFile().getName().split("\\.nd\\.")[1],
                                    String.join("\n", Files.readAllLines(f)));
                        } catch (IOException e) {
                            e.printStackTrace();
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .forEach(p -> program.addSpecialVersion(p.first, p.second));
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

    public PacketList getAllPackets(Path baseFolder) throws IOException {
        return getPacketsForToolsOrDie(AbstractTool.getDefaultTools(), getAllSpecimen(), baseFolder);
    }

    public static PacketList getPacketsForToolsOrDie(List<AbstractTool> tools,
                                                     List<TestProgram> programs,
                                                     Path baseFolder){
        return programs.stream()
                .flatMap(p -> getPacketsForToolsOrDie(tools, p, baseFolder.resolve(p.name)).stream())
                .collect(PacketList.collector());
    }

    public static PacketList getPacketsForToolsOrDie(List<AbstractTool> tools, TestProgram program,
                                                     Path baseFolder){
        PacketList packets = new PacketList();
        tools.forEach(t -> {
            if (program.hasMethods() && !t.isInterprocedural()){
                packets.add(AnalysisPacket.empty(t, program));
                return;
            }
            Path folder = baseFolder.resolve(t.name);
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
            }
            try {
                packets.add(t.createPacket(program, folder));
            } catch (UnsupportedLanguageFeatureException ex){
                packets.add(AnalysisPacket.empty(t, program));
            }
        });
        return packets;
    }

    public static PacketList getPacketsForToolsOrDie(TestProgram program, Path baseFolder){
        return getPacketsForToolsOrDie(AbstractTool.getDefaultTools(), program, baseFolder);
    }

    public PacketList getPacketList(String name, String program, Path baseFolder){
        return getPacketsForToolsOrDie(new TestProgram(name, Parser.process(program), integerType), baseFolder);
    }

    enum Mode {
        SCAL,
        NORMAL
    }

    @Parameters(commandDescription="Evaluation tool")
    static class Cmd {

        @Parameter(description="scal or normal or list", required = true)
        private String mode;

        @Parameter(description="scalability benchmark", names = "--scal")
        private List<String> scalBench = Collections.singletonList(ScalBench.ALL.name().toLowerCase());

        @Parameter(names={"--maxScalAlpha", "--alpha"})
        private int maxScalAlpha = 5;

        @Parameter(names={"--minScalAlpha", "--start"})
        private int minScalAlpha = 0;

        @Parameter(names="--duration")
        private String maxDuration = "PT10S";

        @Parameter(names="--normal")
        private List<String> normalBench = Collections.singletonList("all");
        
        @Parameter(names="--dont_split_temci")
        private boolean dontSplitTemciFiles = false;
        
        @Parameter(names="--tools")
        private List<String> tools = Collections.singletonList("all");

        @Parameter(names="--quail")
        private boolean quail = false;

        @Parameter(names="--parallelism", description = "cores to use")
        private int parallelism = 1;

        @Parameter(names="--exclude_tool_variations")
        private boolean excludeToolVariations = false;

        @Parameter(names="--runs")
        private int runs = 1;
    }

    public static void main(String[] args) {
        Cmd cmd = new Cmd();
        JCommander com = JCommander.newBuilder().addObject(cmd).build();
        Evaluation evaluation = new Evaluation(IntegerType.INT, "eval");
        try {
            System.out.println(String.join(" ", args));
            com.parse(args);
            List<AbstractTool> tools_ = cmd.excludeToolVariations ?
                    AbstractTool.getDefaultToolsWithoutVariations() :
                    AbstractTool.getDefaultTools();
            if (!cmd.tools.get(0).equals("all")){
                tools_ = tools_.stream().filter(t -> cmd.tools.contains(t.name)).collect(Collectors.toList());
            }
            tools_ = tools_.stream().filter(t -> {
                if (t instanceof Quail){
                    return cmd.quail;
                }
                return true;
            }).collect(Collectors.toList());
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
                cmd.scalBench.forEach(s -> ScalBench.valueOf(s.toUpperCase()).benchmark(cmd.minScalAlpha, cmd.maxScalAlpha, duration, cmd.parallelism, cmd.runs, tools));
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
                PacketList packets = getPacketsForToolsOrDie(tools, specimen, Paths.get("eval"));
                if (!cmd.dontSplitTemciFiles){
                    packets.writeTemciConfigOrDiePerProgram(Paths.get("eval"), "run.yaml", duration);
                } else {
                    packets.writeTemciConfigOrDie("run.yaml", duration);
                }
                AggregatedAnalysisResults results =
                        new PacketExecutor(duration).analysePackets(packets, cmd.parallelism, cmd.runs).aggregate();
                storeAndPrintAnalysisResults(results, "eval/results.csv");
            }
          } catch (ParameterException | IOException e) {
            System.out.println(e.getMessage());
            com.usage();
        }
    }

    public static void evalPackets(PacketList packets, String temciFile, String csvFile, Duration duration, int parallelism, int runs){
        Evaluation eval = new Evaluation(IntegerType.INT, "");
        packets.writeTemciConfigOrDie(temciFile, duration);
        AggregatedAnalysisResults results =
                new PacketExecutor(duration).analysePackets(packets, parallelism, runs).aggregate();
        storeAndPrintAnalysisResults(results, csvFile);
    }

    public static void storeAndPrintAnalysisResults(AggregatedAnalysisResults results, String csvFile){
        String csv = results.toStringPerProgram(AggregatedAnalysisResults.LEAKAGE) + "\n" +
                results.toStringPerProgram(AggregatedAnalysisResults.RUNTIME) + "\n" +
                results.toStringPerTool(AggregatedAnalysisResults.LEAKAGE) + "\n" +
                results.toStringPerTool(AggregatedAnalysisResults.RUNTIME);
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
            void benchmark(int start, int endIncl, Duration duration, int parallelism, int runs, List<AbstractTool> tools) {
                for (ScalBench bench : ScalBench.values()) {
                    if (bench != ALL){
                        bench.benchmark(start, endIncl, duration, parallelism, runs, tools);
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
            void benchmark(int start, int endIncl, Duration duration, int parallelism, int runs, List<AbstractTool> tools) {
                evalPackets(IntStream.rangeClosed(start, endIncl)
                        .mapToObj(alpha -> getPacketsForToolsOrDie(tools.stream().map(t -> t.setUnwindingLimit(1 << alpha)).collect(Collectors.toList()), new TestProgram("while_unwinding_" + alpha, programGenerator.apply(1 << alpha), IntegerType.INT), Paths.get("bench").resolve("while_unwinding_" + alpha)))
                        .flatMap(PacketList::stream)
                        .collect(PacketList.collector()),"bench/temci_run.yaml",
                        "bench/results.csv", duration, parallelism, runs);       }
        };
        final Function<Integer, Parser.ProgramNode> programGenerator;

        ScalBench(Function<Integer, Parser.ProgramNode> programGenerator) {
            this.programGenerator = programGenerator;
        }

        void benchmark(int start, int endIncl, Duration duration, int parallelism, int runs, List<AbstractTool> tools){
            evalBenchmark(this.name().toLowerCase(), start, endIncl, "bench/" + name().toLowerCase(), programGenerator, duration, parallelism, runs, tools);
        }
    }

    public static void evalBenchmark(String name, int start, int endIncl, String folder, Function<Integer, Parser.ProgramNode> programGenerator, Duration duration, int parallelism, int runs, List<AbstractTool> tools){
        evalPackets(getPacketsForToolsOrDie(tools, IntStream.rangeClosed(start, endIncl)
                .mapToObj(alpha -> new TestProgram(name + "_" + alpha, programGenerator.apply(1 << alpha), IntegerType.INT))
                .collect(Collectors.toList()), Paths.get(folder)), folder + "/temci_run.yaml",
                folder + "/results.csv", duration, parallelism, runs);
    }

}
