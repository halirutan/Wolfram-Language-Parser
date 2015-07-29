/*
 * Copyright 2000-2010 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.containers;

import gnu.trove.TObjectHashingStrategy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * similar to java.util.ConcurrentHashMap except:
 * conserved as much memory as possible by
 * -- using only one Segment
 * -- eliminating unnecessary fields
 * -- using one of 256 ReentrantLock for Segment statically preallocated in {@link StripedReentrantLocks}
 * added hashing strategy argument
 * made not Serializable
 */
public class StripedLockConcurrentHashMap<K, V> extends _CHMSegment<K, V> implements ConcurrentMap<K, V> {
  /* ---------------- Constants -------------- */

  /**
   * The default initial number of table slots for this table.
   * Used when not otherwise specified in constructor.
   */
  static int DEFAULT_INITIAL_CAPACITY = 16;

  /**
   * The maximum capacity, used if a higher value is implicitly
   * specified by either of the constructors with arguments.  MUST
   * be a power of two <= 1<<30 to ensure that entries are indexible
   * using ints.
   */
  static final int MAXIMUM_CAPACITY = 1 << 30;

  /**
   * The default load factor for this table.  Used when not
   * otherwise specified in constructor.
   */
  public static final float DEFAULT_LOAD_FACTOR = 0.75f;


  /* ---------------- Public operations -------------- */

  public static <K,V> StripedLockConcurrentHashMap<K,V> createWithStrategy(@NotNull final TObjectHashingStrategy<K> hashingStrategy, int initialCapacity) {
    return new StripedLockConcurrentHashMap<K, V>(initialCapacity){
      @Override
      protected TObjectHashingStrategy<K> getHashingStrategy() {
        return hashingStrategy;
      }
    };
  }

  public StripedLockConcurrentHashMap(int initialCapacity) {
    super(getInitCap(initialCapacity, DEFAULT_LOAD_FACTOR));
  }

  private static int getInitCap(int initialCapacity, float loadFactor) {
    if (loadFactor <= 0 || initialCapacity < 0) {
      throw new IllegalArgumentException();
    }

    if (initialCapacity > MAXIMUM_CAPACITY) {
      initialCapacity = MAXIMUM_CAPACITY;
    }
    int cap = 1;
    while (cap < initialCapacity) {
      cap <<= 1;
    }
    return cap;
  }

  /**
   * Creates a new, empty map with a default initial capacity,
   * load factor, and concurrencyLevel.
   */
  public StripedLockConcurrentHashMap() {
    this(DEFAULT_INITIAL_CAPACITY);
  }

  /**
   * Creates a new map with the same mappings as the given map.  The
   * map is created with a capacity of twice the number of mappings in
   * the given map or 11 (whichever is greater), and a default load factor
   * and concurrencyLevel.
   *
   * @param t the map
   */
  public StripedLockConcurrentHashMap(@NotNull Map<? extends K, ? extends V> t) {
    this(Math.max((int)(t.size() / DEFAULT_LOAD_FACTOR) + 1, 11));
    putAll(t);
  }

  // inherit Map javadoc

  public boolean isEmpty() {
    return count == 0;
  }

  // inherit Map javadoc

  public int size() {
    return count;
  }


  /**
   * Returns the value to which the specified key is mapped in this table.
   *
   * @param key a key in the table.
   * @return the value to which the key is mapped in this table;
   *         <tt>null</tt> if the key is not mapped to any value in
   *         this table.
   * @throws NullPointerException if the key is
   *                              <tt>null</tt>.
   */
  public V get(@NotNull Object key) {
    K kKey = (K)key;
    int hash = getHashingStrategy().computeHashCode(kKey); // throws NullPointerException if key null
    return get(kKey, hash);
  }

  /**
   * Tests if the specified object is a key in this table.
   *
   * @param key possible key.
   * @return <tt>true</tt> if and only if the specified object
   *         is a key in this table, as determined by the
   *         <tt>equals</tt> method; <tt>false</tt> otherwise.
   * @throws NullPointerException if the key is
   *                              <tt>null</tt>.
   */
  public boolean containsKey(@NotNull Object key) {
    K kKey = (K)key;
    int hash = getHashingStrategy().computeHashCode(kKey); // throws NullPointerException if key null
    return containsKey(kKey, hash);
  }

  /**
   * Legacy method testing if some key maps into the specified value
   * in this table.  This method is identical in functionality to
   * {@link #containsValue}, and  exists solely to ensure
   * full compatibility with class {@link java.util.Hashtable},
   * which supported this method prior to introduction of the
   * Java Collections framework.
   *
   * @param value a value to search for.
   * @return <tt>true</tt> if and only if some key maps to the
   *         <tt>value</tt> argument in this table as
   *         determined by the <tt>equals</tt> method;
   *         <tt>false</tt> otherwise.
   * @throws NullPointerException if the value is <tt>null</tt>.
   */
  public boolean contains(@NotNull Object value) {
    return containsValue(value);
  }

  /**
   * Maps the specified <tt>key</tt> to the specified
   * <tt>value</tt> in this table. Neither the key nor the
   * value can be <tt>null</tt>.
   * <p/>
   * <p> The value can be retrieved by calling the <tt>get</tt> method
   * with a key that is equal to the original key.
   *
   * @param key   the table key.
   * @param value the value.
   * @return the previous value of the specified key in this table,
   *         or <tt>null</tt> if it did not have one.
   * @throws NullPointerException if the key or value is
   *                              <tt>null</tt>.
   */
  public V put(@NotNull K key, @NotNull V value) {
    int hash = getHashingStrategy().computeHashCode(key);
    return put(key, hash, value, false);
  }

  /**
   * If the specified key is not already associated
   * with a value, associate it with the given value.
   * This is equivalent to
   * <pre>
   *   if (!map.containsKey(key))
   *      return map.put(key, value);
   *   else
   *      return map.get(key);
   * </pre>
   * Except that the action is performed atomically.
   *
   * @param key   key with which the specified value is to be associated.
   * @param value value to be associated with the specified key.
   * @return previous value associated with specified key, or <tt>null</tt>
   *         if there was no mapping for key.
   * @throws NullPointerException if the specified key or value is
   *                              <tt>null</tt>.
   */
  public V putIfAbsent(@NotNull K key, @NotNull V value) {
    int hash = getHashingStrategy().computeHashCode(key);
    return put(key, hash, value, true);
  }


  /**
   * Copies all of the mappings from the specified map to this one.
   * <p/>
   * These mappings replace any mappings that this map had for any of the
   * keys currently in the specified Map.
   *
   * @param t Mappings to be stored in this map.
   */
  public void putAll(@NotNull Map<? extends K, ? extends V> t) {
    for (Entry<? extends K, ? extends V> e : t.entrySet()) {
      V value = e.getValue();
      if (value != null) {  // null is possible if the entry has just been removed
        put(e.getKey(), value);
      }
    }
  }

  /**
   * Removes the key (and its corresponding value) from this
   * table. This method does nothing if the key is not in the table.
   *
   * @param key the key that needs to be removed.
   * @return the value to which the key had been mapped in this table,
   *         or <tt>null</tt> if the key did not have a mapping.
   * @throws NullPointerException if the key is
   *                              <tt>null</tt>.
   */
  public V remove(@NotNull Object key) {
    K kKey = (K)key;
    int hash = getHashingStrategy().computeHashCode(kKey);
    return remove(kKey, hash, null);
  }

  /**
   * Remove entry for key only if currently mapped to given value.
   * Acts as
   * <pre>
   *  if (map.get(key).equals(value)) {
   *     map.remove(key);
   *     return true;
   * } else return false;
   * </pre>
   * except that the action is performed atomically.
   *
   * @param key   key with which the specified value is associated.
   * @param value value associated with the specified key.
   * @return true if the value was removed
   * @throws NullPointerException if the specified key is
   *                              <tt>null</tt>.
   */
  public boolean remove(@NotNull Object key, @NotNull Object value) {
    K kKey = (K)key;
    int hash = getHashingStrategy().computeHashCode(kKey);
    return remove(kKey, hash, value) != null;
  }


  /**
   * Replace entry for key only if currently mapped to given value.
   * Acts as
   * <pre>
   *  if (map.get(key).equals(oldValue)) {
   *     map.put(key, newValue);
   *     return true;
   * } else return false;
   * </pre>
   * except that the action is performed atomically.
   *
   * @param key      key with which the specified value is associated.
   * @param oldValue value expected to be associated with the specified key.
   * @param newValue value to be associated with the specified key.
   * @return true if the value was replaced
   * @throws NullPointerException if the specified key or values are
   *                              <tt>null</tt>.
   */
  public boolean replace(@NotNull K key, @NotNull V oldValue, @NotNull V newValue) {
    int hash = getHashingStrategy().computeHashCode(key);
    return replace(key, hash, oldValue, newValue);
  }

  /**
   * Replace entry for key only if currently mapped to some value.
   * Acts as
   * <pre>
   *  if ((map.containsKey(key)) {
   *     return map.put(key, value);
   * } else return null;
   * </pre>
   * except that the action is performed atomically.
   *
   * @param key   key with which the specified value is associated.
   * @param value value to be associated with the specified key.
   * @return previous value associated with specified key, or <tt>null</tt>
   *         if there was no mapping for key.
   * @throws NullPointerException if the specified key or value is
   *                              <tt>null</tt>.
   */
  public V replace(@NotNull K key, @NotNull V value) {
    int hash = getHashingStrategy().computeHashCode(key);
    return replace(key, hash, value);
  }


  /**
   * Returns a set view of the keys contained in this map.  The set is
   * backed by the map, so changes to the map are reflected in the set, and
   * vice-versa.  The set supports element removal, which removes the
   * corresponding mapping from this map, via the <tt>Iterator.remove</tt>,
   * <tt>Set.remove</tt>, <tt>removeAll</tt>, <tt>retainAll</tt>, and
   * <tt>clear</tt> operations.  It does not support the <tt>add</tt> or
   * <tt>addAll</tt> operations.
   * The view's returned <tt>iterator</tt> is a "weakly consistent" iterator that
   * will never throw {@link java.util.ConcurrentModificationException},
   * and guarantees to traverse elements as they existed upon
   * construction of the iterator, and may (but is not guaranteed to)
   * reflect any modifications subsequent to construction.
   *
   * @return a set view of the keys contained in this map.
   */
  public Set<K> keySet() {
    return new KeySet(); //conserve memory by not caching keyset
  }


  /**
   * Returns a collection view of the values contained in this map.  The
   * collection is backed by the map, so changes to the map are reflected in
   * the collection, and vice-versa.  The collection supports element
   * removal, which removes the corresponding mapping from this map, via the
   * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
   * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
   * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
   * The view's returned <tt>iterator</tt> is a "weakly consistent" iterator that
   * will never throw {@link java.util.ConcurrentModificationException},
   * and guarantees to traverse elements as they existed upon
   * construction of the iterator, and may (but is not guaranteed to)
   * reflect any modifications subsequent to construction.
   *
   * @return a collection view of the values contained in this map.
   */
  public Collection<V> values() {
    return new Values(); //conserve memory by not caching
  }


  /**
   * Returns a collection view of the mappings contained in this map.  Each
   * element in the returned collection is a <tt>Map.Entry</tt>.  The
   * collection is backed by the map, so changes to the map are reflected in
   * the collection, and vice-versa.  The collection supports element
   * removal, which removes the corresponding mapping from the map, via the
   * <tt>Iterator.remove</tt>, <tt>Collection.remove</tt>,
   * <tt>removeAll</tt>, <tt>retainAll</tt>, and <tt>clear</tt> operations.
   * It does not support the <tt>add</tt> or <tt>addAll</tt> operations.
   * The view's returned <tt>iterator</tt> is a "weakly consistent" iterator that
   * will never throw {@link java.util.ConcurrentModificationException},
   * and guarantees to traverse elements as they existed upon
   * construction of the iterator, and may (but is not guaranteed to)
   * reflect any modifications subsequent to construction.
   *
   * @return a collection view of the mappings contained in this map.
   */
  public Set<Entry<K, V>> entrySet() {
    return new EntrySet(); //conserve memory by not caching
  }


  /**
   * Returns an enumeration of the keys in this table.
   *
   * @return an enumeration of the keys in this table.
   * @see #keySet
   */
  public Enumeration<K> keys() {
    return new KeyIterator();
  }

  /**
   * Returns an enumeration of the values in this table.
   *
   * @return an enumeration of the values in this table.
   * @see #values
   */
  public Enumeration<V> elements() {
    return new ValueIterator();
  }

  /* ---------------- Iterator Support -------------- */

  abstract class HashIterator {
    int nextSegmentIndex;
    int nextTableIndex;
    HashEntry[] currentTable;
    HashEntry<K, V> nextEntry;
    HashEntry<K, V> lastReturned;

    HashIterator() {
      nextSegmentIndex = 0;
      nextTableIndex = -1;
      advance();
    }

    public boolean hasMoreElements() {
      return hasNext();
    }

    final void advance() {
      if (nextEntry != null && (nextEntry = nextEntry.next) != null) {
        return;
      }

      while (nextTableIndex >= 0) {
        if ((nextEntry = (HashEntry<K, V>)currentTable[nextTableIndex--]) != null) {
          return;
        }
      }

      while (nextSegmentIndex >= 0) {
        _CHMSegment seg = StripedLockConcurrentHashMap.this;
        nextSegmentIndex--;
        if (seg.count != 0) {
          currentTable = seg.table;
          for (int j = currentTable.length - 1; j >= 0; --j) {
            if ((nextEntry = (HashEntry<K, V>)currentTable[j]) != null) {
              nextTableIndex = j - 1;
              return;
            }
          }
        }
      }
    }

    public boolean hasNext() {
      return nextEntry != null;
    }

    HashEntry<K, V> nextEntry() {
      if (nextEntry == null) {
        throw new NoSuchElementException();
      }
      lastReturned = nextEntry;
      advance();
      return lastReturned;
    }

    public void remove() {
      if (lastReturned == null) {
        throw new IllegalStateException();
      }
      StripedLockConcurrentHashMap.this.remove(lastReturned.key);
      lastReturned = null;
    }
  }

  final class KeyIterator extends HashIterator implements Iterator<K>, Enumeration<K> {
    @NotNull
    public K next() {
      return nextEntry().key;
    }

    @NotNull
    public K nextElement() {
      return nextEntry().key;
    }
  }

  final class ValueIterator extends HashIterator implements Iterator<V>, Enumeration<V> {
    @NotNull
    public V next() {
      return nextEntry().value;
    }

    @NotNull
    public V nextElement() {
      return nextEntry().value;
    }
  }


  /**
   * Entry iterator. Exported Entry objects must write-through
   * changes in setValue, even if the nodes have been cloned. So we
   * cannot return internal HashEntry objects. Instead, the iterator
   * itself acts as a forwarding pseudo-entry.
   */
  final class EntryIterator extends HashIterator implements Entry<K, V>, Iterator<Entry<K, V>> {
    @NotNull
    public Entry<K, V> next() {
      nextEntry();
      return this;
    }

    @NotNull
    public K getKey() {
      if (lastReturned == null) {
        throw new IllegalStateException("Entry was removed");
      }
      return lastReturned.key;
    }

    @Nullable("null means the entry has just been removed")
    public V getValue() {
      if (lastReturned == null) {
        throw new IllegalStateException("Entry was removed");
      }
      return get(lastReturned.key);
    }

    public V setValue(@NotNull V value) {
      if (lastReturned == null) {
        throw new IllegalStateException("Entry was removed");
      }
      return put(lastReturned.key, value);
    }

    public boolean equals(Object o) {
      // If not acting as entry, just use default.
      if (lastReturned == null) {
        return super.equals(o);
      }
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry e = (Entry)o;
      K o1 = getKey();
      K o2 = (K)e.getKey();
      return getHashingStrategy().equals(o1, o2) && getValue().equals(e.getValue());
    }

    public int hashCode() {
      // If not acting as entry, just use default.
      if (lastReturned == null) {
        return super.hashCode();
      }

      Object k = getKey();
      Object v = getValue();
      return k.hashCode() ^ v.hashCode();
    }

    public String toString() {
      // If not acting as entry, just use default.
      if (lastReturned == null) {
        return super.toString();
      }
      return getKey() + "=" + getValue();
    }
  }

  final class KeySet extends AbstractSet<K> {
    public Iterator<K> iterator() {
      return new KeyIterator();
    }

    public int size() {
      return StripedLockConcurrentHashMap.this.size();
    }

    public boolean contains(Object o) {
      return containsKey(o);
    }

    public boolean remove(Object o) {
      return StripedLockConcurrentHashMap.this.remove(o) != null;
    }

    public void clear() {
      StripedLockConcurrentHashMap.this.clear();
    }

    public Object[] toArray() {
      Collection<K> c = new ArrayList<K>();
      for (K k : this) {
        c.add(k);
      }
      return c.toArray();
    }

    public <T> T[] toArray(T[] a) {
      Collection<K> c = new ArrayList<K>();
      for (K k : this) {
        c.add(k);
      }
      return c.toArray(a);
    }
  }

  final class Values extends AbstractCollection<V> {
    public Iterator<V> iterator() {
      return new ValueIterator();
    }

    public int size() {
      return StripedLockConcurrentHashMap.this.size();
    }

    public boolean contains(Object o) {
      return containsValue(o);
    }

    public void clear() {
      StripedLockConcurrentHashMap.this.clear();
    }

    public Object[] toArray() {
      Collection<V> c = new ArrayList<V>();
      for (V k : this) {
        c.add(k);
      }
      return c.toArray();
    }

    public <T> T[] toArray(T[] a) {
      Collection<V> c = new ArrayList<V>();
      for (V k : this) {
        c.add(k);
      }
      return c.toArray(a);
    }
  }

  final class EntrySet extends AbstractSet<Entry<K, V>> {
    public Iterator<Entry<K, V>> iterator() {
      return new EntryIterator();
    }

    public boolean contains(Object o) {
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry<K, V> e = (Entry<K, V>)o;
      V v = get(e.getKey());
      return v != null && v.equals(e.getValue());
    }

    public boolean remove(Object o) {
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry<K, V> e = (Entry<K, V>)o;
      return StripedLockConcurrentHashMap.this.remove(e.getKey(), e.getValue());
    }

    public int size() {
      return StripedLockConcurrentHashMap.this.size();
    }

    public void clear() {
      StripedLockConcurrentHashMap.this.clear();
    }

    public Object[] toArray() {
      // Since we don't ordinarily have distinct Entry objects, we
      // must pack elements using exportable SimpleEntry
      Collection<Entry<K, V>> c = new ArrayList<Entry<K, V>>(size());
      for (Entry<K, V> i : this) {
        c.add(new SimpleEntry(i));
      }
      return c.toArray();
    }

    public <T> T[] toArray(T[] a) {
      Collection<Entry<K, V>> c = new ArrayList<Entry<K, V>>(size());
      for (Entry<K, V> i : this) {
        c.add(new SimpleEntry(i));
      }
      return c.toArray(a);
    }
  }

  /**
   * This duplicates java.util.AbstractMap.SimpleEntry until this class
   * is made accessible.
   */
  final class SimpleEntry implements Entry<K, V> {
    final K key;
    V value;

    public SimpleEntry(@NotNull Entry<K, V> e) {
      key = e.getKey();
      value = e.getValue();
    }

    @NotNull
    public K getKey() {
      return key;
    }

    @NotNull
    public V getValue() {
      return value;
    }

    public V setValue(@NotNull V value) {
      V oldValue = this.value;
      this.value = value;
      return oldValue;
    }

    public boolean equals(Object o) {
      if (!(o instanceof Entry)) {
        return false;
      }
      Entry e = (Entry)o;
      K o2 = (K)e.getKey();
      return getHashingStrategy().equals(key, o2) && value.equals(e.getValue());
    }

    public int hashCode() {
      return key.hashCode() ^ value.hashCode();
    }

    public String toString() {
      return key + "=" + value;
    }
  }

  static class CanonicalHashingStrategy<K> implements TObjectHashingStrategy<K> {
    private static final CanonicalHashingStrategy INSTANCE = new CanonicalHashingStrategy();

    @SuppressWarnings("unchecked")
    static <K> CanonicalHashingStrategy<K> getInstance() {
      return INSTANCE;
    }

    public int computeHashCode(final K object) {
      int h = object.hashCode();
      // performance matters here
      //h += ~(h << 9);
      //h ^= h >>> 14;
      //h += h << 4;
      //h ^= h >>> 10;
      return h;
    }

    public boolean equals(@NotNull K o1, @NotNull K o2) {
      return o1.equals(o2);
    }
  }
}



