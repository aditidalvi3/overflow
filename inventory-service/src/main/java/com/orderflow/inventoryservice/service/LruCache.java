package com.orderflow.inventoryservice.service;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Fixed-capacity LRU cache: a {@link HashMap} for O(1) key lookup plus an intrusive doubly-linked
 * list threaded through the same nodes for O(1) recency reordering and O(1) eviction of the
 * least-recently-used entry - no scan of the map is ever needed for {@link #get}, {@link #put},
 * or eviction. This is the same complexity class as {@code LinkedHashMap}'s access-order mode
 * with {@code removeEldestEntry} overridden, built from scratch to make the mechanics explicit
 * rather than delegate to it.
 *
 * <p>Not thread-safe on its own - {@code synchronized} at the call site (see
 * {@link ProductLruCache}) rather than inside every method, since callers typically want a single
 * get-then-put to be atomic together (check cache, miss, fetch, populate) and synchronizing only
 * individual methods here wouldn't give that anyway.
 */
final class LruCache<K, V> {

    private final int capacity;
    private final Map<K, Node<K, V>> index = new HashMap<>();
    private final Node<K, V> head = new Node<>(null, null); // most-recently-used side
    private final Node<K, V> tail = new Node<>(null, null); // least-recently-used side

    LruCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive: " + capacity);
        }
        this.capacity = capacity;
        head.next = tail;
        tail.prev = head;
    }

    Optional<V> get(K key) {
        Node<K, V> node = index.get(key);
        if (node == null) {
            return Optional.empty();
        }
        moveToFront(node);
        return Optional.of(node.value);
    }

    void put(K key, V value) {
        Node<K, V> existing = index.get(key);
        if (existing != null) {
            existing.value = value;
            moveToFront(existing);
            return;
        }
        Node<K, V> node = new Node<>(key, value);
        index.put(key, node);
        addToFront(node);
        if (index.size() > capacity) {
            Node<K, V> lru = tail.prev;
            unlink(lru);
            index.remove(lru.key);
        }
    }

    void evict(K key) {
        Node<K, V> node = index.remove(key);
        if (node != null) {
            unlink(node);
        }
    }

    void clear() {
        index.clear();
        head.next = tail;
        tail.prev = head;
    }

    int size() {
        return index.size();
    }

    private void moveToFront(Node<K, V> node) {
        unlink(node);
        addToFront(node);
    }

    private void addToFront(Node<K, V> node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void unlink(Node<K, V> node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private static final class Node<K, V> {
        final K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
}
