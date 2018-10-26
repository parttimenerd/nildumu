package nildumu.eval;

import java.io.IOException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import nildumu.*;
import nildumu.eval.tools.*;

public class Evaluation {

    public static final Path DEFAULT_SPECIMEN_DIR =
            Paths.get("src/main/java/nildumu/eval/specimen");

    public final Path specimenDirectory;
    public final IntegerType integerType;

    public Evaluation(IntegerType integerType, String type){
        this(DEFAULT_SPECIMEN_DIR.resolve(type), integerType);
    }

    public Evaluation(Path specimenDirectory, IntegerType integerType) {
        this.specimenDirectory = specimenDirectory;
        this.integerType = integerType;
    }

    public TestProgram loadSpecimen(Path path) {
        try {
            return new TestProgram(path.toFile().getName(),
                    (Parser.ProgramNode) Parser.generator.parse(String.join("\n",
                    Files.readAllLines(path))), integerType);
        } catch (IOException e) {
            return null;
        }
    }

    public List<TestProgram> getAllSpecimen() throws IOException {
        return Files.list(specimenDirectory)
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
            }
            Path folder = baseFolder.resolve(t.name);
            try {
                Files.createDirectories(folder);
            } catch (IOException e) {
                e.printStackTrace();
                System.exit(0);
            }
            packets.add(t.createPacket(program, folder));
        });
        return packets;
    }

    public static PacketList getPacketsForToolsOrDie(TestProgram program, Path baseFolder){
        return getPacketsForToolsOrDie(AbstractTool.getDefaultTools(), program, baseFolder);
    }

    public PacketList getPacketList(String name, String program, Path baseFolder){
        return getPacketsForToolsOrDie(new TestProgram(name, Parser.process(program), integerType), baseFolder);
    }

    public static void main(String[] args) {
        try {
            Evaluation eval = new Evaluation(IntegerType.INT, "");
            PacketList packets = eval.getAllPackets(Paths.get("eval"));
            packets.writeTemciConfigOrDie("eval/temci.yaml", Duration.ofMinutes(5));
            AggregatedAnalysisResults results =
                    new PacketExecutor().analysePackets(packets).aggregate();
            System.out.println(results.toStringPerProgram(AggregatedAnalysisResults.LEAKAGE));
            System.out.println(results.toStringPerProgram(AggregatedAnalysisResults.RUNTIME));
            System.out.println(results.toStringPerTool(AggregatedAnalysisResults.LEAKAGE));
            System.out.println(results.toStringPerTool(AggregatedAnalysisResults.RUNTIME));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
