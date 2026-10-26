package com.snehal.neopay.payment.simulation;

/**
 * CLI entry point.
 *
 * <pre>
 * java -cp payment-service/target/classes \
 *      com.snehal.neopay.payment.simulation.SimulationMain 42 10000000
 * </pre>
 */
public final class SimulationMain {

    private SimulationMain() {
    }

    public static void main(String[] args) {
        long seed = args.length > 0 ? Long.parseLong(args[0]) : 42L;
        int transactions = args.length > 1 ? Integer.parseInt(args[1]) : 1_000_000;

        long start = System.nanoTime();
        SimulationResult result = PaymentSimulator.run(seed, transactions);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("seed=%d transactions=%d elapsedMs=%d%n",
                result.seed(), result.transactions(), elapsedMs);
        System.out.printf("captured=%d failedAuth=%d duplicates=%d refunds=%d settled=%d%n",
                result.captured(), result.failedAuthorization(),
                result.duplicateRequests(), result.refunds(), result.settled());
        System.out.printf("debits=%d credits=%d finalMerchantPayable=%d%n",
                result.totalDebits(), result.totalCredits(), result.finalMerchantPayable());
        System.out.printf("violations=%d%n", result.violations().size());
        result.violations().forEach(v -> System.out.println("  " + v));

        if (!result.clean()) {
            System.exit(1);
        }
    }
}
