package com.bloxbean.intelliada.idea.common;

public class Tuple<K, V> {
    private K _1;
    private V _2;

    public Tuple(K _1, V _2) {
        this._1 = _1;
        this._2 = _2;
    }

    public K _1() {
        return _1;
    }

    public V _2() {
        return _2;
    }
}
