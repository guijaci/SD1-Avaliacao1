package edu.utfpr.guilhermej.sisdist.av1.util;

public class Pair <L, R> {
    private L left;
    private R right;

    public Pair(L left, R right) {
        this.left = left;
        this.right = right;
    }

    public L getLeft() {
        return left;
    }

    public Pair setLeft(L left) {
        this.left = left;
        return this;
    }

    public R getRight() {
        return right;
    }

    public Pair setRight(R right) {
        this.right = right;
        return this;
    }
}
