package com.acctmgr;

import java.util.HashMap;
import java.util.Map;

public class DeadlockDemo {
    
    static class TestAccount {
        private final Map<Integer, Integer> accounts;
        private final Object lock1 = new Object();
        private final Object lock2 = new Object();
        
        public TestAccount() {
            this.accounts = new HashMap<>();
            accounts.put(1, 1000);
            accounts.put(2, 1000);
        }
        
        public void transfer(int fromAccount, int toAccount, int amount) {
            transferWithLockOrdering(fromAccount, toAccount, amount);
        }
        
        private void transferWithLockOrdering(int fromAccount, int toAccount, int amount) {
            Object firstLock = (fromAccount < toAccount) ? lock1 : lock2;
            Object secondLock = (fromAccount < toAccount) ? lock2 : lock1;
            
            synchronized (firstLock) {
                synchronized (secondLock) {
                    int fromBalance = accounts.get(fromAccount);
                    int toBalance = accounts.get(toAccount);
                    
                    if (fromBalance >= amount) {
                        accounts.put(fromAccount, fromBalance - amount);
                        accounts.put(toAccount, toBalance + amount);
                        System.out.println("Transfer completed: " + amount);
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
    
    public static void main(String[] args) {
        System.out.println("Starting DeadlockDemo");
        
        TestAccount account = new TestAccount();
        
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
            e.printStackTrace();
        }
        
        System.out.println("Final balance account 1: " + account.getBalance(1));
        System.out.println("Final balance account 2: " + account.getBalance(2));
        System.out.println("DeadlockDemo completed");
    }
}
