package fixture;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InheritanceIT {
    @TempDir Path temp;

    @Test
    void resolvesAndExecutesOrderedClassAndInterfacePlans() throws Exception {
        String fixture = "io.github.Mehrdadmoradli.c4jnext.runtime.InheritanceApp";
        var output = DiscoveryIT.launchFixture(temp, fixture, true, false);
        assertEquals(List.of("contract inheritance passed"), output.stdout());
        assertTrue(output.stderr().stream().noneMatch(line -> line.startsWith("C4J Next could not transform")),
                output.stderr().toString());
    }
}
