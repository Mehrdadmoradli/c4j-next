package fixture;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class DiscoveryIT {
    @TempDir Path temp;

    record Output(List<String> stdout, List<String> stderr) {}

    private Output launch(boolean agent) throws Exception {
        String jar = System.getProperty("agentJar");
        var command = new ArrayList<>(List.of(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-ea"));
        if (agent) command.add("-javaagent:" + jar + "=debug");
        command.addAll(List.of("-cp", System.getProperty("fixtureClasses") + File.pathSeparator + jar,
                "fixture.DiscoveryApp"));
        Path out = temp.resolve(agent + ".out");
        Path err = temp.resolve(agent + ".err");
        Process child = new ProcessBuilder(command)
                .redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        try {
            assertTrue(child.waitFor(30, TimeUnit.SECONDS), "Fixture timed out");
            assertEquals(0, child.exitValue(), Files.readString(err));
            return new Output(Files.readAllLines(out), Files.readAllLines(err));
        } finally {
            child.destroyForcibly();
        }
    }

    @Test
    void discoversOnlyTheDirectAssociationAndPreservesApplicationResults() throws Exception {
        Output baseline = launch(false);
        Output instrumented = launch(true);
        assertEquals(List.of("5/5", "3/3"), baseline.stdout());
        assertEquals(baseline.stdout(), instrumented.stdout());
        assertEquals(List.of("C4J Next discovered fixture.DiscoveryApp$BankAccount -> "
                        + "fixture.DiscoveryApp$BankAccountContract"),
                instrumented.stderr().stream().filter(line -> line.startsWith("C4J Next discovered ")).toList());
    }
}
