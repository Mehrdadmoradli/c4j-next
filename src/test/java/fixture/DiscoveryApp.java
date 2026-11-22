package fixture;

import io.github.Mehrdadmoradli.c4jnext.annotations.ContractReference;

public class DiscoveryApp {
    @ContractReference(BankAccountContract.class)
    static class BankAccount {
        int balance;

        int deposit(int amount) {
            return balance += amount;
        }
    }

    static class BankAccountContract extends BankAccount {}
    static class Control extends BankAccount {}

    public static void main(String[] args) {
        BankAccount account = new BankAccount();
        Control control = new Control();
        System.out.println(account.deposit(5) + "/" + account.balance);
        System.out.println(control.deposit(3) + "/" + control.balance);
    }
}
