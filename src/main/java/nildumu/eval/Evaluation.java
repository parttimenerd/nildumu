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
        this(IntegerType.INT);
    }

    public Evaluation(IntegerType integerType){
        this(DEFAULT_SPECIMEN_DIR, integerType);
    }

    public Evaluation(Path specimenDirectory, IntegerType integerType) {
        this.specimenDirectory = specimenDirectory;
        this.integerType = integerType;
    }

    public TestProgram loadSpecimen(Path path) {
        assert path.toString().endsWith(".nd");
        try {
            TestProgram program = new TestProgram(path, path.toFile().getName(),
                    Parser.parse(String.join("\n",
                    Files.readAllLines(path))), integerType);
            String[] split = path.getFileName().toString().split("\\.");
            String baseName = Arrays.stream(split, 0, split.length - 1).collect(Collectors.joining("."));
            Files.list(path.getParent())
                    .filter(f -> !f.toString().endsWith(".nd") && f.getFileName().toString().startsWith(baseName + "."))
                    .forEach(p -> {
                        String[] splits = p.getFileName().toString().split("\\.");
                        program.addSpecialVersion(splits[split.length - 1], baseName);
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
            if (!buildOwnVersions) {
                if (!program.hasSpecialVersion(t.fileEnding) && !t.fileEnding.equals("nd")) {
                    packets.add(AnalysisPacket.empty(t, program));
                } else {
                    packets.add(t.createDirectPacket(program));
                }
            } else {
                Path folder = baseFolder.resolve(t.name);
                try {
                    Files.createDirectories(folder);
                } catch (IOException e) {
                    e.printStackTrace();
                    System.exit(0);
                }
                try {
                    packets.add(t.createPacket(program, folder));
                } catch (UnsupportedLanguageFeatureException ex) {
                    packets.add(AnalysisPacket.empty(t, program));
                }
            }
        });
        return packets;
    }

    enum Mode {
        SCAL,
        NORMAL
    }

    @Command(description="Evaluation tool", showDefaultValues = true, mixinStandardHelpOptions = true)
    static class Cmd {

        @Option(names="--duration")
        private String maxDuration = "PT2H";

        @Option(names="--programs", description = "example programs to evaluate: all (all from paper), small (… except E-Voting)")
        private List<String> normalBench = Collections.singletonList("all");

        @Option(names="--excluded_programs")
        private List<String> normalEx = Collections.emptyList();

        //@Option(names="--dont_split_temci")
        private boolean dontSplitTemciFiles = false;

        @Option(names="--tools", description = "all (paper), nildumu (all without ApproxFlow), full (+ other MAXSAT and GraphTT), exact (unwind 5, 10, 64, eps=0.1, delta=0.05)")
        private List<String> tools = Collections.singletonList("all");

        //@Option(names="--parallelism", description = "cores to use")
        //private int parallelism = 1;//(int)Math.ceil(Runtime.getRuntime().availableProcessors() / 4f);

        @Option(names = {"--unwind", "-u"}, description = "unwinds used")
        private List<Integer> unwinds = Arrays.asList(2, 8, 32);

        @Option(names = "--summary_unwind", description = "Unwind in the nildumu summary handler")
        private boolean summaryUnwind = false;

        @Option(names="--runs")
        private int runs = 5;

        //@Option(names="--dryruns")
        private int dryruns = 0;

        @Option(names="--verbose", description = "log all tool output")
        private boolean verbose = false;
    }

    public static void main(String[] args) {
        Cmd cmd = new Cmd();
        CommandLine commandLine = new CommandLine(cmd);
        commandLine.parseArgs(args);
        Evaluation evaluation = new Evaluation(IntegerType.INT);
        try {
            List<AbstractTool> tools_ = new ArrayList<>();
            int[] unwinds = cmd.unwinds.isEmpty() ?
                    new int[]{AbstractTool.DEFAULT_UNWIND} : cmd.unwinds.stream().mapToInt(i -> i).toArray();
            List<AbstractTool> allTools = AbstractTool.getDefaultTools(cmd.summaryUnwind, true, unwinds);
            for (String tool : cmd.tools) {
                switch (tool) {
                    case "all":
                        tools_.addAll(AbstractTool.getDefaultTools(cmd.summaryUnwind, false, unwinds));
                        break;
                    case "nildumu":
                        tools_.addAll(allTools
                                .stream().filter(t -> t.name.contains("nildumu")).collect(Collectors.toList()));
                        break;
                    case "full":
                        tools_.addAll(AbstractTool.getAllTools(cmd.summaryUnwind, unwinds));
                    case "exact":
                        tools_.addAll(Arrays.asList(new ApproxFlow(5, 0.1, 0.05),
                                new ApproxFlow(10, 0.1, 0.05),
                                new ApproxFlow(64, 0.1, 0.05)));
                    default:
                        tools_.addAll(allTools.stream().filter(t -> cmd.tools.contains(t.name)).collect(Collectors.toList()));
                }
            }
            List<AbstractTool> tools = tools_;
            System.out.println(cmd.maxDuration);
            Duration duration = Duration.parse(cmd.maxDuration);
            List<TestProgram> specimen = new ArrayList<>();
            cmd.normalBench.forEach(s -> {
                try {
                    if (s.equals("all")) {
                        specimen.addAll(evaluation.getAllSpecimen().stream()
                                .filter(ss -> !cmd.normalEx.contains(ss.name)).collect(Collectors.toList()));
                    } else if (s.equals("small")) {
                        specimen.addAll(evaluation.getAllSpecimen().stream()
                                .filter(ss -> !cmd.normalEx.contains(ss.name) && !ss.name.contains("preference"))
                                .collect(Collectors.toList()));
                    } else {
                        specimen.add(evaluation.loadSpecimen(evaluation.specimenDirectory.resolve(s + ".nd")));
                    }
                } catch (IOException ex){
                    ex.printStackTrace();
                }
            });
            PacketList packets = getPacketsForToolsOrDie(tools, specimen, Paths.get("."), false);
            if (!cmd.dontSplitTemciFiles){
                //packets.writeTemciConfigOrDiePerProgram(Paths.get("eval"), "run.yaml", duration);
            } else {
                //packets.writeTemciConfigOrDie("run.yaml", duration);
            }
            AggregatedAnalysisResults results =
                    new PacketExecutor(duration).analysePackets(packets, 1, cmd.runs, cmd.dryruns, cmd.verbose).aggregate();
            storeAndPrintAnalysisResults(results, String.format("eval/results%d.csv", System.currentTimeMillis()));
          } catch (ParameterException e) {
            System.out.println(e.getMessage());
            commandLine.usage(System.out);
        }
    }

    public static void evalPackets(PacketList packets, String temciFile, String csvFile, Duration duration, int parallelism, int runs, int dryruns, boolean verbose){
        packets.writeTemciConfigOrDie(temciFile, duration);
        AggregatedAnalysisResults results =
                new PacketExecutor(duration).analysePackets(packets, parallelism, runs, dryruns, verbose).aggregate();
        storeAndPrintAnalysisResults(results, csvFile);
    }

    public static void storeAndPrintAnalysisResults(AggregatedAnalysisResults results, String csvFile){
        String csv = results.toStringPerProgram(AggregatedAnalysisResults.LEAKAGE, AggregatedAnalysisResults.Mode.CSV) + "\n\n" +
                results.toStringPerProgram(AggregatedAnalysisResults.RUNTIME, AggregatedAnalysisResults.Mode.CSV);
        System.out.println(results.toStringPerProgram(AggregatedAnalysisResults.LEAKAGE, AggregatedAnalysisResults.Mode.NICE) + "\n\n" +
                results.toStringPerProgram(AggregatedAnalysisResults.RUNTIME, AggregatedAnalysisResults.Mode.NICE));
        try {
            Files.write(Paths.get(csvFile), Arrays.asList(csv.split("\n")));
        } catch (IOException e) {
            //e.printStackTrace();
        }
    }

    public static void evalBenchmark(String name, int start, int endIncl, String folder, Function<Integer, Parser.ProgramNode> programGenerator, Duration duration, int parallelism, int runs, int dryruns, List<AbstractTool> tools){
        evalPackets(getPacketsForToolsOrDie(tools, IntStream.rangeClosed(start, endIncl)
                .mapToObj(alpha -> new TestProgram(null, name + "_" + alpha, programGenerator.apply(1 << alpha), IntegerType.INT))
                .collect(Collectors.toList()), Paths.get(folder), true), folder + "/temci_run.yaml",
                folder + "/results.csv", duration, parallelism, runs, dryruns, true);
    }

}
