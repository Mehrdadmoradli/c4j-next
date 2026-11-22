package fixture;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvocationIsolationIT {
    @TempDir Path temp;

    @Test
    void isolatesCallsAndPreservesApplicationFailures() throws Exception {
        String fixture = "io.github.Mehrdadmoradli.c4jnext.runtime.InvocationIsolationApp";
        var output = DiscoveryIT.launchFixture(temp, fixture, true, false);
        assertEquals(List.of("invocation isolation passed"), output.stdout());
        assertTrue(output.stderr().stream().noneMatch(line -> line.startsWith("C4J Next could not transform")),
                output.stderr().toString());
    }
}
