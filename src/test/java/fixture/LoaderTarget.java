package fixture;

import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;

import static io.github.Mehrdadmoradli.c4jnext.Condition.postCondition;
import static io.github.Mehrdadmoradli.c4jnext.Condition.result;

@ContractReference(LoaderTarget.Checks.class)
public class LoaderTarget {
    public int run(int value) {
        return value;
    }

    public static class Checks extends LoaderTarget {
        @Override
        public int run(int value) {
            if (postCondition()) {
                assert result(int.class) == value;
            }
            return 0;
        }
    }
}
