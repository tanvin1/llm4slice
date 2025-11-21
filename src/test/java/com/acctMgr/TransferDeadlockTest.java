package com.acctmgr;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Demonstrates the difference between dynamic slicing (Slicer4J) and static analysis (LLM)
 * 
 * Slicer4J will only capture code executed during testBasicTransfer
 * LLM will analyze all possible paths including the deadlock-prone ones
 */
public class TransferDeadlockTest {
    
    /**
     * Test Account class with multiple locking strategies
     */
    static class TestAccount {
        private final Map<Integer, Integer> accounts;
        private final Object lock1 = new Object();
        private final Object lock2 = new Object();
        private boolean useOptimizedPath = false;
        
        public TestAccount() {
            this.accounts = new HashMap<>();
            accounts.put(1, 1000);
            accounts.put(2, 1000);
        }
        
        public void setOptimizedPath(boolean optimized) {
            this.useOptimizedPath = optimized;
        }
        
        /**
         * Transfer method with multiple execution paths.
         * Path 1 (useOptimizedPath=false): Uses lock ordering by account ID (SAFE)
         * Path 2 (useOptimizedPath=true): Direct locking without ordering (DEADLOCK PRONE)
         */
        public void transfer(int fromAccount, int toAccount, int amount) {
            if (useOptimizedPath) {
                // PATH 2: DANGEROUS - Direct locking can cause deadlock
                // This path is NOT executed in the basic test but LLM should detect it
                transferDirectLocking(fromAccount, toAccount, amount);
            } else {
                // PATH 1: SAFE - Lock ordering prevents deadlock
                // This is the path executed in the test
                transferWithLockOrdering(fromAccount, toAccount, amount);
            }
        }
        
        /**
         * SAFE PATH: Uses lock ordering to prevent deadlock
         * Slicer4J will capture this because the test executes it
         */
        private void transferWithLockOrdering(int fromAccount, int toAccount, int amount) {
            // Determine lock order based on account ID
            Object firstLock = (fromAccount < toAccount) ? lock1 : lock2;
            Object secondLock = (fromAccount < toAccount) ? lock2 : lock1;
            
            synchronized (firstLock) {
                synchronized (secondLock) {
                    int fromBalance = accounts.get(fromAccount);
                    int toBalance = accounts.get(toAccount);
                    
                    if (fromBalance >= amount) {
                        accounts.put(fromAccount, fromBalance - amount);
                        accounts.put(toAccount, toBalance + amount);
                        System.out.println("Transfer completed: " + amount + " from " + 
                                         fromAccount + " to " + toAccount);
                    }
                }
            }
        }
        
        /**
         * DANGEROUS PATH: Direct locking can cause deadlock
         * Slicer4J will NOT capture this because the test doesn't execute it
         * But LLM static analysis SHOULD detect this potential deadlock
         */
        private void transferDirectLocking(int fromAccount, int toAccount, int amount) {
            // DEADLOCK RISK: Lock acquisition order depends on parameter order
            // Thread A: transfer(1,2) locks lock1 then lock2
            // Thread B: transfer(2,1) locks lock2 then lock1
            // -> DEADLOCK!
            
            Object lockA = (fromAccount == 1) ? lock1 : lock2;
            Object lockB = (toAccount == 1) ? lock1 : lock2;
            
            synchronized (lockA) {
                // Simulate some processing
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                synchronized (lockB) {
                    int fromBalance = accounts.get(fromAccount);
                    int toBalance = accounts.get(toAccount);
                    
                    if (fromBalance >= amount) {
                        accounts.put(fromAccount, fromBalance - amount);
                        accounts.put(toAccount, toBalance + amount);
                        System.out.println("Optimized transfer completed: " + amount);
                    }
                }
            }
        }
        
        /**
         * Additional method that could be involved in deadlock scenarios
         * but is never called in the test
         */
        public void bulkTransfer(int[] fromAccounts, int[] toAccounts, int[] amounts) {
            // This method has complex locking that could deadlock
            // Slicer4J will miss this entirely if test doesn't call it
            // LLM should analyze it as potentially deadlock-prone
            
            synchronized (lock1) {
                for (int i = 0; i < fromAccounts.length; i++) {
                    synchronized (lock2) {
                        transfer(fromAccounts[i], toAccounts[i], amounts[i]);
                    }
                }
            }
        }
        
        public int getBalance(int accountId) {
            synchronized (lock1) {
                return accounts.get(accountId);
            }
        }
    }
    
    /**
     * Basic test that only exercises the SAFE path
     * Slicer4J slicing from this test will ONLY capture:
     * - transferWithLockOrdering method
     * - The safe lock ordering logic
     * 
     * It will MISS:
     * - transferDirectLocking method (deadlock-prone)
     * - bulkTransfer method (also deadlock-prone)
     * - The dangerous locking patterns
     */
    @Test
    public void testBasicTransfer() {
        TestAccount account = new TestAccount();
        
        // useOptimizedPath defaults to false, so only safe path executes
        Thread t1 = new Thread(() -> {
            account.transfer(1, 2, 100);
        });
        
        Thread t2 = new Thread(() -> {
            account.transfer(2, 1, 50);
        });
        
        t1.start();
        t2.start();
        
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }
        
        // Verify balances
        assertEquals(950, account.getBalance(1));
        assertEquals(1050, account.getBalance(2));
    }
    
    /**
     * Test that would expose the deadlock if uncommented
     * But for our comparison, we leave this commented out
     * so Slicer4J doesn't capture the dangerous path
     */
    /*
    @Test(timeout = 5000)
    public void testDeadlockScenario() {
        TestAccount account = new TestAccount();
        account.setOptimizedPath(true); // Enable dangerous path
        
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                account.transfer(1, 2, 10);
            }
        });
        
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                account.transfer(2, 1, 10);
            }
        });
        
        t1.start();
        t2.start();
        
        try {
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            fail("Deadlock detected!");
        }
    }
    */
}
