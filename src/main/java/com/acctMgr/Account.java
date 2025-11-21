package com.acctmgr;

import java.util.HashMap;
import java.util.Map;

public class Account {
    private final Map<Integer, Integer> accounts;
    private final Object lock1 = new Object();
    private final Object lock2 = new Object();
    private boolean useOptimizedPath = false;
    
    public Account() {
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