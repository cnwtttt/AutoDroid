package com.autodroid;

public class InboxCheckpointTest {
    private static int checks;
    private static void check(boolean ok) { checks++; if(!ok) throw new AssertionError("inbox check "+checks); }
    public static void main(String[] args) {
        InboxCheckpoint first=new InboxCheckpoint(10000,0);
        check(!first.accept(1,9999));
        check(first.lastId()==0);
        check(first.accept(2,10000));
        check(!first.accept(2,10000));
        check(!first.accept(2,11000)); // Marking read/updating the same row cannot trigger again.
        check(!first.accept(1,11000));
        check(first.accept(3,11000));
        check(first.accept(4,11000)); // Distinct IDs with identical times remain distinct.
        InboxCheckpoint restart=new InboxCheckpoint(10000,first.lastId());
        check(!restart.accept(4,11000));
        check(restart.accept(5,12000));
        check(!restart.accept(6,9000)); // Cloud-imported old message is ignored.
        check(restart.accept(7,12500));
        InboxCheckpoint reenabled=new InboxCheckpoint(20000,0);
        check(!reenabled.accept(7,12500));
        check(reenabled.accept(8,20001));
        check(!reenabled.accept(8,20001));
        System.out.println("PASS: "+checks+" inbox checkpoint checks");
    }
}
