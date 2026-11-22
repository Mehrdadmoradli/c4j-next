# C4J Next

C4J Next is a Java 25 Design by Contract prototype built with Byte Buddy.

Build and test the shaded agent from the repository root with `mvn clean verify`. Run an
application with Java assertions enabled. Put the agent JAR on the application classpath
as well when compiling or using the contract API. For an application packaged as
`app.jar` with its dependencies and main class configured, run:

```sh
java -ea -javaagent:target/c4j-next-0.1.0-SNAPSHOT.jar -jar app.jar
```

A concrete class can name its contract with `@ContractReference`. The contract extends
the target class and declares an override for each method it checks.

```java
import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;
import io.github.Mehrdadmoradli.c4jnext.annotations.Target;

import static io.github.Mehrdadmoradli.c4jnext.Condition.*;

@ContractReference(AccountChecks.class)
class Account {
    int balance;

    public int deposit(int amount) {
        return balance += amount;
    }
}

class AccountChecks extends Account {
    @Target private Account account;

    @Override
    public int deposit(int amount) {
        if (preCondition()) {
            assert amount > 0 : "deposit must be positive";
        }
        if (postCondition()) {
            assert result(int.class) == account.balance : "wrong balance";
        }
        return 0;
    }
}
```

A failed precondition stops the call before the body runs. Postconditions run after
normal completion. `result()` and `result(Class<T>)` expose the application result,
including boxed primitives and `null` for a `void` method. An assertion failure keeps
its cause and stack trace and adds the phase to the message, for example
`deposit must be positive (pre-condition)`. A `UsageError` propagates unchanged.
Other exceptions from contract evaluation become a `ContractError` with the original
exception as its cause.

Phase 2 supports direct associations on concrete classes and instance methods declared
by those classes. Use a contract with a no-argument constructor and call the phase
predicates directly in the contract override. Each checked call creates its own contract
instance and shares it between the precondition and postcondition. C4J's reuse of a
contract instance across calls to the same receiver remains outside this scope. The
`@ContractReference` and `@Target` annotations use runtime retention for reflection.

See the [feature support matrix](docs/feature-support.md) and
[pinned reference sources](docs/reference-sources.md).
