package com.intellij.util.containers;

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * Segments are specialized versions of hash tables.  This
 * subclasses from ReentrantLock opportunistically, just to
 * simplify some locking and avoid separate construction.
 */
class _CHMSegment<K, V> {
  private static final StripedReentrantLocks STRIPED_REENTRANT_LOCKS = StripedReentrantLocks.getInstance();
  private final byte lockIndex = (byte)STRIPED_REENTRANT_LOCKS.allocateLockIndex();

  private void lock() {
    STRIPED_REENTRANT_LOCKS.lock((int)lockIndex & 0xff);
    if (modificationBlocked) throw new ConcurrentModificationException();
  }

  private void unlock() {
    STRIPED_REENTRANT_LOCKS.unlock((int)lockIndex & 0xff);
  }
  /*
  * Segments maintain a table of entry lists that are ALWAYS
  * kept in a consistent state, so can be read without locking.
  * Next fields of nodes are immutable (final).  All list
  * additions are performed at the front of each bin. This
  * makes it easy to check changes, and also fast to traverse.
  * When nodes would otherwise be changed, new nodes are
  * created to replace them. This works well for hash tables
  * since the bin lists tend to be short. (The average length
  * is less than two for the default load factor threshold.)
  *
  * Read operations can thus proceed without locking, but rely
  * on selected uses of volatiles to ensure that completed
  * write operations performed by other threads are
  * noticed. For most purposes, the "count" field, tracking the
  * number of elements, serves as that volatile variable
  * ensuring visibility.  This is convenient because this field
  * needs to be read in many read operations anyway:
  *
  *   - All (unsynchronized) read operations must first read the
  *     "count" field, and should not look at table entries if
  *     it is 0.
  *
  *   - All (synchronized) write operations should write to
  *     the "count" field after structurally changing any bin.
  *     The operations must not take any action that could even
  *     momentarily cause a concurrent read operation to see
  *     inconsistent data. This is made easier by the nature of
  *     the read operations in Map. For example, no operation
  *     can reveal that the table has grown but the threshold
  *     has not yet been updated, so there are no atomicity
  *     requirements for this with respect to reads.
  *
  * As a guide, all critical volatile reads and writes to the
  * count field are marked in code comments.
  */

  /**
   * The number of elements in this segment's region.
   */
  volatile int count;

  /*
   * The table is rehashed when its size exceeds this threshold.
   */
  private int threshold() {
    return (int)(table.length * loadFactor);
  }

  /**
   * The per-segment table. Declared as a raw type, casted
   * to HashEntry<K,V> on each use.
   */
  volatile HashEntry[] table;

  private static final float loadFactor = 0.75f;
  private volatile boolean modificationBlocked; // the only state transition is 0 -> 1

  _CHMSegment(int initialCapacity) {
    setTable(new HashEntry[initialCapacity]);
  }

  public void blockModification() {
    try {
      lock();
      modificationBlocked = true;
    }
    finally {
      unlock();
    }
  }

  /*
   * Set table to new HashEntry array.
   * Call only while holding lock or in constructor.
   */
  void setTable(HashEntry[] newTable) {
    table = newTable;
  }

  /*
   * Return properly casted first entry of bin for given hash
   */
  HashEntry<K, V> getFirst(int hash) {
    HashEntry[] tab = table;
    return tab[hash & tab.length - 1];
  }

  /*
   * Read value field of an entry under lock. Called if value
   * field ever appears to be null. This is possible only if a
   * compiler happens to reorder a HashEntry initialization with
   * its table assignment, which is legal under memory model
   * but is not known to ever occur.
   */
  V readValueUnderLock(HashEntry<K, V> e) {
    try {
      lock();
      return e.value;
    }
    finally {
      unlock();
    }
  }

  /* Specialized implementations of map methods */

  V get(K key, int hash) {
    if (count != 0) { // read-volatile
      HashEntry<K, V> e = getFirst(hash);
      while (e != null) {
        if (e.hash == hash && getHashingStrategy().equals(key, e.key)) {
          V v = e.value;
          if (v != null) {
            return v;
          }
          return readValueUnderLock(e); // recheck
        }
        e = e.next;
      }
    }
    return null;
  }

  boolean containsKey(K key, int hash) {
    if (count != 0) { // read-volatile
      HashEntry<K, V> e = getFirst(hash);
      while (e != null) {
        if (e.hash == hash && getHashingStrategy().equals(key, e.key)) {
          return true;
        }
        e = e.next;
      }
    }
    return false;
  }

  public boolean containsValue(Object value) {
    if (count != 0) { // read-volatile
      HashEntry[] tab = table;
      int len = tab.length;
      for (int i = 0; i < len; i++) {
        for (HashEntry<K, V> e = tab[i]; e != null; e = e.next) {
          V v = e.value;
          if (v == null) // recheck
          {
            v = readValueUnderLock(e);
          }
          if (value.equals(v)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  boolean replace(@NotNull K key, int hash, @NotNull V oldValue, @NotNull V newValue) {
    try {
      lock();
      HashEntry<K, V> e = getFirst(hash);
      while (e != null && (e.hash != hash || !getHashingStrategy().equals(key, e.key))) {
        e = e.next;
      }

      boolean replaced = false;
      if (e != null && oldValue.equals(e.value)) {
        replaced = true;
        e.value = newValue;
      }
      return replaced;
    }
    finally {
      unlock();
    }
  }

  V replace(@NotNull K key, int hash, @NotNull V newValue) {
    try {
      lock();
      HashEntry<K, V> e = getFirst(hash);
      while (e != null && (e.hash != hash || !getHashingStrategy().equals(key, e.key))) {
        e = e.next;
      }

      V oldValue = null;
      if (e != null) {
        oldValue = e.value;
        e.value = newValue;
      }
      return oldValue;
    }
    finally {
      unlock();
    }
  }


  V put(@NotNull K key, int hash, @NotNull V value, boolean onlyIfAbsent) {
    try {
      lock();
      int c = count;
      if (c++ > threshold()) // ensure capacity
      {
        rehash();
      }
      HashEntry[] tab = table;
      int index = hash & tab.length - 1;
      HashEntry<K, V> first = tab[index];
      HashEntry<K, V> e = first;
      while (e != null && (e.hash != hash || !getHashingStrategy().equals(key, e.key))) {
        e = e.next;
      }

      V oldValue;
      if (e != null) {
        oldValue = e.value;
        if (!onlyIfAbsent) {
          e.value = value;
        }
      }
      else {
        oldValue = null;
        tab[index] = new HashEntry<K, V>(key, hash, first, value);
        count = c; // write-volatile
      }
      return oldValue;
    }
    finally {
      unlock();
    }
  }

  void rehash() {
    HashEntry[] oldTable = table;
    int oldCapacity = oldTable.length;
    if (oldCapacity >= StripedLockConcurrentHashMap.MAXIMUM_CAPACITY) {
      return;
    }

    /*
    * Reclassify nodes in each list to new Map.  Because we are
    * using power-of-two expansion, the elements from each bin
    * must either stay at same index, or move with a power of two
    * offset. We eliminate unnecessary node creation by catching
    * cases where old nodes can be reused because their next
    * fields won't change. Statistically, at the default
    * threshold, only about one-sixth of them need cloning when
    * a table doubles. The nodes they replace will be garbage
    * collectable as soon as they are no longer referenced by any
    * reader thread that may be in the midst of traversing table
    * right now.
    */

    HashEntry[] newTable = new HashEntry[oldCapacity << 1];
    int sizeMask = newTable.length - 1;
    for (int i = 0; i < oldCapacity; i++) {
      // We need to guarantee that any existing reads of old Map can
      //  proceed. So we cannot yet null out each bin.
      HashEntry<K, V> e = oldTable[i];

      if (e != null) {
        HashEntry<K, V> next = e.next;
        int idx = e.hash & sizeMask;

        //  Single node on list
        if (next == null) {
          newTable[idx] = e;
        }

        else {
          // Reuse trailing consecutive sequence at same slot
          HashEntry<K, V> lastRun = e;
          int lastIdx = idx;
          for (HashEntry<K, V> last = next;
               last != null;
               last = last.next) {
            int k = last.hash & sizeMask;
            if (k != lastIdx) {
              lastIdx = k;
              lastRun = last;
            }
          }
          newTable[lastIdx] = lastRun;

          // Clone all remaining nodes
          for (HashEntry<K, V> p = e; p != lastRun; p = p.next) {
            int k = p.hash & sizeMask;
            HashEntry<K, V> n = newTable[k];
            newTable[k] = new HashEntry<K, V>(p.key, p.hash, n, p.value);
          }
        }
      }
    }
    setTable(newTable);
  }

  /*
   * Remove; match on key only if value null, else match both.
   */
  V remove(@NotNull K key, int hash, @Nullable("null means don't care") Object value) {
    try {
      lock();
      int c = count - 1;
      HashEntry[] tab = table;
      int index = hash & tab.length - 1;
      HashEntry<K, V> first = tab[index];
      HashEntry<K, V> e = first;
      while (e != null && (e.hash != hash || !getHashingStrategy().equals(key, e.key))) {
        e = e.next;
      }

      V oldValue = null;
      if (e != null) {
        V v = e.value;
        if (value == null || value.equals(v)) {
          oldValue = v;
          // All entries following removed node can stay
          // in list, but all preceding ones need to be
          // cloned.
          HashEntry<K, V> newFirst = e.next;
          for (HashEntry<K, V> p = first; p != e; p = p.next) {
            newFirst = new HashEntry<K, V>(p.key, p.hash, newFirst, p.value);
          }
          tab[index] = newFirst;
          count = c; // write-volatile
        }
      }
      return oldValue;
    }
    finally {
      unlock();
    }
  }

  public void clear() {
    if (count != 0) {
      try {
        lock();
        HashEntry[] tab = table;
        for (int i = 0; i < tab.length; i++) {
          tab[i] = null;
        }
        count = 0; // write-volatile
      }
      finally {
        unlock();
      }
    }
  }

  protected TObjectHashingStrategy<K> getHashingStrategy() {
    return StripedLockConcurrentHashMap.CanonicalHashingStrategy.getInstance();
  }

  /**
     * ConcurrentHashMap list entry. Note that this is never exported
     * out as a user-visible Map.Entry.
     * <p/>
     * Because the value field is volatile, not final, it is legal wrt
     * the Java Memory Model for an unsynchronized reader to see null
     * instead of initial value when read via a data race.  Although a
     * reordering leading to this is not likely to ever actually
     * occur, the Segment.readValueUnderLock method is used as a
     * backup in case a null (pre-initialized) value is ever seen in
     * an unsynchronized access method.
     */
    static final class HashEntry<K, V> {
      @NotNull final K key;
      final int hash;
      @NotNull volatile V value;
      final HashEntry<K, V> next;

      HashEntry(@NotNull K key, int hash, HashEntry<K, V> next, @NotNull V value) {
        this.key = key;
        this.hash = hash;
        this.next = next;
        this.value = value;
      }
    }
}
