package fixture;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MethodContractsIT {
    @TempDir Path temp;

    @Test
    void enforcesNormalReturnContractsAndRecoversFromViolations() throws Exception {
        var output = DiscoveryIT.launchFixture(temp, "fixture.MethodContractsApp", true, false);
        assertEquals(List.of("method contracts passed"), output.stdout());
        assertTrue(output.stderr().stream().noneMatch(line -> line.startsWith("C4J Next could not transform")),
                output.stderr().toString());
    }
}
